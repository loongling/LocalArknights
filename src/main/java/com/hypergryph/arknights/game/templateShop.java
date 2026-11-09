package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

import static com.hypergryph.arknights.ArknightsApplication.LOGGER;
import static com.hypergryph.arknights.core.work.checkAccount.checkAccount;
import static com.hypergryph.arknights.core.work.checkAccount.createError;

/**
 * 活动模板商店（/templateShop/*）
 * 数据源：data/shop/templateShop.json（shopId → {shopName,type,price,shopGroup…}）
 * 协议参照官服抓包：
 *   parsed_live_1432/019_01__templateShop_getGoodList.json
 *   parsed_live_1450/031_02~05__templateShop_buyGood.json
 */
@RestController
@RequestMapping("/templateShop")
public class templateShop {

    private static volatile JSONObject SHOP_TABLE = null;
    private static volatile long SHOP_TABLE_TS = -1;

    private static synchronized JSONObject shopTable() {
        long ts = ArknightsApplication.lastReloadTs;
        if (SHOP_TABLE == null || SHOP_TABLE_TS != ts) {
            // 用 OrderedField 解析，保持 json 里字段的原始顺序（和官服响应一致）
            JSONObject t = null;
            try {
                String raw = new String(java.nio.file.Files.readAllBytes(
                        java.nio.file.Path.of(System.getProperty("user.dir") + "/data/shop/templateShop.json")),
                        java.nio.charset.StandardCharsets.UTF_8);
                if (raw.startsWith("\uFEFF")) {
                    raw = raw.substring(1);
                }
                t = com.alibaba.fastjson.JSON.parseObject(raw,
                        com.alibaba.fastjson.parser.Feature.OrderedField);
            } catch (Exception e) {
                LOGGER.warn("[templateShop] 读取 templateShop.json 失败: " + e.toString());
            }
            SHOP_TABLE = t == null ? new JSONObject(true) : t;
            SHOP_TABLE_TS = ts;
            LOGGER.info("[templateShop] 载入 templateShop.json（{} 个商店）", SHOP_TABLE.size());
        }
        return SHOP_TABLE;
    }

