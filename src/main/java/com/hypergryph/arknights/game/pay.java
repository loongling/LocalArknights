package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONArray;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.orderIdDao;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.file.IOTools;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.boot.logging.LoggerGroup;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hypergryph.arknights.core.work.checkAccount.checkAccount;
import static com.hypergryph.arknights.core.work.getProduct.*;
import static com.hypergryph.arknights.core.work.processArray.handleModifiedBuild;
import static com.hypergryph.arknights.core.work.processArray.handleUserDataChange;

@RestController
@RequestMapping({"/pay"})
public class pay {

    @PostMapping(value = "/createOrder",
            produces = "application/json;charset=UTF-8")
    public JSONObject createOrder(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /pay/createOrder");
        ArknightsApplication.LOGGER.info("Received JSON: " + JsonBody.toJSONString());
        String check_orderId = orderIdDao.queryOrderIdBtSecret(secret);
        if (check_orderId != null) {
            orderIdDao.DelTable(check_orderId);
        }
        String phone = userDao.queryPhoneBySecret(secret);
        String GoodId = JsonBody.getString("goodId");
        int storeId = JsonBody.getInteger("storeId");
        JSONObject product = getProduct(storeId);
        JSONObject result = new JSONObject();
        JSONObject extension = new JSONObject();
        String orderId = ArknightsApplication.getTimestamp() + phone;
        int price = product.getInteger("price");
        String pName = product.getString("name");
        String pDes = product.getString("desc");
        long uid = userDao.queryUidBySecret(secret);
        long create_time = ArknightsApplication.getTimestamp();
        extension.put("appCode", "7318def77669979d");
        extension.put("amount", price);
        extension.put("productName", pName);
        JSONObject ext2 = new JSONObject();
        ext2.put("appStoreProductId", GoodId);
        ext2.put("harmonyProductId", GoodId);
        extension.put("uid", uid);
        extension.put("outOrderId", orderId);
        extension.put("ts", create_time);
        ext2.put("platform", 2);
        ext2.put("sign", "0cf6ed10808afa887cab5ab747e11e1e");
        extension.put("extension", ext2);
        result.put("orderId", orderId);
        String ext_string = JSON.toJSONString(extension);
        result.put("extension", ext_string);
        result.put("result", 0);
        result.put("alertMinor", 0);
        JSONObject playerDataDelta = new JSONObject();
        playerDataDelta.put("modified", new JSONObject());
        playerDataDelta.put("deleted", new JSONObject());
        result.put("playerDataDelta", playerDataDelta);
        int times = 0;
        orderIdDao.UpdateTable(secret, orderId, GoodId, storeId, price, pName, times, create_time);
        return result;
    };

    @GetMapping("/order/v1/state")
    public JSONObject getOrderState(@RequestHeader(value = "secret", required = false)String secret,@RequestParam("orderId") String orderId, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /pay/order/v1/state");
        JSONObject result = new JSONObject();
        JSONObject data = new JSONObject();
        JSONObject product = orderIdDao.queryAllBySecret(secret);
        long createTime = product.getLong("create_at");
        String productName  = product.getString("product_name");
        int amount = product.getInteger("price");
        data.put("productName", productName);
        data.put("amount", amount);
        data.put("createTime", createTime);
        data.put("productType", 2);
        data.put("appCode", "7318def77669979d");
        data.put("orderId", orderId);
        data.put("orderType", 0);
        data.put("pc", 1);
        result.put("data", data);
        result.put("type", "A");
        int time = ArknightsApplication.serverConfig.getJSONObject("pay").getInteger("time");
        if (orderIdDao.checkTimes(secret) < time) {
            result.put("status", 101);
            result.put("msg", "未支付");
            orderIdDao.updateTimes(secret);
            return result;
        }
        result.put("status", 0);
        result.put("msg", "已支付已回调");
        return result;
    }

