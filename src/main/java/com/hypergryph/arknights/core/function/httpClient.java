package com.hypergryph.arknights.core.function;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.core.decrypt.Utils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

public class httpClient {
    public httpClient() {
    }

    public static JSONObject verifySmsCode(String phone, String smsCode) {
        String secKey = "FFFFFFFFFFFFFFFF";
        String encSecKey = "257348aecb5e556c066de214e531faadd1c55d814f9be95fd06d6bff9f4c7a41f831f6394d5a3fd2e3881736d94a02ca919d952872e7d0a50ebfa1769a7a62d512f5f1ca21aec60bc3819a9c3ffca5eca9a0dba6d6f7249b06f5965ecfff3695b54e1c28f3f624750ed39e7de08fc8493242e26dbc4484a01c76f739e135637c";
        String encText = Utils.aesEncrypt(Utils.aesEncrypt("{\"countrycode\":\"86\",\"phone\":\"" + phone + "\",\"rememberLogin\":\"true\",\"captcha\":\"" + smsCode + "\",\"checkToken\":\"\",\"csrf_token\":\"\"}", "0CoJUm6Qyw8W8jud"), secKey);
        PrintWriter out = null;
        BufferedReader in = null;
        String result = "";

        try {
            URL realUrl = new URL("https://music.163.com/weapi/login/cellphone?csrf_token=");
            URLConnection connection = realUrl.openConnection();
            connection.setRequestProperty("Referer", "https://music.163.com/");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            out = new PrintWriter(connection.getOutputStream());
            out.print("params=" + URLEncoder.encode(encText, "UTF-8") + "&encSecKey=" + encSecKey);
            out.flush();

            String line;
            for(in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8")); (line = in.readLine()) != null; result = result + line) {
            }
        } catch (Exception var19) {
            Exception e = var19;
            System.out.println("发送 POST 请求出现异常！" + e);
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }

                if (in != null) {
                    in.close();
                }
            } catch (IOException var18) {
                IOException ex = var18;
                ex.printStackTrace();
            }

        }

        return JSONObject.parseObject(result);
    }

    public static JSONObject sentSmsCode(String phone) {
        String secKey = "FFFFFFFFFFFFFFFF";
        String encSecKey = "257348aecb5e556c066de214e531faadd1c55d814f9be95fd06d6bff9f4c7a41f831f6394d5a3fd2e3881736d94a02ca919d952872e7d0a50ebfa1769a7a62d512f5f1ca21aec60bc3819a9c3ffca5eca9a0dba6d6f7249b06f5965ecfff3695b54e1c28f3f624750ed39e7de08fc8493242e26dbc4484a01c76f739e135637c";
        String encText = Utils.aesEncrypt(Utils.aesEncrypt("{\"cellphone\":\"" + phone + "\",\"ctcode\":\"86\",\"csrf_token\":\"\"}", "0CoJUm6Qyw8W8jud"), secKey);
        PrintWriter out = null;
        BufferedReader in = null;
        String result = "";

        try {
            URL realUrl = new URL("https://music.163.com/weapi/sms/captcha/sent?csrf_token=");
            URLConnection connection = realUrl.openConnection();
            connection.setRequestProperty("Referer", "https://music.163.com/");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            out = new PrintWriter(connection.getOutputStream());
            out.print("params=" + URLEncoder.encode(encText, "UTF-8") + "&encSecKey=" + encSecKey);
            out.flush();

            String line;
            for(in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8")); (line = in.readLine()) != null; result = result + line) {
            }
        } catch (Exception var18) {
            Exception e = var18;
            System.out.println("发送 POST 请求出现异常！" + e);
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }

                if (in != null) {
                    in.close();
                }
            } catch (IOException var17) {
                IOException ex = var17;
                ex.printStackTrace();
            }

        }

        return JSONObject.parseObject(result);
    }
}
