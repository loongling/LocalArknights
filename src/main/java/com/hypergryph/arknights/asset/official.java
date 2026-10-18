package com.hypergryph.arknights.asset;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/assetbundle/official/{os}/assets"})
public class official {
    private static final Logger LOGGER = LogManager.getLogger();

    public official() {
    }

    /** md5 缓存：key = 绝对路径 + "|" + length + "|" + lastModified */
    private static final Map<String, String> MD5_CACHE = new ConcurrentHashMap<>();

    @GetMapping({"/{assetsHash}/{fileName:.+}"})
    public void getFile(
            @PathVariable("assetsHash") String assetsHash,
            @PathVariable("fileName") String fileName,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        if (!assetsHash.matches("[a-zA-Z0-9_-]+")
                || fileName.contains("..")
                || fileName.contains("\\")
                || fileName.contains("/")) {
            response.sendError(HttpStatus.BAD_REQUEST.value());
            return;
        }

        JSONObject cfg = ArknightsApplication.serverConfig.getJSONObject("assets");
        String cdnBase = cfg.getString("cdnBase");
        if (cdnBase == null || cdnBase.isEmpty()) {
            cdnBase = "https://ak.hycdn.cn/assetbundle/official";
        }

        String osName = resolveOs(request);
        String baseDir = System.getProperty("user.dir") + "/assets/" + assetsHash + "/direct/";
        File dir = new File(baseDir);
        File file = new File(dir, fileName);

        // ---- 先保证文件在本地 ----
        if (!file.exists() || file.length() == 0) {
            if (!dir.exists() && !dir.mkdirs()) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value());
                return;
            }
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            String srcUrl = cdnBase + "/" + osName + "/assets/" + assetsHash + "/" + encoded;

            try {
                LOGGER.info("[{}] 回源 {}/{} <- {}", remoteIp(request), assetsHash, fileName, srcUrl);
                downloadTo(srcUrl, file);
            } catch (IOException e) {
                LOGGER.error("回源失败 {}/{}", assetsHash, fileName, e);
                if (!response.isCommitted()) {
                    response.sendError(HttpStatus.BAD_GATEWAY.value());
                }
                return;
            }
        }

        serveLocal(file, request, response);
    }

    private void serveLocal(File file, HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        long total = file.length();
        String md5 = md5Of(file);
        String eTag = "\"" + md5 + "\"";

        // If-None-Match → 304
        String inm = request.getHeader("If-None-Match");
        if (inm != null && inm.contains(md5)) {
            response.setStatus(HttpStatus.NOT_MODIFIED.value());
            response.setHeader("ETag", eTag);
            return;
        }

        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("ETag", eTag);
        response.setHeader("Last-Modified",
                Instant.ofEpochMilli(file.lastModified())
                        .atZone(ZoneId.of("GMT"))
                        .format(DateTimeFormatter.RFC_1123_DATE_TIME));
        response.setHeader("Cache-Control", "public, max-age=604800");
        response.setContentType("application/octet-stream");

        // Range
        long start = 0L;
        long end = total - 1L;
        boolean partial = false;

        String rangeHeader = request.getHeader("Range");

        // If-Range：客户端说"如果还是这个版本就给我分片"，版本不符要退回全量
        String ifRange = request.getHeader("If-Range");
        if (ifRange != null && !ifRange.contains(md5)) {
            LOGGER.info("If-Range 不匹配，退回全量: {} ({} vs md5 {})", file.getName(), ifRange, md5);
            rangeHeader = null;
        }

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            long[] r = parseSingleRange(rangeHeader, total);
            if (r == null) {
                response.setHeader("Content-Range", "bytes */" + total);
                response.sendError(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
                return;
            }
            if (r[0] >= 0) {
                start = r[0];
                end = r[1];
                partial = true;
            }
        }

        long count = end - start + 1L;
        response.setContentLengthLong(count);

        if (partial) {
            response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + total);
        } else {
            response.setStatus(HttpStatus.OK.value());
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);
            ServletOutputStream out = response.getOutputStream();
            byte[] buf = new byte[(int) Math.min(65536L, count)];
            long remaining = count;

            while (remaining > 0) {
                int want = (int) Math.min(buf.length, remaining);
                int read = raf.read(buf, 0, want);
                if (read < 0) {
                    break;
                }
                out.write(buf, 0, read);
                remaining -= read;
            }
            out.flush();
        } catch (IOException e) {
            LOGGER.debug("发送中断 {}: {}", file.getName(), e.getMessage());
        }

        LOGGER.info("[{}] {} /{}/{} sent={} total={}",
                remoteIp(request), partial ? "206" : "200",
                file.getParentFile().getName(), file.getName(), count, total);
    }

    /**
     * 严格校验状态码后落盘。原实现用 HttpUtil.downloadFile，CDN 返回 404
     * 时会把错误页当资源存下来。
     */
    private static void downloadTo(String urlStr, File dest) throws IOException {
        HttpURLConnection conn = null;
        File tmp = File.createTempFile(dest.getName() + ".", ".part", dest.getParentFile());
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(180_000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Arknights-AssetSrv/1.0");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("CDN 返回 " + code + ": " + urlStr);
            }

            long expect = conn.getContentLengthLong();
            long got = 0L;

            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(tmp.toPath())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    got += n;
                }
            }

            if (expect > 0 && got != expect) {
                throw new IOException("长度不符: 期望 " + expect + " 实际 " + got);
            }

            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("回源完成 {} ({} B)", dest.getName(), got);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            Files.deleteIfExists(tmp.toPath());
        }
    }

    private static String md5Of(File f) throws IOException {
        String key = f.getAbsolutePath() + "|" + f.length() + "|" + f.lastModified();
        String cached = MD5_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder(32);
            for (byte b : md.digest()) {
                sb.append(String.format("%02X", b));
            }
            String hex = sb.toString();
            MD5_CACHE.put(key, hex);
            return hex;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** @return {start,end}；{-1,-1}=忽略 Range；null=不可满足 */
    private static long[] parseSingleRange(String header, long total) {
        String spec = header.substring("bytes=".length()).trim();
        if (spec.indexOf(',') >= 0) {
            return new long[]{-1, -1};
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String s = spec.substring(0, dash).trim();
        String e = spec.substring(dash + 1).trim();
        try {
            long start;
            long end;
            if (s.isEmpty()) {
                if (e.isEmpty()) {
                    return null;
                }
                long suffix = Long.parseLong(e);
                if (suffix <= 0) {
                    return null;
                }
                start = Math.max(0L, total - suffix);
                end = total - 1L;
            } else {
                start = Long.parseLong(s);
                end = e.isEmpty() ? total - 1L : Long.parseLong(e);
            }
            if (start < 0L || start >= total || end < start) {
                return null;
            }
            if (end >= total) {
                end = total - 1L;
            }
            return new long[]{start, end};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String resolveOs(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua != null) {
            String low = ua.toLowerCase(Locale.ROOT);
            if (low.contains("android")) {
                return "Android";
            }
            if (low.contains("iphone") || low.contains("ipad")) {
                return "iOS";
            }
        }
        return "Windows";
    }

    private static String remoteIp(HttpServletRequest request) {
        return ArknightsApplication.getIpAddr(request);
    }
}