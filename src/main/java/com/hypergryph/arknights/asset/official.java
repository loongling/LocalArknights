package com.hypergryph.arknights.asset;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping({"/assetbundle/official/{os}/assets"})
public class official {
    private static final Logger LOGGER = LogManager.getLogger();

    public official() {
    }
    private static final String BASE_CDN = "https://ak.hycdn.cn/assetbundle/official";
    RestTemplate restTemplate = new RestTemplate();



    @RequestMapping("/{assetsHash}/{fileName}")
    public ResponseEntity<?> proxyAsset(
            @PathVariable("os") String os,
            @PathVariable("assetsHash") String assetsHash,
            @PathVariable("fileName") String fileName,
            HttpServletRequest clientRequest) {

        try {
            String targetUrl = BASE_CDN + "/" + os + "/assets/" + assetsHash + "/" + fileName;

            // 构建转发请求头
            HttpHeaders forwardHeaders = new HttpHeaders();
            String range = clientRequest.getHeader("Range");
            if (range != null) {
                forwardHeaders.set("Range", range);
            }
            forwardHeaders.set("User-Agent", "okhttp/3.12.1"); // 强制与客户端一致

            HttpEntity<Void> requestEntity = new HttpEntity<>(forwardHeaders);

            ResponseEntity<byte[]> upstreamResponse = restTemplate.exchange(
                    URI.create(targetUrl),
                    HttpMethod.GET,
                    requestEntity,
                    byte[].class
            );

            byte[] body = upstreamResponse.getBody();
            if (body == null || body.length == 0 || upstreamResponse.getStatusCode().is5xxServerError()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Remote resource failed.");
            }

            // 构建返回头
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.set("Accept-Ranges", "bytes");

            if (range != null && upstreamResponse.getStatusCode() == HttpStatus.PARTIAL_CONTENT) {
                // 客户端请求了 Range，我们手动补全 Content-Range
                String contentRange = upstreamResponse.getHeaders().getFirst("Content-Range");
                if (contentRange != null) {
                    responseHeaders.set("Content-Range", contentRange);
                } else {
                    // 手动计算
                    responseHeaders.set("Content-Range", "bytes 0-" + (body.length - 1) + "/" + body.length);
                }
                responseHeaders.setContentLength(body.length);
                return new ResponseEntity<>(body, responseHeaders, HttpStatus.PARTIAL_CONTENT);
            } else {
                // 非 Range 请求，正常返回
                responseHeaders.setContentLength(body.length);
                return new ResponseEntity<>(body, responseHeaders, HttpStatus.OK);
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("代理失败: " + e.getMessage());
        }
    }



    public static String downLoadFromUrl(String urlStr, String fileName, String savePath) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
            InputStream inputStream = conn.getInputStream();
            byte[] getData = readInputStream(inputStream);
            File saveDir = new File(savePath);
            if (!saveDir.exists()) {
                saveDir.mkdir();
            }

            File dir = new File(saveDir + File.separator);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(getData);
            if (fos != null) {
                fos.close();
            }

            if (inputStream != null) {
                inputStream.close();
            }

            return saveDir + File.separator + fileName;
        } catch (Exception var11) {
            Exception e = var11;
            e.printStackTrace();
            return "";
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