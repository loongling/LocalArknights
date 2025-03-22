package com.hypergryph.arknights.asset;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONArray;
import com.hypergryph.arknights.ArKnightsApplication;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/assetbundle/official/{os}/assets"})
public class official {
    private static final Logger LOGGER = LogManager.getLogger();

    public official() {
    }

    @RequestMapping({"/{assetsHash}/{fileName}"})
    public ResponseEntity<FileSystemResource> getFile(@PathVariable("os") String os, @PathVariable("assetsHash") String assetsHash, @PathVariable("fileName") String fileName, HttpServletResponse response, HttpServletRequest request) throws IOException {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        Boolean redirect = ArKnightsApplication.serverConfig.getJSONObject("assets").getBooleanValue("enableRedirect");
        String redirectUrl = ArKnightsApplication.serverConfig.getJSONObject("assets").getString("redirectUrl");
        String filePath = System.getProperty("user.dir") + "/assets/" + assetsHash + "/direct/";
        if (redirect) {
            filePath = System.getProperty("user.dir") + "/assets/" + assetsHash + "/redirect/";
            JSONArray localFiles = ArKnightsApplication.serverConfig.getJSONObject("assets").getJSONArray("localFiles");
            if (!localFiles.contains(fileName)) {
                response.sendRedirect(redirectUrl + "/" + fileName);
                return null;
            }
        }

        File file = new File(filePath, fileName);
        if (file.exists()) {
            return this.export(file);
        } else {
            LOGGER.warn("正在下载 " + assetsHash + "/" + fileName);
            HttpUtil.downloadFile(redirectUrl + "/" + fileName, filePath + fileName);
            file = new File(filePath, fileName);
            if (file.exists()) {
                LOGGER.info("[/" + clientIp + "] /" + assetsHash + "/" + fileName);
                return this.export(file);
            } else {
                return null;
            }
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