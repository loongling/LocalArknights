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

import static com.hypergryph.arknights.core.monthlycard.MonthlyCard.*;
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
    public JSONObject getOrderState(@RequestParam("orderId") String orderId, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /pay/order/v1/state");
        JSONObject result = new JSONObject();
        JSONObject data = new JSONObject();
        JSONObject product = orderIdDao.queryAllByOrderId(orderId);
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
        if (orderIdDao.checkTimes(orderId) < time) {
            result.put("status", 101);
            result.put("msg", "未支付");
            orderIdDao.updateTimes(orderId);
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
                JSONObject good = com.hypergryph.arknights.core.monthlycard.MonthlyCard
                        .findGood(product_id);
                if (good == null) {
                    ArknightsApplication.LOGGER.warn("[pay] confirmOrder: 找不到商品 {}", product_id);
                    return new JSONObject();
                }

                String[] gpPath = com.hypergryph.arknights.core.shop.ShopPurchase.gpShopPath(product_id);
                if (gpPath != null) {
                    String soldOut = com.hypergryph.arknights.core.shop.ShopPurchase
                            .checkAvailCount(good, user, gpPath, product_id, 1);
                    if (soldOut != null) {
                        ArknightsApplication.LOGGER.warn("[pay] confirmOrder: {}", soldOut);
                        orderIdDao.DelTable(orderId);
                        result.put("result", 1);
                        result.put("error", soldOut);
                        return result;
                    }
                }

                JSONArray items = com.hypergryph.arknights.core.shop.GPGoodGrant
                        .grant(user, good, uid, modified);

                userDao.setUserData(uid, user);

                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                receiveItems.put("items", items);
                result.put("receiveItems", receiveItems);
                result.put("result", 0);
                orderIdDao.DelTable(orderId);

                ArknightsApplication.LOGGER.info("[pay] uid={} 订单 {} 发放 {} 项，modified={}",
                        uid, orderId, items.size(), modified.toJSONString());
                return result;
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
