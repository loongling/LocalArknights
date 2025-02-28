package com.hypergryph.arknights.auth;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/user"})
public class user {
    private static String Key = "IxMMveJRWsxStJgX";

    public user(){
    }

    @RequestMapping({"/info/v1/need_cloud_auth"})
    public JSONObject need_cloud_auth() throws JSONException {
        JSONObject result = new JSONObject(String.valueOf(true));
        result.put("status", 0);
        result.put("msg", "faq");
        return result;
    }
}
