package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.card.NameCardBuilder;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/businessCard")
public class businessCard {

    private static final int FLAG_COMPONENT_ORDER = 1;
    private static final int FLAG_SKIN_ID = 2;
    private static final int FLAG_MISC = 4;
    private static final int FLAG_SKIN_TMPL = 8;
    private static final int FLAG_ALL = FLAG_COMPONENT_ORDER | FLAG_SKIN_ID | FLAG_MISC | FLAG_SKIN_TMPL;

    @PostMapping("/editNameCard")
    public JSONObject editNameCard(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody JSONObject jsonBody,
                                   HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /businessCard/editNameCard");
        ArknightsApplication.LOGGER.info("editNameCard:" + jsonBody);

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }
        long uid = account.getUid();
        JSONObject userData = holder.getJSONObject("user");

        int flag = jsonBody.getIntValue("flag");
        JSONObject content = jsonBody.getJSONObject("content");

        if (content == null) {
            response.setStatus(400);
            return errorResponse(222, "validation", "\"content\" is required");
        }
        if (!content.containsKey("skinId")) {
            response.setStatus(400);
            return errorResponse(222, "validation", "\"content.skinId\" is required");
        }
        if (!content.containsKey("skinTmpl")) {
            response.setStatus(400);
            return errorResponse(222, "validation", "\"content.skinTmpl\" is required");
        }
        if (!content.containsKey("component")) {
            response.setStatus(400);
            return errorResponse(222, "validation", "\"content.component\" is required");
        }
        if (!content.containsKey("misc")) {
            response.setStatus(400);
            return errorResponse(222, "validation", "\"content.misc\" is required");
        }

        String skinId = content.getString("skinId");
        JSONArray component = content.getJSONArray("component");
        JSONObject misc = content.getJSONObject("misc");
        // misc 非 null 时才校验 0/1（官方不接受 true/false）
        if (misc != null) {
            Object showDetail = misc.get("showDetail");
            Object showBirthday = misc.get("showBirthday");
            if (!isZeroOrOne(showDetail)) {
                response.setStatus(400);
                return errorResponse(222, "validation", "\"content.misc.showDetail\" must be one of [0, 1]");
            }
            if (!isZeroOrOne(showBirthday)) {
                response.setStatus(400);
                return errorResponse(222, "validation", "\"content.misc.showBirthday\" must be one of [0, 1]");
            }
        }

        if (flag == 0 || (flag & ~FLAG_ALL) != 0) {
            response.setStatus(400);
            return errorResponse(5525, "unknown server error", "invalid flag to edit name card");
        }
        if ((flag & FLAG_SKIN_TMPL) != 0 && (skinId == null || content.get("skinTmpl") == null)) {
            response.setStatus(400);
            return errorResponse(5847, "invalid parameter", "no skin tmpl change info");
        }

        JSONObject nameCardStyle = userData.getJSONObject("nameCardStyle");
        if (nameCardStyle == null) {
            nameCardStyle = defaultNameCardStyle();
            userData.put("nameCardStyle", nameCardStyle);
        }
        JSONObject cardSkin = nameCardStyle.getJSONObject("skin");
        if (cardSkin == null) {
            cardSkin = new JSONObject(true);
            cardSkin.put("selected", "nc_rhodes_default");
            cardSkin.put("state", new JSONObject(true));
            cardSkin.put("tmpl", new JSONObject(true));
            nameCardStyle.put("skin", cardSkin);
        }

        if ((flag & FLAG_COMPONENT_ORDER) != 0 && component != null) {
            nameCardStyle.put("componentOrder", component);
        }
        if ((flag & FLAG_SKIN_ID) != 0 && skinId != null) {
            cardSkin.put("selected", skinId);
        }
        if ((flag & FLAG_MISC) != 0 && misc != null) {
            JSONObject storedMisc = new JSONObject(true);
            storedMisc.put("showDetail", intToBool(misc.get("showDetail")));
            storedMisc.put("showBirthday", intToBool(misc.get("showBirthday")));
            nameCardStyle.put("misc", storedMisc);
        }
        if ((flag & FLAG_SKIN_TMPL) != 0) {
            JSONObject tmpl = cardSkin.getJSONObject("tmpl");
            if (tmpl == null) {
                tmpl = new JSONObject(true);
                cardSkin.put("tmpl", tmpl);
            }
            tmpl.put(skinId, content.getIntValue("skinTmpl"));
        }

