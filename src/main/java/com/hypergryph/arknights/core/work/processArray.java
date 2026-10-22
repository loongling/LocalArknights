package com.hypergryph.arknights.core.work;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;

import static com.hypergryph.arknights.core.work.consumableNumberG.*;

public class processArray {

    public static JSONArray removeByGoodId(JSONArray array, String id) {
        if (array == null || array.isEmpty() || id == null) {
            return null;
        }

        for (int i = 0; i < array.size(); i++) {
            if (array.get(i) instanceof JSONObject item
                    && Objects.equals(id, item.getString("id"))) {
                array.remove(i);
                return array;
            }
        }
        return null;
    }

    public static JSONObject handleModifiedBuild(JSONObject obj, JSONObject modified, JSONObject user, int i) {
        int count = obj.getInteger("count");
        String id = obj.getString("id");
        String type = obj.getString("type");
        int T = ArknightsApplication.checkCheckInItemType(type);
        JSONObject changedStatus = new JSONObject();
        JSONObject status = user.getJSONObject("status");
        JSONObject inventory = user.getJSONObject("inventory");
        JSONObject inventoryChanged = new JSONObject();
        JSONObject consumable = user.getJSONObject("consumable");
        JSONObject consumableChanged = new JSONObject();
        if (T == 1) {
            int LMD = status.getInteger("gold");
            count = LMD + count;
            changedStatus.put("gold", count);
            modified.put("status", changedStatus);
            return modified;
        }
        else if (T == 2) {
            if (inventory.getInteger(id) != null) {
                int counts = inventory.getInteger(id);
                count = count + counts;
                inventory.put(id, count);
                modified.put("inventory", inventoryChanged);
                return modified;
            } else {
                inventoryChanged.put(id, count);
                modified.put("inventory", inventoryChanged);
                return modified;
            }
        }
        else if (T == 3) {
            int TKT = resolveTKT(type);
            if (TKT == 0) {
                int GachaTKT = status.getInteger("gachaTicket");
                GachaTKT = GachaTKT + count;
                changedStatus.put("gachaTicket", GachaTKT);
                modified.put("status", changedStatus);
                return modified;
            }
            else if (TKT == 1) {
                int GachaTKT10 = status.getInteger("tenGachaTicket");
                GachaTKT10 = GachaTKT10 + count;
                changedStatus.put("tenGachaTicket", GachaTKT10);
                modified.put("status", changedStatus);
                return modified;
            }
            else if (TKT == 2) {
                if (isExist(id, consumable)){
                    JSONObject A = consumable.getJSONObject(id);
                    JSONObject B = getInnerObject(A);
                    int counts = B.getInteger("count");
                    count = count + counts;
                    int me = getInnerNumber(A);
                    B.put("count", count);
                    JSONObject C = new JSONObject();
                    C.put(String.valueOf(me), B);
                    consumableChanged.put(id, C);
                }
                else {
                    int me = nextItemNumber(consumable);
                    JSONObject A = new JSONObject();
                    A.put("count", count);
                    String specialDesc = "";
                    long ts = -1;
                    if (i >= 0 && i < ArknightsApplication.GPGoodList.getJSONArray("oneTimeGP").size()) {
                        JSONObject item = ArknightsApplication.GPGoodList.getJSONArray("oneTimeGP").getJSONObject(i);

                        JSONObject specialItemInfos = item.getJSONObject("specialItemInfos").getJSONObject(id);
                        if (specialItemInfos != null && !specialItemInfos.isEmpty()) {
                            specialDesc = specialItemInfos.getString("specialDesc");
                            ts = parseExpireTs(specialDesc);
                        }
                    }
                    A.put("ts", ts);
                    JSONObject B = new JSONObject();
                    B.put(String.valueOf(me), A);
                    consumableChanged.put(id, B);
                }
                modified.put("consumable", consumableChanged);
                return modified;
            }
        } else if(T == 5){
            if (isExist(id, consumable)){
                JSONObject A = consumable.getJSONObject(id);
                JSONObject B = getInnerObject(A);
                int counts = B.getInteger("count");
                count = count + counts;
                int me = getInnerNumber(A);
                B.put("count", count);
                JSONObject C = new JSONObject();
                C.put(String.valueOf(me), B);
                consumableChanged.put(id, C);
            } else  {
                int me = nextItemNumber(consumable);
                JSONObject A = new JSONObject();
                A.put("count", count);
                A.put("ts", -1);
                JSONObject C = new JSONObject();
                C.put(String.valueOf(me), A);
                consumableChanged.put(id, C);
                modified.put("consumable", consumableChanged);
                return modified;
            }
        } else if(T == 6){
            int andD = status.getInteger("androidDiamond");
            int iosD = status.getInteger("iosDiamond");
            andD = andD + count;
            iosD = iosD + count;
            changedStatus.put("androidDiamond", andD);
            changedStatus.put("iosDiamond", iosD);
            modified.put("status", changedStatus);
            return modified;
        } else  if(T == 9){
            if(type.equals("HOME_THEME")){
                JSONObject homeTheme = new JSONObject();
                JSONObject theme = new JSONObject();
                JSONObject add = new JSONObject();
                add.put("unlock", ArknightsApplication.getTimestamp());
                theme.put(id, add);
                homeTheme.put("themes", theme);
                modified.put("homeTheme", homeTheme);
                return modified;
            }
            else if(type.equals("HOME_BACKGROUND")){
                JSONObject background = new JSONObject();
                JSONObject bgs = new JSONObject();
                JSONObject add = new JSONObject();
                add.put("unlock", ArknightsApplication.getTimestamp());
                bgs.put(id, add);
                background.put("bgs", bgs);
                modified.put("background", background);
                return modified;
            }
            else if(type.equals("PLAYER_AVATAR")){
                JSONObject avatar = new JSONObject();
                JSONObject icon = new JSONObject();
                JSONObject add = new JSONObject();
                add.put("ts", ArknightsApplication.getTimestamp());
                add.put("src", "other");
                icon.put(id, add);
                avatar.put("avatar_icon", icon);
                modified.put("avatar", avatar);
                return modified;
            }
        }
        else if(T == 10){
            int o_shd = status.getInteger("diamondShard");
            changedStatus.put("diamondShard", o_shd + count);
            modified.put("status", changedStatus);
            return modified;
        }
        return modified;
    }

