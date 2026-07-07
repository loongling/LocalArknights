package com.hypergryph.arknights.core.file;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.hypergryph.arknights.ArknightsApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class IOTools {
    public IOTools() {
    }

    public static String ReadNormalFile(String FilePath) {
        try {
            File jsonFile = new File(FilePath);
            FileReader fileReader = new FileReader(jsonFile);
            Reader reader = new InputStreamReader(new FileInputStream(jsonFile), "UTF-8");
            StringBuffer Buffer = new StringBuffer();

            int ReadChar;
            while((ReadChar = ((Reader)reader).read()) != -1) {
                Buffer.append((char)ReadChar);
            }

            fileReader.close();
            ((Reader)reader).close();
            return Buffer.toString();
        } catch (IOException var6) {
            IOException e = var6;
            e.printStackTrace();
            return null;
        }
    }

    public static JSONObject ReadJsonFile(String JsonFilePath) {
        try {
            File jsonFile = new File(JsonFilePath);
            FileReader fileReader = new FileReader(jsonFile);
            Reader reader = new InputStreamReader(new FileInputStream(jsonFile), "UTF-8");
            StringBuffer Buffer = new StringBuffer();

            int ReadChar;
            while((ReadChar = ((Reader)reader).read()) != -1) {
                Buffer.append((char)ReadChar);
            }

            fileReader.close();
            ((Reader)reader).close();
            return JSONObject.parseObject(Buffer.toString(), new Feature[]{Feature.OrderedField});
        } catch (IOException var6) {
            IOException e = var6;
            e.printStackTrace();
            return null;
        }
    }

    public static Boolean SaveJsonFile(String JsonFilePath, JSONObject JsonData) {
        try {
            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(JsonFilePath), "UTF-8");
            osw.write(JSON.toJSONString(JsonData, new SerializerFeature[]{SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue}));
            osw.flush();
            osw.close();
            return true;
        } catch (IOException var3) {
            IOException e = var3;
            e.printStackTrace();
            return false;
        }
    }

    public  static JSONArray ReadJsonArray(String ArrayFilePath){
        try {
            // 读取文件内容
            String content = new String(Files.readAllBytes(Paths.get(ArrayFilePath)), StandardCharsets.UTF_8);

            // 解析为 JSONArray
            return JSON.parseArray(content);

        } catch (IOException e) {
            ArknightsApplication.LOGGER.error("读取 JSON 数组文件失败: {}", ArrayFilePath, e);
            return new JSONArray();  // 返回空数组
        } catch (JSONException e) {
            ArknightsApplication.LOGGER.error("解析 JSON 数组失败: {}", ArrayFilePath, e);
            return new JSONArray();
        }
    }
}
