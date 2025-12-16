package com.hypergryph.arknights.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import javax.annotation.PostConstruct;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/assetbundle/official/{os}/assets")
public class official {

    // ========== 配置 ==========
    private static final String BASE_CDN_CN = "https://ak.hycdn.cn/assetbundle/official";
    private static final String BASE_CDN_GLOBAL = "https://ark-us-static-online.yo-star.com/assetbundle/official";
    private static final Path CACHE_DIR = Paths.get("./assets"); // <-- 本地缓存根（等价 Python 的 ./assets）
    private static final Path CACHE_SUBDIR = CACHE_DIR.resolve("cache");
    private static final Path CONFIG_PATH = Paths.get("./config.json"); // <-- 你的配置文件路径
    private static final int MAX_DOWNLOAD_RETRIES = 4;
    private static final int DOWNLOAD_CHUNK = 64 * 1024; // 64KB 块写回
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient okHttpClient;
    private final DownloadManager downloadManager;
    private final ModsManager modsManager;
    private final ServerConfig serverConfig;

    public official() {
        // okHttpClient 可按需配置代理、超时等
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .callTimeout(Duration.ofMinutes(5))
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(60));

        // 如果需要通过本地代理（eg. 127.0.0.1:7890），打开下面代码并调整：
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890));
        builder.proxy(proxy);

        this.okHttpClient = builder.build();
        this.downloadManager = new DownloadManager(okHttpClient);
        this.modsManager = new ModsManager();
        this.serverConfig = ServerConfig.load(CONFIG_PATH);
    }

    @PostConstruct
    public void initDirs() throws IOException {
        if (!Files.exists(CACHE_DIR)) Files.createDirectories(CACHE_DIR);
        if (!Files.exists(CACHE_SUBDIR)) Files.createDirectories(CACHE_SUBDIR);
    }

    @GetMapping("/{versionHash}/{fileName}")
    public ResponseEntity<StreamingResponseBody> asset(
            @PathVariable("os") String os,
            @PathVariable("versionHash") String versionHash,
            @PathVariable("fileName") String fileName,
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) {
        // 决定上游 CDN
        String base = "cn".equalsIgnoreCase(serverConfig.server.mode) ? BASE_CDN_CN : BASE_CDN_GLOBAL;
        String upstreamUrl = String.format("%s/%s/assets/%s/%s", base, os, versionHash, fileName);

        // 处理 hot_update_list.json 的特殊逻辑
        if ("hot_update_list.json".equalsIgnoreCase(fileName) && serverConfig.assets.enableMods) {
            try {
                return handleHotUpdateList(upstreamUrl, versionHash, rangeHeader);
            } catch (Exception e) {
                e.printStackTrace();
                return buildEmpty206();
            }
        }

        // 本地路径（与 Python 等价： ./assets/{version}/redirect 或 ./assets/{version}）
        Path versionDir = CACHE_DIR.resolve(versionHash).resolve("redirect");
        if (!serverConfig.assets.downloadLocally) {
            // 如果不启用完全本地下载，仍先尝试缓存目录 ./assets/{version}，只有未在下载列表则走 redirect->上游重定向
            versionDir = CACHE_DIR.resolve(versionHash);
        }
        try {
            Files.createDirectories(versionDir);
        } catch (IOException ignored) {}

        Path filePath = versionDir.resolve(fileName);

        // If configured to proxy (downloadLocally == false) AND the file is not in mod-download list,
        // mimic Python behavior: redirect to upstream. But in our B 模式我们 want first-to-CDN-then-cache,
        // so we will always attempt to provide cached file, fetching if missing.
        // Start download (or resume) asynchronously but ensure we return a streaming response using local file if available.
        try {
            downloadManager.ensureDownloaded(upstreamUrl, filePath, MAX_DOWNLOAD_RETRIES);
        } catch (Exception e) {
            log("downloadManager.ensureDownloaded failed: " + e.getMessage());
            // 不抛500，允许返回已有部分或空文件
        }

        // Now build response (support Range)
        try {
            long total = Files.exists(filePath) ? Files.size(filePath) : 0L;
            long start = 0;
            long end = Math.max(0, total - 1);

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] parts = rangeHeader.substring(6).split("-");
                try {
                    start = Long.parseLong(parts[0]);
                } catch (Exception ignored) {
                    start = 0;
                }
                if (parts.length > 1 && parts[1].length() > 0) {
                    try {
                        end = Long.parseLong(parts[1]);
                    } catch (Exception ignored) {
                        end = Math.max(0, total - 1);
                    }
                } else {
                    end = Math.max(0, total - 1);
                }
            }

            if (start > end) {
                // 没有可用数据，返回空 206，客户端会重试
                return buildPartialEmpty(start, end, total);
            }

            long contentLen = end - start + 1;

            // 计算 MD5（如果有文件）
            String md5Base64 = total > 0 ? calcMd5Base64(filePath) : null;
            String etag = md5Base64 != null ? md5Base64.toUpperCase(Locale.ROOT) : null;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept-Ranges", "bytes");
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentLength(contentLen);
            if (md5Base64 != null) headers.set("Content-MD5", md5Base64);
            if (etag != null) headers.set("Etag", "\"" + etag + "\"");
            headers.set("Content-Range", String.format("bytes %d-%d/%d", start, end, total));

            HttpStatus status = (start == 0 && end == total - 1) ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;

            long finalStart = start;
            StreamingResponseBody body = out -> {
                if (!Files.exists(filePath)) {
                    // 文件尚未存在：返回空体（客户端会重试）
                    return;
                }
                try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
                    raf.seek(finalStart);
                    byte[] buf = new byte[DOWNLOAD_CHUNK];
                    long left = contentLen;
                    while (left > 0) {
                        int read = raf.read(buf, 0, (int) Math.min(buf.length, left));
                        if (read == -1) break;
                        out.write(buf, 0, read);
                        left -= read;
                        out.flush();
                    }
                } catch (IOException e) {
                    // 客户端中断或 IO 问题，结束流但不要抛 500
                }
            };

            return ResponseEntity.status(status).headers(headers).body(body);

        } catch (IOException ex) {
            ex.printStackTrace();
            return buildEmpty206();
        }
    }

    // ========== hot_update_list.json 特殊处理 ==========
    private ResponseEntity<StreamingResponseBody> handleHotUpdateList(String upstreamUrl, String versionHash, String rangeHeader) throws Exception {
        // 缓存位置
        Path cachePath = CACHE_SUBDIR.resolve("hot_update_list.json");
        JsonNode hotJson;
        if (!Files.exists(cachePath)) {
            // 从上游拉取一次 JSON 并保存
            Request req = new Request.Builder().url(upstreamUrl).header("User-Agent", "Mozilla/5.0").build();
            try (Response resp = okHttpClient.newCall(req).execute()) {
                if (!resp.isSuccessful()) throw new IOException("upstream hot_update_list failed: " + resp.code());
                String body = resp.body().string();
                hotJson = MAPPER.readTree(body);
                Files.createDirectories(cachePath.getParent());
                Files.writeString(cachePath, hotJson.toPrettyString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } else {
            hotJson = MAPPER.readTree(cachePath.toFile());
        }

        // 修改 hot_update_list.json -> 注入 mods / 替换 hash
        JsonNode newHot = modsManager.rewriteHotUpdate(hotJson, versionHash);

        // 保存到缓存并返回（以文件方式发送）
        Files.writeString(cachePath, newHot.toPrettyString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // 直接 send cached file (stream)
        final Path toSend = cachePath;
        long total = Files.size(toSend);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentLength(total);
        headers.set("Accept-Ranges", "bytes");

        StreamingResponseBody body = out -> {
            try (InputStream in = Files.newInputStream(toSend, StandardOpenOption.READ)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) {
                    out.write(buf, 0, r);
                }
            }
        };

        return ResponseEntity.ok().headers(headers).body(body);
    }

    // ========== 辅助方法 ==========
    private static ResponseEntity<StreamingResponseBody> buildEmpty206() {
        HttpHeaders h = new HttpHeaders();
        h.set("Accept-Ranges", "bytes");
        h.setContentLength(0);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(h).body(out -> {});
    }

    private static ResponseEntity<StreamingResponseBody> buildPartialEmpty(long start, long end, long total) {
        HttpHeaders h = new HttpHeaders();
        h.set("Accept-Ranges", "bytes");
        h.setContentLength(0);
        h.set("Content-Range", String.format("bytes %d-%d/%d", start, end, total));
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(h).body(o -> {});
    }

    private static String calcMd5Base64(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
                byte[] buf = new byte[16 * 1024];
                int r;
                while ((r = in.read(buf)) != -1) md.update(buf, 0, r);
            }
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (Exception e) {
            return null;
        }
    }

    private static void log(String s) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MMM/yyyy HH:mm:ss"));
        System.out.println(String.format("%s %s", now, s));
    }

    // ========== 内部类：DownloadManager（并发保护） ==========
    static class DownloadManager {
        private final OkHttpClient client;
        // key = absolute file path string
        private final ConcurrentHashMap<String, CompletableFuture<Boolean>> inflight = new ConcurrentHashMap<>();
        private final UpstreamDownloader downloader = new UpstreamDownloader();

        public DownloadManager(OkHttpClient client) {
            this.client = client;
        }

        /**
         * 确保文件已被下载或正在下载。此方法不会阻塞太久（会等待同一文件的下载完成）。
         * 如果下载失败，仍可能保留已下载的部分供调用方返回（以避免 500 导致客户端报 205）。
         */
        public void ensureDownloaded(String upstreamUrl, Path destPath, int maxRetries) throws Exception {
            String key = destPath.toAbsolutePath().toString();
            // 使用 CompletableFuture 保证同一文件只有一条上游连接，其他请求等待
            CompletableFuture<Boolean> fut = inflight.computeIfAbsent(key, k -> {
                CompletableFuture<Boolean> cf = new CompletableFuture<>();
                // 异步执行下载任务
                Executors.newSingleThreadExecutor().submit(() -> {
                    try {
                        boolean ok = downloader.downloadWithResume(upstreamUrl, destPath, maxRetries);
                        cf.complete(ok);
                    } catch (Throwable t) {
                        cf.completeExceptionally(t);
                    } finally {
                        inflight.remove(key);
                    }
                });
                return cf;
            });

            try {
                // 等待下载完成（限制等待时间避免永久阻塞）
                fut.get(2, TimeUnit.MINUTES);
            } catch (TimeoutException te) {
                // 超时：不要抛异常，保留已下载部分
            } catch (ExecutionException ee) {
                // 上游失败，抛但调用者会捕获并继续（我们在 controller 捕获）
                throw new Exception(ee.getCause());
            }
        }
    }

    // ========== 内部类：UpstreamDownloader（负责实际拉取并支持断点续传 + 重试） ==========
    static class UpstreamDownloader {

        private static final int BUFFER = 64 * 1024;
        private static final int CONNECT_TIMEOUT_MS = 15_000;
        private static final int READ_TIMEOUT_MS = 60_000;

        private final OkHttpClient client;

        public UpstreamDownloader() {
            // 这里使用默认 client（可以注入），为简洁直接创建
            this.client = new OkHttpClient.Builder()
                    .retryOnConnectionFailure(true)
                    .connectTimeout(Duration.ofSeconds(15))
                    .readTimeout(Duration.ofSeconds(60))
                    .build();
        }

        /**
         * downloadWithResume:
         * - 如果本地已存在部分文件，会发送 Range 请求续传（bytes=<existing>-）
         * - 在失败时会重试 maxRetries 次
         * - 如果多次失败，会保留已下载部分并返回 false（调用方会用部分文件继续返回，避免 500）
         */
        public boolean downloadWithResume(String upstreamUrl, Path dest, int maxRetries) {
            try {
                if (dest.getParent() != null) Files.createDirectories(dest.getParent());
            } catch (IOException ignored) {}

            int attempt = 0;
            while (attempt < maxRetries) {
                attempt++;
                long existing = dest.toFile().exists() ? dest.toFile().length() : 0L;

                Request.Builder rb = new Request.Builder().url(upstreamUrl)
                        .header("User-Agent", "okhttp/3.12.1");
                if (existing > 0) {
                    rb.header("Range", "bytes=" + existing + "-");
                }
                Request req = rb.get().build();

                try (Response resp = client.newCall(req).execute()) {
                    if (!resp.isSuccessful()) {
                        // drain error stream then retry
                        try (okhttp3.ResponseBody err = resp.body()) {
                            if (err != null) ((okhttp3.ResponseBody) err).close();
                        } catch (Exception ignored) {}
                        Thread.sleep(500L * attempt);
                        continue;
                    }

                    // 获取上游输入流并写入文件（支持续传）
                    try (okhttp3.ResponseBody body = resp.body();
                         InputStream in = (body != null) ? ((okhttp3.ResponseBody) body).byteStream() : InputStream.nullInputStream();
                         RandomAccessFile raf = new RandomAccessFile(dest.toFile(), "rw")) {

                        if (existing > 0) raf.seek(existing);
                        byte[] buf = new byte[BUFFER];
                        int read;
                        while ((read = in.read(buf)) != -1) {
                            raf.write(buf, 0, read);
                        }
                    }

                    return true; // 成功
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // 打印但不抛出，尝试重试
                    System.err.println("download attempt " + attempt + " failed: " + e.getMessage());
                    try { Thread.sleep(500L * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }

            // all attempts failed — keep partial file if any
            return false;
        }
    }

    // ========== 内部类：ModsManager（处理 hot_update_list.json 注入/替换） ==========
    static class ModsManager {
        private final Path modsJson = Paths.get("./mods.json");

        public JsonNode rewriteHotUpdate(JsonNode original, String assetsHash) {
            // Deep copy
            ObjectMapper mapper = MAPPER;
            JsonNode root = original.deepCopy();

            ArrayList<JsonNode> newAbInfos = new ArrayList<>();
            JsonNode abInfos = root.path("abInfos");
            if (abInfos.isArray()) {
                for (JsonNode node : abInfos) {
                    newAbInfos.add(node);
                }
            }

            // 如果需要注入 mods，从 mods.json 读取（你可以按需改）
            if (Files.exists(modsJson)) {
                try {
                    JsonNode modsRoot = mapper.readTree(modsJson.toFile());
                    if (modsRoot.isArray()) {
                        for (JsonNode mod : modsRoot) {
                            newAbInfos.add(mod);
                        }
                    }
                } catch (IOException ignored) {}
            }

            // 替换 versionId/hash（如果需要）
            if (root.has("versionId")) {
                ((ObjectNode) root).put("versionId", assetsHash);
            } else {
                ((ObjectNode) root).put("versionId", assetsHash);
            }

            // set abInfos
            ((ObjectNode) root).set("abInfos", mapper.valueToTree(newAbInfos));
            return root;
        }
    }

    // ========== 内部类：ServerConfig（简易配置读取） ==========
    static class ServerConfig {
        public Server server = new Server();
        public Assets assets = new Assets();

        static class Server { public String mode = "cn"; }
        static class Assets { public boolean downloadLocally = true; public boolean enableMods = false; }

        public static ServerConfig load(Path cfgPath) {
            ServerConfig cfg = new ServerConfig();
            try {
                if (Files.exists(cfgPath)) {
                    JsonNode node = MAPPER.readTree(cfgPath.toFile());
                    if (node.has("server") && node.get("server").has("mode")) {
                        cfg.server.mode = node.get("server").get("mode").asText("cn");
                    }
                    if (node.has("assets")) {
                        JsonNode a = node.get("assets");
                        cfg.assets.downloadLocally = a.path("downloadLocally").asBoolean(true);
                        cfg.assets.enableMods = a.path("enableMods").asBoolean(false);
                    }
                }
            } catch (Exception e) {
                // ignore and use defaults
            }
            return cfg;
        }
    }

    public static byte[] readInputStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        int len;
        while((len = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }

        bos.close();
        return bos.toByteArray();
    }

    public ResponseEntity<FileSystemResource> export(File file) {
        if (file == null) {
            return null;
        } else {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
            headers.add("Content-Disposition", "attachment; filename=" + file.getName());
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");
            headers.add("Last-Modified", (new Date()).toString());
            headers.add("ETag", String.valueOf(System.currentTimeMillis()));
            return ((ResponseEntity.BodyBuilder)ResponseEntity.ok().headers(headers)).contentLength(file.length()).contentType(MediaType.parseMediaType("application/octet-stream")).body(new FileSystemResource(file));
        }
    }
}