    @PostMapping(
            value = "/confirmOrder",
            produces = "application/json;charset=UTF-8"
    )
    public JSONObject confirmOrder(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /pay/confirmOrder");
        ArknightsApplication.LOGGER.info("Received JSON: " + JsonBody.toJSONString());
        if(checkAccount(secret)) {
            String orderId = JsonBody.getString("orderId");
            JSONObject result = new JSONObject();
            int storeId = orderIdDao.queryStoreIdBtSecret(secret);
            long uid = userDao.queryUidBySecret(secret);
            JSONObject product = getProduct(storeId);
            String product_id = product.getString("product_id");
            int productType = ArknightsApplication.resolveGoodType(product_id);
            String GoodId = orderIdDao.queryGoodIdByOrderId(orderId);
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            Account account = Accounts.get(0);
            JSONObject user = JSON.parseObject(account.getUser());
            JSONObject status = user.getJSONObject("status");
            JSONObject playerDataDelta = new JSONObject();
            JSONObject modified = new JSONObject();
            playerDataDelta.put("deleted", new JSONObject());
            result.put("goodId", GoodId);
            JSONObject receiveItems = new JSONObject();
            result.put("result", 0);
            if (productType == 0) {
                if (ArknightsApplication.serverConfig.getJSONObject("pay").getBooleanValue("cashDoubleCount")) {
                    JSONObject cash = getCashGood(product_id);
                    int amount = cash.getInteger("doubleCount");
                    int android = status.getInteger("androidDiamond");
                    int ios = status.getInteger("iosDiamond");
                    status.put("androidDiamond", android + amount);
                    status.put("iosDiamond", ios + amount);
                    user.put("status", status);
                    userDao.setUserData(account.getUid(), user);
                    modified.put("status", status);
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    JSONArray items = new JSONArray();
                    JSONObject receiveItem = new JSONObject();
                    receiveItem.put("id", 4002);
                    receiveItem.put("count", amount);
                    receiveItem.put("type", "DIAMOND");
                    items.add(receiveItem);
                    receiveItems.put("items", items);
                    result.put("receiveItems", receiveItems);
                    orderIdDao.DelTable(orderId);
                    return result;
                } else {
                    JSONObject cash = getCashGood(product_id);
                    int first = cash.getInteger("diamondNum");
                    int plusAmount = cash.getInteger("plusNum");
                    int amount = first + plusAmount;
                    int android = status.getInteger("androidDiamond");
                    int ios = status.getInteger("iosDiamond");
                    status.put("androidDiamond", android + amount);
                    status.put("iosDiamond", ios + amount);
                    user.put("status", status);
                    userDao.setUserData(account.getUid(), user);
                    modified.put("status", status);
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    JSONObject receiveItem = new JSONObject();
                    JSONArray items = new JSONArray();
                    receiveItem.put("id", 4002);
                    receiveItem.put("count", amount);
                    receiveItem.put("type", "DIAMOND");
                    items.add(receiveItem);
                    receiveItems.put("items", items);
                    result.put("receiveItems", receiveItems);
                    JSONArray orderIdList = new JSONArray();
                    orderIdList.add(orderIdDao.queryAllBySecret(secret).getString("order_id"));
                    result.put("orderIdList", orderIdList);
                    orderIdDao.DelTable(orderId);
                    return result;
                }
            }
            if (productType == 1) {
                if (product_id.startsWith("GP_Once")) {
                    if (product_id.equals("GP_Once_106")) {
                        JSONObject A = new JSONObject();
                        A.put("id", "GP_Once_106");
                        A.put("count", 1);               //特殊处理唯一的GP_Once_106的干员获取
                        if (user.getJSONObject("troop").getJSONObject("chars").getJSONObject("151") == null) {
                            JSONObject resp = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/GP_Once_106_New_resp.json");
                            JSONObject chars = resp.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("troop").getJSONObject("chars").getJSONObject("151");
                            user.getJSONObject("troop").getJSONObject("chars").put("151", chars);
                            JSONObject charGroup = resp.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("troop").getJSONObject("charGroup").getJSONObject("char_151_myrtle");
                            user.getJSONObject("troop").getJSONObject("charGroup").put("char_151_myrtle", charGroup);
                            JSONObject building = resp.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("building").getJSONObject("chars").getJSONObject("151");
                            user.getJSONObject("building").getJSONObject("chars").put("151", building);
                            JSONObject dex = resp.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("dexNav").getJSONObject("character").getJSONObject("char_151_myrtle");
                            user.getJSONObject("dexNav").getJSONObject("character").put("char_151_myrtle",dex);
                            JSONObject shop = resp.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("shop").getJSONObject("GP").getJSONObject("oneTime").getJSONArray("info").getJSONObject(0);
                            user.getJSONObject("shop").getJSONObject("GP").getJSONObject("oneTime").getJSONArray("info").add(shop);
                            userDao.setUserData(uid, user);
                            JSONArray info =  user.getJSONObject("shop").getJSONObject("GP").getJSONObject("oneTime").getJSONArray("info");
                            resp.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("shop").getJSONObject("GP").getJSONObject("oneTime").put("info", info);
                            return resp;
                        } else {
                            JSONObject res = IOTools.ReadJsonFile(System.getProperty("user.dir") + "/data/GP_Once_106_noNew_resp.json");
                            int o_LMD = user.getJSONObject("status").getInteger("gold");
                            user.getJSONObject("status").put("gold", o_LMD + 10000);
                            int o_gachaTicket = user.getJSONObject("status").getInteger("gachaTicket");
                            user.getJSONObject("status").put("gachaTicket", o_gachaTicket + 1);
                            int o_lggShard = user.getJSONObject("status").getInteger("lggShard");
                            user.getJSONObject("status").put("lggShard", o_lggShard + 30);
                            res.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("status").put("gachaTicket",o_gachaTicket + 1);
                            res.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("status").put("lggShard", o_lggShard + 30);
                            int o_exp = 0;
                            if(user.getJSONObject("inventory").getInteger("2001") != null){
                                o_exp = user.getJSONObject("inventory").getInteger("2001");
                                user.getJSONObject("inventory").put("2001", o_exp + 20);
                                res.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("inventory").put("2001",o_exp + 20);
                            }
                            int p_char_115_myrtle;
                            if(user.getJSONObject("inventory").getInteger("p_char_115_myrtle") != null){
                                p_char_115_myrtle  = user.getJSONObject("inventory").getInteger("p_char_115_myrtle");
                                user.getJSONObject("inventory").put("p_char_115_myrtle", p_char_115_myrtle + 1);
                                res.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("inventory").put("p_char_115_myrtle", p_char_115_myrtle + 1);
                            } else  {
                                user.getJSONObject("inventory").put("p_char_115_myrtle", 1);
                                res.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("inventory").put("p_char_115_myrtle", 1);
                            }
                            int o_dexCount = user.getJSONObject("dexNav").getJSONObject("character").getJSONObject("char_151_myrtle").getInteger("count");
                            user.getJSONObject("dexNav").getJSONObject("character").getJSONObject("char_151_myrtle").put("count", o_dexCount + 1);
                            res.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("dexNav").getJSONObject("character").getJSONObject("char_151_myrtle").put("count", o_dexCount + 1);
                            JSONObject shop = res.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("shop").getJSONObject("GP").getJSONObject("ineTime").getJSONArray("info").getJSONObject(0);
                            user.getJSONObject("shop").getJSONObject("GP").getJSONObject("oneTime").getJSONArray("info").add(shop);
                            userDao.setUserData(uid, user);
                            JSONArray info =  user.getJSONObject("shop").getJSONObject("GP").getJSONObject("oneTime").getJSONArray("info");
                            res.getJSONObject("playerDataDelta").getJSONObject("modified").getJSONObject("shop").getJSONObject("GP").getJSONObject("oneTime").put("info", info);
                            return res;
                        }
                    } else {
                        JSONObject once = oneTimeGood(product_id);
                        JSONArray items = once.getJSONArray("items");
                        receiveItems.put("items", items);
                        if (items != null && !items.isEmpty()) {
                            for (int i = 0; i < items.size(); i++) {          // 遍历全部
                                if (items.get(i) instanceof JSONObject obj) {
                                    modified = handleModifiedBuild(obj, modified, user, i);
                                    user = handleUserDataChange(obj, user, uid, i);
                                }
                            }
                            userDao.setUserData(uid, user);
                            JSONObject add = new JSONObject();
                            add.put("id", GoodId);
                            add.put("count", 1);
                            JSONObject shop = user.getJSONObject("shop");
                            shop.getJSONObject("GP").getJSONObject("oneTime").getJSONArray("info").add(add);
                            modified.put("shop", shop);
                            user.getJSONObject("shop").getJSONObject("GP").getJSONObject("oneTime").getJSONArray("info").add(add);
                            userDao.setUserData(uid, user);
                            playerDataDelta.put("modified", modified);
                            result.put("playerDataDelta", playerDataDelta);
                            result.put("receiveItems", receiveItems);
                            ArknightsApplication.LOGGER.info("Response confirmOrder JSON: " + result);
                            return result;
                        }
                    }
                }
            }
        }
        return new JSONObject();
    }

