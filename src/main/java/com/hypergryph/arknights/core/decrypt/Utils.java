package com.hypergryph.arknights.core.decrypt;

import com.alibaba.fastjson.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.ZipInputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.DigestUtils;

public class Utils {
    public Utils() {
    }

    public static byte[] hexToByte(String hex) {
        int byteLen = hex.length() / 2;
        byte[] ret = new byte[byteLen];

        for(int i = 0; i < byteLen; ++i) {
            int m = i * 2 + 1;
            int n = m + 1;
            int intVal = Integer.decode("0x" + hex.substring(i * 2, m) + hex.substring(m, n));
            ret[i] = Byte.valueOf((byte)intVal);
        }

        return ret;
    }

    public static String byteToHex(byte[] bytes) {
        String strHex = "";
        StringBuilder sb = new StringBuilder("");

        for(int n = 0; n < bytes.length; ++n) {
            strHex = Integer.toHexString(bytes[n] & 255);
            sb.append(strHex.length() == 1 ? "0" + strHex : strHex);
        }

        return sb.toString().trim();
    }

    public static String aesEncrypt(String src, String key) {
        String encodingFormat = "UTF-8";
        String iv = "0102030405060708";

        Exception e;
        try {
            e = null;
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] raw = key.getBytes();
            SecretKeySpec secretKeySpec = new SecretKeySpec(raw, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv.getBytes());
            cipher.init(1, secretKeySpec, ivParameterSpec);
            byte[] encrypted = cipher.doFinal(src.getBytes(encodingFormat));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception var9) {
            e = var9;
            System.out.println(e);
            return null;
        }
    }

    public static JSONObject BattleData_decrypt(String EncodeData, String login_time) {
        String LOG_TOKEN_KEY = "pM6Umv*^hVQuB6t&";
        byte[] BattleData = hexToByte(EncodeData.substring(0, EncodeData.length() - 32));
        SecretKeySpec Key = new SecretKeySpec(hexToByte(DigestUtils.md5DigestAsHex((LOG_TOKEN_KEY + login_time).getBytes())), "AES");
        IvParameterSpec Iv = new IvParameterSpec(hexToByte(EncodeData.substring(EncodeData.length() - 32)));

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(2, Key, Iv);
            return JSONObject.parseObject(new String(cipher.doFinal(BattleData)));
        } catch (Exception var7) {
            Exception e = var7;
            System.out.println(e);
            return null;
        }
    }

    public static JSONObject BattleReplay_decrypt(String battleReplay) {
        byte[] data = Base64.getDecoder().decode(battleReplay);
        byte[] b = null;

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ZipInputStream zip = new ZipInputStream(bis);

            while(zip.getNextEntry() != null) {
                byte[] buf = new byte[1024];
                int num;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while((num = zip.read(buf, 0, buf.length)) != -1) {
                    baos.write(buf, 0, num);
                }

                b = baos.toByteArray();
                baos.flush();
                baos.close();
            }

            zip.close();
            bis.close();
            return JSONObject.parseObject(new String(b, "UTF-8"));
        } catch (Exception var8) {
            Exception ex = var8;
            ex.printStackTrace();
            return null;
        }
    }
}