        userDao.setUserData(uid, userData);
        ArknightsApplication.LOGGER.info("[businessCard] uid={} flag={} 名片已更新（皮肤 {}）", uid, flag, skinId);

        JSONObject modified = new JSONObject(true);
        modified.put("nameCardStyle", nameCardStyle);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject out = new JSONObject(true);
        out.put("playerDataDelta", playerDataDelta);
        return out;
    }

    @PostMapping("/getOtherPlayerNameCard")
    public JSONObject getOtherPlayerNameCard(@RequestHeader(value = "secret", required = false) String secret,
                                             @RequestBody JSONObject jsonBody,
                                             HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /businessCard/getOtherPlayerNameCard");
        ArknightsApplication.LOGGER.info("getOtherPlayerNameCard:" + jsonBody);

        JSONObject holder = new JSONObject();
        if (auth(secret, response, holder) == null) {
            return (JSONObject) holder.remove("__error");
        }

        String uidStr = jsonBody.getString("uid");
        if (uidStr == null || uidStr.trim().isEmpty()) {
            response.setStatus(400);
            return errorResponse(222, "validation", "\"uid\" is required");
        }
        Object srcRaw = jsonBody.get("src");
        if (srcRaw instanceof String && ((String) srcRaw).trim().isEmpty()) {
            response.setStatus(400);
            return errorResponse(222, "validation", "\"src\" is not allowed to be empty");
        }

        long targetUid;
        try {
            targetUid = Long.parseLong(uidStr.trim());
        } catch (NumberFormatException e) {
            response.setStatus(400);
            return errorResponse(5525, "unknown server error", "invalid uid to show name card");
        }

        JSONObject card = NameCardBuilder.buildFriendDetail(targetUid, false);
        if (card == null) {
            response.setStatus(400);
            ArknightsApplication.LOGGER.info("[businessCard] 查不到 uid={} 的名片", targetUid);
            return errorResponse(5525, "unknown server error", "player not found: " + targetUid);
        }
        ArknightsApplication.LOGGER.info("[businessCard] 返回 uid={} 的名片，src={}", targetUid, srcRaw);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", new JSONObject(true));
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject out = new JSONObject(true);
        out.put("nameCard", card);
        out.put("playerDataDelta", playerDataDelta);
        return out;
    }

    private static JSONObject defaultNameCardStyle() {
        JSONObject style = new JSONObject(true);
        JSONArray componentOrder = new JSONArray();
        componentOrder.add("module_sign");
        componentOrder.add("module_assist");
        componentOrder.add("module_medal");
        style.put("componentOrder", componentOrder);

        JSONObject skin = new JSONObject(true);
        skin.put("selected", "nc_rhodes_default");
        skin.put("state", new JSONObject(true));
        skin.put("tmpl", new JSONObject(true));
        style.put("skin", skin);

        JSONObject misc = new JSONObject(true);
        misc.put("showDetail", true);
        misc.put("showBirthday", false);
        style.put("misc", misc);
        return style;
    }

    private static boolean isZeroOrOne(Object value) {
        if (value == null || value instanceof Boolean) {
            return false;
        }
        if (value instanceof Number) {
            int v = ((Number) value).intValue();
            return v == 0 || v == 1;
        }
        String s = String.valueOf(value).trim();
        return "0".equals(s) || "1".equals(s);
    }

    private static boolean intToBool(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return "1".equals(String.valueOf(value).trim());
    }

    private Account auth(String secret, HttpServletResponse response, JSONObject holder) {
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            holder.put("__error", errorResponse(400, "服务器维护中"));
            return null;
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            holder.put("__error", errorResponse(2, "账户验证失败"));
            return null;
        }
        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            holder.put("__error", errorResponse(403, "账号已被封禁"));
            return null;
        }
        holder.put("user", JSONObject.parseObject(account.getUser()));
        return account;
    }

    private JSONObject errorResponse(int code, String msg) {
        JSONObject out = new JSONObject(true);
        out.put("code", code);
        out.put("msg", msg);
        out.put("message", "server error");
        return out;
    }

    private JSONObject errorResponse(int code, String msg, String info) {
        JSONObject out = errorResponse(code, msg);
        out.put("info", info);
        return out;
    }
}