    private static final Pattern PATTERN =
            Pattern.compile("(\\d{1,2})月(\\d{1,2})日(\\d{1,2}):(\\d{2})");

    public static long parseExpireTs(String text) {
        Matcher m = PATTERN.matcher(text);
        if (!m.find()) {
            throw new IllegalArgumentException("未找到过期时间: " + text);
        }

        int month = Integer.parseInt(m.group(1));
        int day   = Integer.parseInt(m.group(2));
        int hour  = Integer.parseInt(m.group(3));
        int minute = Integer.parseInt(m.group(4));

        int year = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).getYear();

        LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, 0);

        //若解析出的时间已早于"现在"，说明跨年了，年份 +1
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        if (ldt.isBefore(now.minusDays(1))) {   // 容错 1 天，避免边界误判
            ldt = ldt.plusYears(1);
        }

        return ldt.atZone(ZoneId.of("Asia/Shanghai"))
                .toInstant()
                .toEpochMilli();
    }

    public static JSONObject handleUserDataChange(JSONObject obj, JSONObject user, long uid, int i) {
        int count = obj.getInteger("count");
        String id = obj.getString("id");
        String type = obj.getString("type");
        int T = ArknightsApplication.checkCheckInItemType(type);
        JSONObject changedStatus = new JSONObject();
        JSONObject status = user.getJSONObject("status");
        JSONObject inventory = user.getJSONObject("inventory");
        JSONObject inventoryChanged = new JSONObject();
        JSONObject consumable = user.getJSONObject("consumable");
        JSONObject consumableChanged = new JSONObject();
        if(T == 1) {
            int o_LMD =  status.getInteger("gold");
            user.getJSONObject("status").put("gold", o_LMD + count);
            return user;
        }
        else if(T == 2) {
            if (inventory.getInteger(id) != null) {
                int o_counts = inventory.getInteger(id);
                user.getJSONObject("inventory").put(id, o_counts + count);
                return user;
            } else {
                user.getJSONObject("inventory").put(id, count);
                return user;
            }
        } else if(T == 3) {
            int TKT = resolveTKT(type);
            if (TKT == 0) {
                int GachaTKT = status.getInteger("gachaTicket");
                GachaTKT = GachaTKT + count;
                user.getJSONObject("status").put("gachaTicket", GachaTKT);
                return user;
            }
            else if (TKT == 1) {
                int GachaTKT10 = status.getInteger("tenGachaTicket");
                GachaTKT10 = GachaTKT10 + count;
                user.getJSONObject("status").put("tenGachaTicket", GachaTKT10);
                return user;
            }
            else if (TKT == 2) {
                if (isExist(id, consumable)){
                    JSONObject A = consumable.getJSONObject(id);
                    JSONObject B = getInnerObject(A);
                    int counts = B.getInteger("count");
                    count = count + counts;
                    int me = getInnerNumber(A);
                    B.put("count", count);
                    JSONObject C = new JSONObject();
                    C.put(String.valueOf(me), B);
                    user.getJSONObject("consumable").put(id, C);
                    return user;
                }
                else {
                    int me = nextItemNumber(consumable);
                    JSONObject A = new JSONObject();
                    A.put("count", count);
                    String specialDesc = "";
                    long ts = -1;
                    if (i >= 0 && i < ArknightsApplication.GPGoodList.getJSONArray("oneTimeGP").size()) {
                        JSONObject item = ArknightsApplication.GPGoodList.getJSONArray("oneTimeGP").getJSONObject(i);

                        JSONObject specialItemInfos = item.getJSONObject("specialItemInfos").getJSONObject(id);
                        if (specialItemInfos != null && !specialItemInfos.isEmpty()) {
                            specialDesc = specialItemInfos.getString("specialDesc");
                            ts = parseExpireTs(specialDesc);
                        }
                    }
                    A.put("ts", ts);
                    JSONObject B = new JSONObject();
                    B.put(String.valueOf(me), A);
                    user.getJSONObject("consumable").put(id, B);
                    return user;
                }
            }
        }
        else if(T == 5) {
            if (isExist(id, consumable)){
                JSONObject A = consumable.getJSONObject(id);
                JSONObject B = getInnerObject(A);
                int counts = B.getInteger("count");
                count = count + counts;
                int me = getInnerNumber(A);
                B.put("count", count);
                JSONObject C = new JSONObject();
                C.put(String.valueOf(me), B);
                user.getJSONObject("consumable").put(id, C);
                return user;
            } else  {
                int me = nextItemNumber(consumable);
                JSONObject A = new JSONObject();
                A.put("count", count);
                A.put("ts", -1);
                JSONObject C = new JSONObject();
                C.put(String.valueOf(me), A);
                user.getJSONObject("consumable").put(id, C);
                return user;
            }
        }
        else if(T == 6){
            int andD = status.getInteger("androidDiamond");
            int iosD = status.getInteger("iosDiamond");
            andD = andD + count;
            iosD = iosD + count;
            user.getJSONObject("status").put("androidDiamond", andD);
            user.getJSONObject("status").put("iosDiamond", iosD);
            return user;
        }
        else  if(T == 9){
            if(type.equals("HOME_THEME")){
                JSONObject add = new JSONObject();
                add.put("unlock", ArknightsApplication.getTimestamp());
                user.getJSONObject("homeTheme").getJSONObject("themes").put(id, add);
                return user;
            }
            else if(type.equals("HOME_BACKGROUND")){
                JSONObject add = new JSONObject();
                add.put("unlock", ArknightsApplication.getTimestamp());
                user.getJSONObject("background").getJSONObject("bgs").put(id, add);
                return  user;
            }
            else if(type.equals("PLAYER_AVATAR")){
                JSONObject add = new JSONObject();
                add.put("ts", ArknightsApplication.getTimestamp());
                add.put("src", "other");
                user.getJSONObject("avatar").getJSONObject("avatar_icon").put(id, add);
                return  user;
            }
        }
        else if(T == 10){
            int o_shd = status.getInteger("diamondShard");
            user.getJSONObject("status").put("diamondShard", o_shd + count);
            return user;
        }
        return user;
    }

    private static int resolveTKT(String TKT) {
        if (TKT == null) {
            return -1;
        }
        return switch (TKT.trim().toUpperCase(Locale.ROOT)) {
            case "TKT_GACHA" -> 0;
            case "TKT_GACHA_10" -> 1;
            case "EXCLUSIVE_TKT_GACHA", "EXCLUSIVE_TKT_GACHA_10" -> 2;
            default -> -1;
        };
    }

    public static boolean handleTenGachaCould(JSONArray itemList, JSONObject userData) {
        ArknightsApplication.LOGGER.info("检查itemList" + itemList);
        List<JSONObject> items = new ArrayList<>(2);
        if (itemList != null) {
            for (int i = 0; i < itemList.size(); i++) {
                Object o = itemList.get(i);
                if (o instanceof Map) {                 // 兼容 fastjson1 / fastjson2
                    items.add(itemList.getJSONObject(i)); // 统一转成 com.alibaba.fastjson.JSONObject
                }
            }
        }
        ArknightsApplication.LOGGER.info("检查items" + items + " size=" + items.size());
        if (items.size() == 2) {
            JSONObject a = items.get(0);
            JSONObject b = items.get(1);
            String aId = a.getString("id");
            String bId = b.getString("id");
            int ACount = a.getInteger("count");
            int BCount = b.getInteger("count");
            return checkIsEnough(aId, ACount, userData) && checkIsEnough(bId, BCount, userData);
        } else if (items.size() == 1) {
            JSONObject only = items.get(0);
            String id = only.getString("id");
            int count = only.getInteger("count");
            return checkIsEnough(id, count, userData);
        }
        return false;
    }

    public static JSONObject handleTenGachaItem(JSONArray itemList,JSONObject userData) {
        List<JSONObject> items = new ArrayList<>(2);
        if (itemList != null) {
            for (int i = 0; i < itemList.size(); i++) {
                Object o = itemList.get(i);
                if (o instanceof Map) {
                    items.add(itemList.getJSONObject(i));
                }
            }
        }
        if (items.size() == 2) {
            JSONObject a = items.get(0);
            JSONObject b = items.get(1);
            String aId = a.getString("id");
            int ACount = a.getInteger("count");
            userData = costTenGachaItem(aId, ACount, userData);
            String bId = b.getString("id");
            int BCount = b.getInteger("count");
            userData = costTenGachaItem(bId, BCount, userData);
            return userData;
        } else if (items.size() == 1) {
            JSONObject only = items.get(0);
            String id = only.getString("id");
            int count = only.getInteger("count");
            return costTenGachaItem(id, count, userData);
        }
        return userData;
    }

    private static int checkId(String id){
        return switch (id) {
            case "4003" -> 0;
            case "7003" -> 1;
            case "7004" -> 2;
            default -> 3;
        };
    }

    private static final String MARK = "_GACHA";

    public static int parsePullCount(String gachaId) {
        if (gachaId == null) return -1;

        int idx = gachaId.indexOf(MARK);
        if (idx < 0) return -1;

        String rest = gachaId.substring(idx + MARK.length());
        // rest 只可能是: "", "_10", "_4位ID", "_10_4位ID"

        if (rest.equals("_10") || rest.startsWith("_10_")) {
            return 10;   // 十连（有无 4 位 ID 都算）
        }
        if (rest.isEmpty() || (rest.startsWith("_") && rest.length() == 5)) {
            return 1;    // 单抽（无 ID 或带 4 位 ID）
        }
        return -1;       // 格式异常
    }

    public static boolean checkIsEnough(String id, int count, JSONObject userData){
        int type = checkId(id);
        JSONObject status = userData.getJSONObject("status");
        if (type == 0) {
            int D = status.getInteger("diamondShard");
            return D >= count;
        }
        else if (type == 1) {
            int TKT = status.getInteger("gachaTicket");
            return TKT >= count;
        }
        else if (type == 2) {
            int TKT = status.getInteger("tenGachaTicket");
            return TKT >= count;
        }
        else if (type == 3) {
            JSONObject consumable = userData.getJSONObject("consumable");
            JSONObject A = consumable.getJSONObject(id);
            JSONObject TKT = getInnerObject(A);
            int counts = TKT.getInteger("count");
            return counts >= count;
        }
        return false;
    }

    private static JSONObject costTenGachaItem(String id, int count, JSONObject userData){
        int type = checkId(id);
        JSONObject status = userData.getJSONObject("status");
        if (type == 0) {
            int D = status.getInteger("diamondShard");
            userData.getJSONObject("status").put("diamondShard", D - count);
            return userData;
        }
        else if (type == 1) {
            int TKT = status.getInteger("gachaTicket");
            userData.getJSONObject("status").put("gachaTicket", TKT - count);
            return userData;
        }
        else if (type == 2) {
            int TKT = status.getInteger("tenGachaTicket");
            userData.getJSONObject("status").put("tenGachaTicket", TKT - count);
            return userData;
        }
        else if (type == 3) {
            int dropCount = parsePullCount(id);
            JSONObject consumable = userData.getJSONObject("consumable");
            JSONObject A = consumable.getJSONObject(id);
            JSONObject TKT = getInnerObject(A);
            int counts = TKT.getInteger("count");
            long ts  = TKT.getLong("ts");
            int me = getInnerNumber(A);
            JSONObject number = new JSONObject();
            JSONObject TKTc = new  JSONObject();
            if (dropCount == 1){
                number.put("count",counts - count);
                number.put("ts",ts);
                TKTc.put(String.valueOf(me), number);
                userData.getJSONObject("consumable").put(id, TKTc);
                return userData;
            }
            else if (dropCount == 10){
                number.put("count",counts - count);
                number.put("ts",ts);
                TKTc.put(String.valueOf(me), number);
                userData.getJSONObject("consumable").put(id, TKTc);
                return userData;
            }
        }
        return userData;
    }
}