    @PostMapping(
            value = "/getUnconfirmedOrderIdList",
            produces = "application/json;charset=UTF-8"
    )
    public Map<String, Object> getUnconfirmOrderId(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /pay/getUnconfirmedOrderIdList");
        ArknightsApplication.LOGGER.info("Received JSON: " + JsonBody.toJSONString());

        JSONObject result = new JSONObject(true); // true 保持字段顺序

        try {
            JSONArray orderIdArray = new JSONArray();
            if (orderIdDao.queryOrderIdBtSecret(secret) != null) {
                String orderId = orderIdDao.queryOrderIdBtSecret(secret);
                orderIdArray.add(orderId);
                orderIdDao.DelTable(orderId);
            }
            result.put("orderIdList", orderIdArray);

            // 4. 构建 pushMessage
            JSONArray pushMessageArray = buildPushMessage();
            result.put("pushMessage", pushMessageArray);

            // 5. 构建 playerDataDelta
            JSONObject playerDataDelta = buildPlayerDataDelta();
            result.put("playerDataDelta", playerDataDelta);

            ArknightsApplication.LOGGER.info("Response: " + result.toJSONString());

        } catch (Exception e) {
            ArknightsApplication.LOGGER.error("Error processing request", e);
            // 返回空数据
            result.put("orderIdList", new JSONArray());
            result.put("pushMessage", new JSONArray());
            result.put("playerDataDelta", buildEmptyPlayerDataDelta());
        }
        return result;
    }
    private JSONArray buildPushMessage() {
        JSONArray pushMessageArray = new JSONArray();

        JSONObject dataPayload = new JSONObject();
        dataPayload.put("content", "Welcome to Local Arknights!这是一个开源项目,如果你为它付费了,那你一定被骗了");
        dataPayload.put("loop", 3);
        dataPayload.put("majorVersion", "455");

        JSONObject payload = new JSONObject();
        payload.put("data", dataPayload.toJSONString());

        JSONObject pushItem = new JSONObject();
        pushItem.put("path", "flushAlerts");
        pushItem.put("payload", payload);

        pushMessageArray.add(pushItem);

        return pushMessageArray;
    }

    private JSONObject buildPlayerDataDelta() {
        JSONObject playerDataDelta = new JSONObject();
        playerDataDelta.put("modified", new JSONObject());
        playerDataDelta.put("deleted", new JSONObject());
        return playerDataDelta;
    }

    private JSONObject buildEmptyPlayerDataDelta() {
        JSONObject delta = new JSONObject();
        delta.put("modified", new JSONObject());
        delta.put("deleted", new JSONObject());
        return delta;
    }
}
