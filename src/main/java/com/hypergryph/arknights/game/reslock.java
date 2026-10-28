package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/reslock"})
public class reslock {

    private static final Logger LOGGER = LogManager.getLogger();

    @PostMapping(value = {"/lockConsumable"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject lockConsumable(@RequestHeader(value = "secret", required = false) String secret,
                                     @RequestBody JSONObject JsonBody,
                                     HttpServletResponse response,
                                     HttpServletRequest request) {
        return handle(secret, JsonBody, response, request, true);
    }

    @PostMapping(value = {"/unlockConsumable"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject unlockConsumable(@RequestHeader(value = "secret", required = false) String secret,
                                       @RequestBody JSONObject JsonBody,
                                       HttpServletResponse response,
                                       HttpServletRequest request) {
        return handle(secret, JsonBody, response, request, false);
    }

    private JSONObject handle(String secret, JSONObject JsonBody, HttpServletResponse response,
                              HttpServletRequest request, boolean lock) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String path = lock ? "/reslock/lockConsumable" : "/reslock/unlockConsumable";
        LOGGER.info("[" + "/" + clientIp + "] " + path);

        String itemId = JsonBody.getString("itemId");
        String instId = JsonBody.getString("instId");
        int count = JsonBody.getIntValue("count");
        if (count <= 0) {
            count = 1;
        }

        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            JSONObject result = new JSONObject(true);
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }
        Long uid = ((Account) Accounts.get(0)).getUid();
        if (((Account) Accounts.get(0)).getBan() == 1L) {
            response.setStatus(500);
            JSONObject banned = new JSONObject(true);
            banned.put("statusCode", 403);
            banned.put("error", "Bad Request");
            banned.put("message", "error");
            return banned;
        }

        JSONObject UserSyncData = JSONObject.parseObject(((Account) Accounts.get(0)).getUser());
        JSONObject applied = applyReslock(UserSyncData, lock, itemId, instId, count);
        if (applied.getString("error") != null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", applied.getString("error"));
            return bad;
        }
        userDao.setUserData(uid, UserSyncData);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", applied.getJSONObject("modified"));
        JSONObject deleted = new JSONObject(true);
        JSONArray deletedInstIds = applied.getJSONArray("deleted");
        if (deletedInstIds != null && !deletedInstIds.isEmpty()) {
            deleted.put(lock ? "consumable" : "reslock", deletedInstIds);
        }
        playerDataDelta.put("deleted", deleted);

        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    public static JSONObject applyReslock(JSONObject UserSyncData, boolean lock, String itemId,
                                          String instId, int count) {
        JSONObject out = new JSONObject(true);
        if (count <= 0) {
            count = 1;
        }
        JSONObject consumable = ensureNodeStatic(UserSyncData, "consumable");
        JSONObject reslockRoot = ensureNodeStatic(UserSyncData, "reslock");
        JSONObject lockedMap = ensureNodeStatic(reslockRoot, "consumable");

        JSONObject fromMap = lock ? consumable : lockedMap;
        JSONObject toMap = lock ? lockedMap : consumable;

        JSONObject fromItem = fromMap.getJSONObject(itemId);
        JSONObject fromSlot = fromItem == null ? null : fromItem.getJSONObject(instId);
        if (fromSlot == null) {
            out.put("error", "没有这个实例: " + itemId + "/" + instId);
            return out;
        }
        int have = fromSlot.getIntValue("count");
        if (have < count) {
            out.put("error", "数量不足: 有 " + have + "，要操作 " + count);
            return out;
        }

        // 官方实测：数量归零时保留 {count:0}，不删实例，也从不回 deleted
        int left = have - count;
        fromSlot.put("count", left);

        // 目标：同 item 且 ts 相同的堆就复用，否则新开实例号
        JSONObject toItem = ensureNodeStatic(toMap, itemId);
        String targetInstId = null;
        for (String key : toItem.keySet()) {
            JSONObject existing = toItem.getJSONObject(key);
            if (existing != null && existing.getIntValue("ts") == fromSlot.getIntValue("ts")) {
                targetInstId = key;
                break;
            }
        }
        if (targetInstId == null) {
            if (!lock) {
                // 解锁：优先还原到当初上锁的那个真库存实例号（服务端侧记录，客户端不需要知道）
                JSONObject origin = ensureNodeStatic(reslockRoot, "origin").getJSONObject(itemId);
                String originInst = origin == null ? null : origin.getString(instId);
                if (originInst != null && !origin.containsKey(originInst)) {
                    targetInstId = originInst;
                }
            }
            if (targetInstId == null) {
                targetInstId = nextInstIdStatic(UserSyncData);
            }
        }
        JSONObject toSlot = toItem.getJSONObject(targetInstId);
        if (toSlot == null) {
            toSlot = new JSONObject(true);
            toSlot.put("ts", fromSlot.getIntValue("ts"));
            toSlot.put("count", 0);
            toItem.put(targetInstId, toSlot);
        }
        toSlot.put("count", toSlot.getIntValue("count") + count);

        if (lock) {
            JSONObject origin = ensureNodeStatic(reslockRoot, "origin");
            JSONObject originItem = ensureNodeStatic(origin, itemId);
            originItem.put(targetInstId, instId);
        }

        // 响应：官方只回两个镜像模块里变化的那些
        JSONObject modified = new JSONObject(true);
        // 官方即使扣到 0 也会把来源实例放进响应（count:0）
        JSONObject sourceDelta = new JSONObject(true);
        JSONObject sourceStack = new JSONObject(true);
        sourceStack.put(instId, fromSlot);
        sourceDelta.put(itemId, sourceStack);
        JSONObject targetDelta = new JSONObject(true);
        JSONObject targetStack = new JSONObject(true);
        targetStack.put(targetInstId, toSlot);
        targetDelta.put(itemId, targetStack);

        if (lock) {
            modified.put("consumable", sourceDelta);
            JSONObject reslockDelta = new JSONObject(true);
            reslockDelta.put("consumable", targetDelta);
            modified.put("reslock", reslockDelta);
        } else {
            JSONObject reslockDelta = new JSONObject(true);
            reslockDelta.put("consumable", sourceDelta);
            modified.put("reslock", reslockDelta);
            modified.put("consumable", targetDelta);
        }

        out.put("modified", modified);
        out.put("deleted", null);   // 官方从不回删除项
        return out;
    }

    private static JSONObject ensureNodeStatic(JSONObject parent, String key) {
        JSONObject node = parent.getJSONObject(key);
        if (node == null) {
            node = new JSONObject(true);
            parent.put(key, node);
        }
        return node;
    }

    private static String nextInstIdStatic(JSONObject UserSyncData) {
        JSONObject root = UserSyncData.getJSONObject("reslock");
        JSONObject map = root == null ? null : root.getJSONObject("consumable");
        long max = -1;
        if (map != null) {
            for (String itemId : map.keySet()) {
                JSONObject item = map.getJSONObject(itemId);
                if (item == null) {
                    continue;
                }
                for (String id : item.keySet()) {
                    try {
                        max = Math.max(max, Long.parseLong(id));
                    } catch (NumberFormatException ignored) {
                        // 忽略非数字实例号
                    }
                }
            }
        }
        return String.valueOf(max + 1);
    }

    private JSONObject ensureNode(JSONObject parent, String key) {
        JSONObject node = parent.getJSONObject(key);
        if (node == null) {
            node = new JSONObject(true);
            parent.put(key, node);
        }
        return node;
    }

    private String nextInstId(JSONObject UserSyncData) {
        int max = 0;
        for (String rootName : new String[]{"consumable", "reslock"}) {
            JSONObject root = UserSyncData.getJSONObject(rootName);
            if (root == null) {
                continue;
            }
            JSONObject map = "reslock".equals(rootName) ? root.getJSONObject("consumable") : root;
            if (map == null) {
                continue;
            }
            for (String itemId : map.keySet()) {
                JSONObject item = map.getJSONObject(itemId);
                if (item == null) {
                    continue;
                }
                for (String instId : item.keySet()) {
                    try {
                        max = Math.max(max, Integer.parseInt(instId));
                    } catch (NumberFormatException ignored) {
                        // 非数字实例号忽略
                    }
                }
            }
        }
        return String.valueOf(max + 1);
    }
}
