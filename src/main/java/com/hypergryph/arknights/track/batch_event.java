package com.hypergryph.arknights.track;

import com.alibaba.fastjson.JSONObject;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class batch_event {

    public batch_event() {
    }

    @RequestMapping({"/batch_event"})
    public JSONObject BatchEvent() {
        JSONObject result = new JSONObject(true);
        result.put("code", 200);
        result.put("msg", "ok");
        return result;
    }
}