    /** 商店商品列表：{shopId} → {data, nextSyncTime, playerDataDelta} */
    @PostMapping(value = {"/getGoodList"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject getGoodList(@RequestHeader(value = "secret", required = false) String secret,
            @RequestBody JSONObject requestBody,
            HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String shopId = requestBody == null ? null : requestBody.getString("shopId");
        LOGGER.info("[" + clientIp + "] /templateShop/getGoodList shopId=" + shopId);
        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        JSONObject shop = shopId == null ? null : shopTable().getJSONObject(shopId);
        if (shop == null) {
            return error("没有这个商店: " + shopId);
        }
        JSONObject result = new JSONObject(true);
        result.put("data", shop);   // ordered map，直接引用以保留字段顺序
        result.put("nextSyncTime", -1);
        JSONObject delta = new JSONObject(true);
        delta.put("modified", new JSONObject(true));
        delta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", delta);
        return result;
    }

    /**
     * 购买：{shopId, goodId, count} → {itemList, playerDataDelta}
     * 扣活动代币（user.activity.&lt;类型&gt;.&lt;actId&gt;.coin），购买次数记 user.tshop.&lt;shopId&gt;
     */
    @PostMapping(value = {"/buyGood"}, produces = {"application/json;charset=UTF-8"})
    public JSONObject buyGood(@RequestHeader(value = "secret", required = false) String secret,
            @RequestBody JSONObject requestBody,
            HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String shopId = requestBody == null ? null : requestBody.getString("shopId");
        String goodId = requestBody == null ? null : requestBody.getString("goodId");
        int wantCount = requestBody == null ? 1 : Math.max(1, requestBody.getIntValue("count"));
        LOGGER.info("[" + clientIp + "] /templateShop/buyGood shopId=" + shopId + " goodId=" + goodId + " count=" + wantCount);

        if (!checkAccount(secret)) {
            return createError(secret, response);
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts == null || accounts.size() != 1) {
            return error("无法查询到此账户");
        }
        Account account = accounts.get(0);
        JSONObject user = com.alibaba.fastjson.JSON.parseObject(account.getUser());

        JSONObject shop = shopId == null ? null : shopTable().getJSONObject(shopId);
        if (shop == null) {
            return error("没有这个商店: " + shopId);
        }
        JSONObject good = findGood(shop, goodId);
        if (good == null) {
            return error("商店里没有这件商品: " + goodId);
        }
        if ("PROGRESS".equals(good.getString("goodType"))) {
            return error("进度奖励不能直接购买: " + goodId);
        }
        JSONObject item = good.getJSONObject("item");
        if (item == null) {
            return error("商品没有配置物品: " + goodId);
        }

        JSONObject tshopNode = ensureTshop(user, shopId);
        String currencyId = shop.getJSONObject("price") == null ? null : shop.getJSONObject("price").getString("id");
        String currencyType = shop.getJSONObject("price") == null ? null : shop.getJSONObject("price").getString("type");
        int unitPrice = good.getIntValue("price");
        int totalCost = unitPrice * wantCount;
        int avail = good.getIntValue("availCount");
        int bought = purchasedCount(tshopNode, goodId);
        if (avail >= 0 && bought + wantCount > avail) {
            return error("超出限购数量: " + goodId + "（已买 " + bought + " / 限 " + avail + "）");
        }

        // ── 扣货币 ──
        String[] actRef = null;
        JSONObject actState = null;
        if ("ACTIVITY_ITEM".equals(currencyType) && currencyId != null) {
            actRef = resolveActivityOfItem(currencyId);
            if (actRef == null) {
                return error("找不到代币对应的活动: " + currencyId);
            }
            actState = ensureActivityState(user, actRef[0], actRef[1]);
            int coin = actState.getIntValue("coin");
            if (coin < totalCost) {
                return error("代币不足：需要 " + totalCost + "，当前 " + coin);
            }
            actState.put("coin", coin - totalCost);
        } else if (currencyId != null) {
            JSONObject inventory = user.getJSONObject("inventory");
            int have = inventory == null ? 0 : inventory.getIntValue(currencyId);
            if (have < totalCost) {
                return error("货币不足：需要 " + totalCost + "，当前 " + have);
            }
            inventory.put(currencyId, have - totalCost);
        }

        // ── 发物品 ──
        JSONObject snap = snapshot(user);
        JSONArray itemList = new JSONArray();
        int grantCount = item.getIntValue("count") * wantCount;
        String itemType = item.getString("type");
        String itemId = item.getString("id");
        if ("CHAR_SKIN".equals(itemType)) {
            // 时装：handleUserDataChange 不认识这个类型，直接写 user.skin
            grantSkin(user, itemId);
        } else if ("FURN".equals(itemType)) {
            // 家具同理，直接写 building.furniture + solution.furnitureTs
            grantFurniture(user, itemId, grantCount);
        } else {
            JSONObject grant = new JSONObject(true);
            grant.put("id", itemId);
            grant.put("type", itemType);
            grant.put("count", grantCount);
            com.hypergryph.arknights.core.work.processArray
                    .handleUserDataChange(grant, user, account.getUid(), 0, itemList);
        }
        // itemList 由我们回显（handleUserDataChange 只在干员时写 itemsOut）
        JSONObject echoed = new JSONObject(true);
        echoed.put("type", itemType);
        echoed.put("id", itemId);
        echoed.put("count", grantCount);
        itemList.clear();
        itemList.add(echoed);

        // ── 记录购买 + 代币余额 ──
        bumpPurchased(tshopNode, goodId, wantCount);
        tshopNode.put("coin", actState != null ? actState.getIntValue("coin")
                : (currencyId == null ? 0 : user.getJSONObject("inventory").getIntValue(currencyId)));
        if (tshopNode.getJSONObject("progressInfo") == null) {
            tshopNode.put("progressInfo", new JSONObject(true));
        }

        userDao.setUserData(account.getUid(), user);

        // ── 组装 delta ──
        JSONObject modified = new JSONObject(true);
        modified.put("activity", activityDelta(user, actRef));
        JSONObject tshopMod = new JSONObject(true);
        tshopMod.put(shopId, tshopNode);
        modified.put("tshop", tshopMod);
        mergeGranted(modified, snap, user);

        JSONObject delta = new JSONObject(true);
        delta.put("modified", modified);
        delta.put("deleted", new JSONObject(true));
        JSONObject result = new JSONObject(true);
        result.put("itemList", itemList);
        result.put("playerDataDelta", delta);
        LOGGER.info("[templateShop] 购买 " + goodId + " x" + wantCount + " 花费 " + totalCost
                + " " + currencyId + "，余额 " + tshopNode.getIntValue("coin"));
        return result;
    }

    // ───────────────────────── 辅助 ─────────────────────────

    private static JSONObject error(String msg) {
        JSONObject bad = new JSONObject(true);
        bad.put("result", 1);
        bad.put("error", msg);
        return bad;
    }

    /** 在 shopGroup.*.shopGood 里找商品 */
    private static JSONObject findGood(JSONObject shop, String goodId) {
        JSONObject groups = shop.getJSONObject("shopGroup");
        if (groups == null || goodId == null) {
            return null;
        }
        for (String g : groups.keySet()) {
            JSONObject group = groups.getJSONObject(g);
            JSONObject goodList = group == null ? null : group.getJSONObject("shopGood");
            if (goodList != null && goodList.getJSONObject(goodId) != null) {
                return goodList.getJSONObject(goodId);
            }
        }
        return null;
    }

    /** user.tshop.&lt;shopId&gt;（没有就建） */
    private static JSONObject ensureTshop(JSONObject user, String shopId) {
        JSONObject tshop = user.getJSONObject("tshop");
        if (tshop == null) {
            tshop = new JSONObject(true);
            user.put("tshop", tshop);
        }
        JSONObject node = tshop.getJSONObject(shopId);
        if (node == null) {
            node = new JSONObject(true);
            node.put("coin", 0);
            node.put("info", new JSONArray());
            node.put("progressInfo", new JSONObject(true));
            tshop.put(shopId, node);
        }
        if (node.getJSONArray("info") == null) {
            node.put("info", new JSONArray());
        }
        return node;
    }

    private static int purchasedCount(JSONObject tshopNode, String goodId) {
        JSONArray info = tshopNode.getJSONArray("info");
        if (info == null) {
            return 0;
        }
        for (int i = 0; i < info.size(); i++) {
            JSONObject one = info.getJSONObject(i);
            if (one != null && goodId.equals(one.getString("id"))) {
                return one.getIntValue("count");
            }
        }
        return 0;
    }

    private static void bumpPurchased(JSONObject tshopNode, String goodId, int count) {
        JSONArray info = tshopNode.getJSONArray("info");
        for (int i = 0; i < info.size(); i++) {
            JSONObject one = info.getJSONObject(i);
            if (one != null && goodId.equals(one.getString("id"))) {
                one.put("count", one.getIntValue("count") + count);
                return;
            }
        }
        JSONObject one = new JSONObject(true);
        one.put("id", goodId);
        one.put("count", count);
        info.add(one);
    }

    /** itemId → {活动类型, 活动id}（activity_table.basicInfo 前缀匹配） */
    private static String[] resolveActivityOfItem(String itemId) {
        if (itemId == null) {
            return null;
        }
        JSONObject basic = activity.activityTable() == null ? null
                : activity.activityTable().getJSONObject("basicInfo");
        if (basic == null) {
            return null;
        }
        String best = null;
        for (String k : basic.keySet()) {
            if (itemId.equals(k) || itemId.startsWith(k + "_")) {
                if (best == null || k.length() > best.length()) {
                    best = k;
                }
            }
        }
        if (best == null) {
            return null;
        }
        JSONObject info = basic.getJSONObject(best);
        String type = info == null ? null : info.getString("type");
        return type == null ? null : new String[]{type, best};
    }

    private static JSONObject ensureActivityState(JSONObject user, String type, String actId) {
        JSONObject activityNode = user.getJSONObject("activity");
        if (activityNode == null) {
            activityNode = new JSONObject(true);
            user.put("activity", activityNode);
        }
        JSONObject group = activityNode.getJSONObject(type);
        if (group == null) {
            group = new JSONObject(true);
            activityNode.put(type, group);
        }
        JSONObject st = group.getJSONObject(actId);
        if (st == null) {
            st = new JSONObject(true);
            st.put("coin", 0);
            group.put(actId, st);
        }
        return st;
    }

    /** {类型:{actId:{coin}}}，actRef 为 null 时返回空对象 */
    private static JSONObject activityDelta(JSONObject user, String[] actRef) {
        JSONObject out = new JSONObject(true);
        if (actRef == null) {
            return out;
        }
        JSONObject st = ensureActivityState(user, actRef[0], actRef[1]);
        JSONObject state = new JSONObject(true);
        state.put("coin", st.getIntValue("coin"));
        JSONObject group = new JSONObject(true);
        group.put(actRef[1], state);
        out.put(actRef[0], group);
        return out;
    }

    /** 家具入账：building.furniture[furniId] + solution.furnitureTs[furniId] */
    private static void grantFurniture(JSONObject user, String furniId, int count) {
        JSONObject building = user.getJSONObject("building");
        if (building == null) {
            building = new JSONObject(true);
            user.put("building", building);
        }
        JSONObject furniture = building.getJSONObject("furniture");
        if (furniture == null) {
            furniture = new JSONObject(true);
            building.put("furniture", furniture);
        }
        JSONObject one = furniture.getJSONObject(furniId);
        if (one == null) {
            one = new JSONObject(true);
            one.put("count", count);
            one.put("inUse", 0);
            furniture.put(furniId, one);
        } else {
            one.put("count", one.getIntValue("count") + count);
        }
        JSONObject solution = building.getJSONObject("solution");
        if (solution == null) {
            solution = new JSONObject(true);
            building.put("solution", solution);
        }
        JSONObject furnitureTs = solution.getJSONObject("furnitureTs");
        if (furnitureTs == null) {
            furnitureTs = new JSONObject(true);
            solution.put("furnitureTs", furnitureTs);
        }
        furnitureTs.put(furniId, ArknightsApplication.getTimestamp());
    }

    /** 时装入账：user.skin.characterSkins[skinId] = 1 + skinTs */
    private static void grantSkin(JSONObject user, String skinId) {
        JSONObject skin = user.getJSONObject("skin");
        if (skin == null) {
            skin = new JSONObject(true);
            user.put("skin", skin);
        }
        JSONObject charSkins = skin.getJSONObject("characterSkins");
        if (charSkins == null) {
            charSkins = new JSONObject(true);
            skin.put("characterSkins", charSkins);
        }
        JSONObject skinTs = skin.getJSONObject("skinTs");
        if (skinTs == null) {
            skinTs = new JSONObject(true);
            skin.put("skinTs", skinTs);
        }
        charSkins.put(skinId, 1);
        skinTs.put(skinId, ArknightsApplication.getTimestamp());
    }

    /** status/inventory/building.chars/troop.chars/dexNav.character 快照 */
    private static JSONObject snapshot(JSONObject user) {
        JSONObject snap = new JSONObject(true);
        for (String node : new String[]{"status", "inventory", "consumable"}) {
            JSONObject v = user.getJSONObject(node);
            snap.put(node, v == null ? new JSONObject(true) : com.alibaba.fastjson.JSON.parseObject(v.toJSONString()));
        }
        JSONObject building = user.getJSONObject("building");
        JSONObject bChars = building == null ? null : building.getJSONObject("chars");
        snap.put("buildingChars", bChars == null ? new JSONObject(true) : com.alibaba.fastjson.JSON.parseObject(bChars.toJSONString()));
        JSONObject troop = user.getJSONObject("troop");
        JSONObject tChars = troop == null ? null : troop.getJSONObject("chars");
        snap.put("troopChars", tChars == null ? new JSONObject(true) : com.alibaba.fastjson.JSON.parseObject(tChars.toJSONString()));
        JSONObject furniture = building == null ? null : building.getJSONObject("furniture");
        snap.put("furniture", furniture == null ? new JSONObject(true) : com.alibaba.fastjson.JSON.parseObject(furniture.toJSONString()));
        JSONObject skin = user.getJSONObject("skin");
        JSONObject charSkins = skin == null ? null : skin.getJSONObject("characterSkins");
        snap.put("characterSkins", charSkins == null ? new JSONObject(true) : com.alibaba.fastjson.JSON.parseObject(charSkins.toJSONString()));
        return snap;
    }

    /** 把本次发放引起的变化并进 modified */
    private static void mergeGranted(JSONObject modified, JSONObject snap, JSONObject user) {
        JSONObject statusBefore = snap.getJSONObject("status");
        JSONObject statusAfter = user.getJSONObject("status");
        if (statusBefore != null && statusAfter != null) {
            JSONObject status = new JSONObject(true);
            for (String k : statusAfter.keySet()) {
                Object a = statusAfter.get(k);
                Object b = statusBefore.get(k);
                if (a instanceof Number && b instanceof Number
                        && ((Number) a).longValue() != ((Number) b).longValue()) {
                    status.put(k, a);
                }
            }
            if (!status.isEmpty()) {
                modified.put("status", status);
            }
        }
        JSONObject invBefore = snap.getJSONObject("inventory");
        JSONObject invAfter = user.getJSONObject("inventory");
        if (invBefore != null && invAfter != null) {
            JSONObject inv = new JSONObject(true);
            for (String k : invAfter.keySet()) {
                Object a = invAfter.get(k);
                Object b = invBefore.get(k);
                if (a instanceof Number && b instanceof Number
                        && ((Number) a).longValue() != ((Number) b).longValue()) {
                    inv.put(k, a);
                }
            }
            if (!inv.isEmpty()) {
                modified.put("inventory", inv);
            }
        }
        JSONObject building = user.getJSONObject("building");
        JSONObject bAfter = building == null ? null : building.getJSONObject("chars");
        if (bAfter != null) {
            JSONObject bBefore = snap.getJSONObject("buildingChars");
            JSONObject chars = new JSONObject(true);
            for (String k : bAfter.keySet()) {
                if (bBefore == null || !bBefore.containsKey(k)) {
                    chars.put(k, bAfter.getJSONObject(k));
                }
            }
            if (!chars.isEmpty()) {
                JSONObject b = new JSONObject(true);
                b.put("chars", chars);
                modified.put("building", b);
            }
        }
        JSONObject troop = user.getJSONObject("troop");
        JSONObject tAfter = troop == null ? null : troop.getJSONObject("chars");
        if (tAfter != null) {
            JSONObject tBefore = snap.getJSONObject("troopChars");
            JSONObject chars = new JSONObject(true);
            for (String k : tAfter.keySet()) {
                if (tBefore == null || !tBefore.containsKey(k)) {
                    chars.put(k, tAfter.getJSONObject(k));
                }
            }
            if (!chars.isEmpty()) {
                JSONObject t = new JSONObject(true);
                t.put("chars", chars);
                modified.put("troop", t);
            }
        }
        // 家具：只回本次变化的条目（别把整个家具表塞回去）
        if (building != null) {
            JSONObject furniture = building.getJSONObject("furniture");
            JSONObject furBefore = snap.getJSONObject("furniture");
            if (furniture != null) {
                JSONObject furMod = new JSONObject(true);
                for (String k : furniture.keySet()) {
                    Object a = furniture.get(k);
                    Object b = furBefore == null ? null : furBefore.get(k);
                    if (b == null || !String.valueOf(a).equals(String.valueOf(b))) {
                        furMod.put(k, a);
                    }
                }
                if (!furMod.isEmpty()) {
                    JSONObject modBuilding = modified.getJSONObject("building");
                    if (modBuilding == null) {
                        modBuilding = new JSONObject(true);
                        modified.put("building", modBuilding);
                    }
                    modBuilding.put("furniture", furMod);
                    JSONObject solution = building.getJSONObject("solution");
                    JSONObject furTs = solution == null ? null : solution.getJSONObject("furnitureTs");
                    if (furTs != null) {
                        JSONObject tsMod = new JSONObject(true);
                        for (String k : furMod.keySet()) {
                            if (furTs.containsKey(k)) {
                                tsMod.put(k, furTs.get(k));
                            }
                        }
                        if (!tsMod.isEmpty()) {
                            JSONObject sol = new JSONObject(true);
                            sol.put("furnitureTs", tsMod);
                            modBuilding.put("solution", sol);
                        }
                    }
                }
            }
        }
        // 时装
        JSONObject skin = user.getJSONObject("skin");
        JSONObject charSkins = skin == null ? null : skin.getJSONObject("characterSkins");
        JSONObject beforeSkins = snap.getJSONObject("characterSkins");
        if (charSkins != null) {
            JSONObject skinMod = new JSONObject(true);
            JSONObject tsMod = new JSONObject(true);
            JSONObject skinTs = skin.getJSONObject("skinTs");
            for (String k : charSkins.keySet()) {
                if (beforeSkins == null || !beforeSkins.containsKey(k)) {
                    skinMod.put(k, charSkins.get(k));
                    if (skinTs != null && skinTs.containsKey(k)) {
                        tsMod.put(k, skinTs.get(k));
                    }
                }
            }
            if (!skinMod.isEmpty()) {
                JSONObject s = new JSONObject(true);
                s.put("characterSkins", skinMod);
                if (!tsMod.isEmpty()) {
                    s.put("skinTs", tsMod);
                }
                modified.put("skin", s);
            }
        }
    }
}
