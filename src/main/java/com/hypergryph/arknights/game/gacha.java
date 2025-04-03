package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.file.IOTools;
import com.hypergryph.arknights.core.pojo.Account;
import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/gacha"})
public class gacha {
    public gacha() {
    }

    @PostMapping("/syncNormalGacha")
    public JSONObject syncNormalGacha(HttpServletResponse response,HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/syncNormalGacha");
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        if (!ArknightsApplication.enableServer) {
            return createErrorResponse(response, 400, "server is close");
        }

        Account account = validateAccount(secret);
        if (account == null) {
            return createErrorResponse(2, "无法查询到此账户");
        }

        if (account.getBan() == 1L) {
            return createErrorResponse(response, 403, "error");
        }

        JSONObject userSyncData = JSONObject.parseObject(account.getUser());
        JSONObject result = new JSONObject(true);
        JSONObject playerDataDelta = new JSONObject(true);
        JSONObject modified = new JSONObject(true);
        modified.put("recruit", userSyncData.getJSONObject("recruit"));
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    @PostMapping("/normalGacha")
    public JSONObject normalGacha(@RequestBody JSONObject jsonBody, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/normalGacha");
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        logRequest(request, "/gacha/normalGacha");
        if (!ArknightsApplication.enableServer) {
            return createErrorResponse(400, "server is close");
        }

        Account account = validateAccount(secret);
        if (account == null) {
            return createErrorResponse(2, "无法查询到此账户");
        }

        if (account.getBan() == 1L) {
            return createErrorResponse(403, "error");
        }

        String slotId = jsonBody.getString("slotId");
        JSONArray tagList = jsonBody.getJSONArray("tagList");
        JSONObject userSyncData = JSONObject.parseObject(account.getUser());

        // Update recruit data
        JSONObject recruitSlot = userSyncData.getJSONObject("recruit").getJSONObject("normal").getJSONObject("slots").getJSONObject(slotId);
        recruitSlot.put("state", 2);
        recruitSlot.put("selectTags", createSelectTags(tagList));

        // Update recruit license count
        userSyncData.getJSONObject("status").put("recruitLicense",
                userSyncData.getJSONObject("status").getIntValue("recruitLicense") - 1);

        // Save updated user data
        userDao.setUserData(account.getUid(), userSyncData);

        // Prepare response
        JSONObject result = new JSONObject(true);
        JSONObject playerDataDelta = new JSONObject(true);
        JSONObject modified = new JSONObject(true);
        modified.put("recruit", userSyncData.getJSONObject("recruit"));
        modified.put("status", userSyncData.getJSONObject("status"));
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", playerDataDelta);

        return result;
    }

    @PostMapping("/finishNormalGacha")
    public JSONObject finishNormalGacha(@RequestBody JSONObject jsonBody, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/finishNormalGacha");
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        logRequest(request, "/gacha/finishNormalGacha");
        if (!ArknightsApplication.enableServer) {
            return createErrorResponse(400, "server is close");
        }

        Account account = validateAccount(secret);
        if (account == null) {
            return createErrorResponse(2, "无法查询到此账户");
        }

        if (account.getBan() == 1L) {
            return createErrorResponse(403, "error");
        }

        String slotId = jsonBody.getString("slotId");
        JSONObject userSyncData = JSONObject.parseObject(account.getUser());

        // Perform gacha
        JSONObject gachaResult = performNormalGacha(userSyncData);

        // Update user data
        updateUserDataAfterGacha(userSyncData, gachaResult, slotId);
        userDao.setUserData(account.getUid(), userSyncData);

        // Prepare response
        JSONObject result = new JSONObject(true);
        JSONObject playerDataDelta = new JSONObject(true);
        JSONObject modified = new JSONObject(true);
        modified.put("recruit", userSyncData.getJSONObject("recruit"));
        modified.put("status", userSyncData.getJSONObject("status"));
        modified.put("troop", userSyncData.getJSONObject("troop"));
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        result.put("playerDataDelta", playerDataDelta);
        result.put("charGet", gachaResult);

        return result;
    }

    @PostMapping("/getPoolDetail")
    public JSONObject getPoolDetail(@RequestBody JSONObject jsonBody, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/getPoolDetail");
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        logRequest(request, "/gacha/getPoolDetail");
        if (!ArknightsApplication.enableServer) {
            return createErrorResponse(400, "server is close");
        }

        String poolId = jsonBody.getString("poolId");
        String poolPath = System.getProperty("user.dir") + "/data/gacha/" + poolId + ".json";
        File poolFile = new File(poolPath);

        if (!poolFile.exists()) {
            return createEmptyPoolDetail(poolId);
        }

        return IOTools.ReadJsonFile(poolPath);
    }

    @PostMapping("/advancedGacha")
    public JSONObject advancedGacha(@RequestBody JSONObject jsonBody, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/advancedGacha");
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        logRequest(request, "/gacha/advancedGacha");
        if (!ArknightsApplication.enableServer) {
            return createErrorResponse(400, "server is close");
        }

        String poolId = jsonBody.getString("poolId");
        int diamondCost = poolId.equals("BOOT_0_1_2") ? 380 : 600;
        return Gacha("gachaTicket", diamondCost, secret, jsonBody);
    }

    @PostMapping("/tenAdvancedGacha")
    public JSONObject tenAdvancedGacha(@RequestBody JSONObject jsonBody, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/tenAdvancedGacha");
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        logRequest(request, "/gacha/tenAdvancedGacha");
        if (!ArknightsApplication.enableServer) {
            return createErrorResponse(400, "server is close");
        }

        String poolId = jsonBody.getString("poolId");
        int diamondCost = poolId.equals("BOOT_0_1_2") ? 3800 : 6000;
        return Gacha("tenGachaTicket", diamondCost, secret, jsonBody);
    }

    // Helper methods...
    private Account validateAccount(String secret) {
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        return accounts.size() == 1 ? accounts.get(0) : null;
    }

    private JSONObject createErrorResponse(int statusCode, String message) {
        JSONObject result = new JSONObject(true);
        result.put("statusCode", statusCode);
        result.put("error", "Bad Request");
        result.put("message", message);
        return result;
    }

    private JSONObject createErrorResponse(HttpServletResponse response, int statusCode, String message) {
        response.setStatus(statusCode);
        return createErrorResponse(statusCode, message);
    }

    private void logRequest(HttpServletRequest request, String endpoint) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] " + endpoint);
    }

    private JSONArray createSelectTags(JSONArray tagList) {
        JSONArray selectTags = new JSONArray();
        for (int i = 0; i < tagList.size(); i++) {
            JSONObject tag = new JSONObject(true);
            tag.put("pick", 1);
            tag.put("tagId", tagList.getIntValue(i));
            selectTags.add(tag);
        }
        return selectTags;
    }

    private JSONObject performNormalGacha(JSONObject userSyncData) {
        JSONObject chars = userSyncData.getJSONObject("troop").getJSONObject("chars");
        JSONObject buildingChars = userSyncData.getJSONObject("building").getJSONObject("chars");
        JSONArray availCharInfo = ArknightsApplication.normalGachaData.getJSONObject("detailInfo").getJSONObject("availCharInfo").getJSONArray("perAvailList");
        JSONArray randomRankArray = new JSONArray();

        // Calculate probabilities
        for (int i = 0; i < availCharInfo.size(); i++) {
            int totalPercent = (int)(availCharInfo.getJSONObject(i).getFloat("totalPercent") * 100.0F);
            int rarityRank = availCharInfo.getJSONObject(i).getIntValue("rarityRank");
            JSONObject randomRankObject = new JSONObject(true);
            randomRankObject.put("rarityRank", rarityRank);
            randomRankObject.put("index", i);
            IntStream.range(0, totalPercent).forEach(n -> randomRankArray.add(randomRankObject));
        }

        // Randomize and select character
        Collections.shuffle(randomRankArray);
        JSONObject randomRank = randomRankArray.getJSONObject(new Random().nextInt(randomRankArray.size()));
        JSONArray randomCharArray = availCharInfo.getJSONObject(randomRank.getIntValue("index")).getJSONArray("charIdList");
        Collections.shuffle(randomCharArray);
        String randomCharId = randomCharArray.getString(new Random().nextInt(randomCharArray.size()));

        // Check if character is new or repeat
        int repeatCharId = 0;
        for (int i = 0; i < chars.size(); i++) {
            if (chars.getJSONObject(String.valueOf(i + 1)).getString("charId").equals(randomCharId)) {
                repeatCharId = i + 1;
                break;
            }
        }

        JSONArray itemGet = new JSONArray();
        int isNew = 0;
        int charinstId = repeatCharId;
        JSONObject charGet = new JSONObject(true);

        if (repeatCharId == 0) {
            // New character
            charGet = createNewCharacter(randomCharId, userSyncData, chars, buildingChars);
            charinstId = charGet.getIntValue("instId");
            isNew = 1;

            JSONObject hggShardItem = new JSONObject(true);
            hggShardItem.put("type", "HGG_SHD");
            hggShardItem.put("id", "4004");
            hggShardItem.put("count", 1);
            itemGet.add(hggShardItem);
            userSyncData.getJSONObject("status").put("hggShard", userSyncData.getJSONObject("status").getIntValue("hggShard") + 1);
        } else {
            // Repeat character
            charGet = chars.getJSONObject(String.valueOf(repeatCharId));
            int potentialRank = charGet.getIntValue("potentialRank");
            int rarity = ArknightsApplication.characterJson.getJSONObject(randomCharId).getIntValue("rarity");

            JSONObject shardItem = createShardItem(rarity, potentialRank);
            itemGet.add(shardItem);

            JSONObject potentialItem = new JSONObject(true);
            potentialItem.put("type", "MATERIAL");
            potentialItem.put("id", "p_" + randomCharId);
            potentialItem.put("count", 1);
            itemGet.add(potentialItem);

            updateUserInventory(userSyncData, shardItem, randomCharId);
        }

        JSONObject result = new JSONObject(true);
        result.put("itemGet", itemGet);
        result.put("charId", randomCharId);
        result.put("charInstId", charinstId);
        result.put("isNew", isNew);
        return result;
    }

    private void updateUserDataAfterGacha(JSONObject userSyncData, JSONObject gachaResult, String slotId) {
        // Update character data
        userSyncData.getJSONObject("troop").put("chars", userSyncData.getJSONObject("troop").getJSONObject("chars"));

        // Reset recruit slot
        JSONObject recruitSlot = userSyncData.getJSONObject("recruit").getJSONObject("normal").getJSONObject("slots").getJSONObject(slotId);
        recruitSlot.put("state", 1);
        recruitSlot.put("selectTags", new JSONArray());
    }

    private JSONObject createNewCharacter(String charId, JSONObject userSyncData, JSONObject chars, JSONObject buildingChars) {
        JSONObject charGet = new JSONObject(true);
        JSONArray skillsArray = ArknightsApplication.characterJson.getJSONObject(charId).getJSONArray("skills");
        JSONArray skills = new JSONArray();

        for (int i = 0; i < skillsArray.size(); i++) {
            JSONObject newSkill = new JSONObject(true);
            newSkill.put("skillId", skillsArray.getJSONObject(i).getString("skillId"));
            newSkill.put("state", 0);
            newSkill.put("specializeLevel", 0);
            newSkill.put("completeUpgradeTime", -1);
            newSkill.put("unlock", skillsArray.getJSONObject(i).getJSONObject("unlockCond").getIntValue("phase") == 0 ? 1 : 0);
            skills.add(newSkill);
        }

        int instId = chars.size() + 1;
        charGet.put("instId", instId);
        charGet.put("charId", charId);
        charGet.put("favorPoint", 0);
        charGet.put("potentialRank", 0);
        charGet.put("mainSkillLvl", 1);
        charGet.put("skin", charId + "#1");
        charGet.put("level", 1);
        charGet.put("exp", 0);
        charGet.put("evolvePhase", 0);
        charGet.put("gainTime", new Date().getTime() / 1000L);
        charGet.put("skills", skills);
        charGet.put("voiceLan", ArknightsApplication.charwordTable.getJSONObject("charDefaultTypeDict").getString(charId));
        charGet.put("defaultSkillIndex", skills.isEmpty() ? -1 : 0);

        // Handle equipment
        String itemType = charId.substring(charId.indexOf("_") + 1);
        String itemId = itemType.substring(itemType.indexOf("_") + 1);
        if (ArknightsApplication.uniequipTable.containsKey("uniequip_001_" + itemId)) {
            JSONObject equip = new JSONObject(true);
            JSONObject uniequip1 = new JSONObject(true);
            uniequip1.put("hide", 0);
            uniequip1.put("locked", 0);
            uniequip1.put("level", 1);
            JSONObject uniequip2 = new JSONObject(true);
            uniequip2.put("hide", 0);
            uniequip2.put("locked", 0);
            uniequip2.put("level", 1);
            equip.put("uniequip_001_" + itemId, uniequip1);
            equip.put("uniequip_002_" + itemId, uniequip2);
            charGet.put("equip", equip);
            charGet.put("currentEquip", "uniequip_001_" + itemId);
        } else {
            charGet.put("equip", new JSONObject(true));
            charGet.put("currentEquip", null);
        }

        chars.put(String.valueOf(instId), charGet);

        // Update char group
        JSONObject charGroup = new JSONObject(true);
        charGroup.put("favorPoint", 0);
        userSyncData.getJSONObject("troop").getJSONObject("charGroup").put(charId, charGroup);

        // Update building chars
        JSONObject buildingChar = new JSONObject(true);
        buildingChar.put("charId", charId);
        buildingChar.put("lastApAddTime", new Date().getTime() / 1000L);
        buildingChar.put("ap", 8640000);
        buildingChar.put("roomSlotId", "");
        buildingChar.put("index", -1);
        buildingChar.put("changeScale", 0);
        JSONObject bubble = new JSONObject(true);
        JSONObject normal = new JSONObject(true);
        normal.put("add", -1);
        normal.put("ts", 0);
        bubble.put("normal", normal);
        JSONObject assist = new JSONObject(true);
        assist.put("add", -1);
        assist.put("ts", -1);
        bubble.put("assist", assist);
        buildingChar.put("bubble", bubble);
        buildingChar.put("workTime", 0);
        buildingChars.put(String.valueOf(instId), buildingChar);

        return charGet;
    }

    private JSONObject createShardItem(int rarity, int potentialRank) {
        JSONObject shardItem = new JSONObject(true);
        String itemName, itemType, itemId;
        int itemCount;

        if (rarity <= 2) {
            itemName = "lggShard";
            itemType = "LGG_SHD";
            itemId = "4005";
            itemCount = rarity == 2 ? 5 : 1;
        } else if (rarity == 3) {
            itemName = "lggShard";
            itemType = "LGG_SHD";
            itemId = "4005";
            itemCount = 30;
        } else {
            itemName = "hggShard";
            itemType = "HGG_SHD";
            itemId = "4004";
            if (rarity == 5) {
                itemCount = potentialRank != 5 ? 10 : 15;
            } else {
                itemCount = potentialRank != 5 ? 5 : 8;
            }
        }

        shardItem.put("type", itemType);
        shardItem.put("id", itemId);
        shardItem.put("count", itemCount);
        return shardItem;
    }

    private void updateUserInventory(JSONObject userSyncData, JSONObject shardItem, String charId) {
        String itemName = shardItem.getString("type").equals("HGG_SHD") ? "hggShard" : "lggShard";
        int itemCount = shardItem.getIntValue("count");
        userSyncData.getJSONObject("status").put(itemName, userSyncData.getJSONObject("status").getIntValue(itemName) + itemCount);
        userSyncData.getJSONObject("inventory").put("p_" + charId, userSyncData.getJSONObject("inventory").getIntValue("p_" + charId) + 1);
    }

    private JSONObject createEmptyPoolDetail(String poolId) {
        JSONObject result = new JSONObject(true);
        JSONObject detailInfo = new JSONObject();
        // ... (rest of the empty pool detail creation)
        return result;
    }

    public JSONObject Gacha(String type, int useDiamondShard, String secret, JSONObject JsonBody) {
        // 1. 验证账户
        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            JSONObject result = new JSONObject(true);
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }

        // 2. 获取用户数据
        Long uid = Accounts.get(0).getUid();
        JSONObject UserSyncData = JSONObject.parseObject(Accounts.get(0).getUser());
        String poolId = JsonBody.getString("poolId");
        int useTkt = JsonBody.getIntValue("useTkt");

        // 3. 验证卡池
        String poolPath = System.getProperty("user.dir") + "/data/gacha/" + poolId + ".json";
        if (!(new File(poolPath)).exists()) {
            JSONObject result = new JSONObject(true);
            result.put("result", 1);
            result.put("errMsg", "该当前干员寻访无法使用，详情请关注官方公告");
            return result;
        }

        // 4. 读取卡池数据
        JSONObject poolJson = IOTools.ReadJsonFile(poolPath);
        JSONArray gachaResultList = new JSONArray();
        JSONArray newChars = new JSONArray();
        JSONObject charGet = new JSONObject(true);
        JSONObject troop = new JSONObject(true);
        JSONObject chars = UserSyncData.getJSONObject("troop").getJSONObject("chars");

        // 5. 计算抽卡次数
        int pullCount = JsonBody.getString("poolId").equals("BOOT_0_1_1") ?
                useDiamondShard / 380 : useDiamondShard / 600;

        // 6. 检查资源是否足够
        if (useTkt != 1 && useTkt != 2) {
            if (UserSyncData.getJSONObject("status").getIntValue("diamondShard") < useDiamondShard) {
                JSONObject result = new JSONObject(true);
                result.put("result", 3);
                result.put("errMsg", "剩余合成玉不足");
                return result;
            }
        } else if (UserSyncData.getJSONObject("status").getIntValue(type) <= 0) {
            JSONObject result = new JSONObject(true);
            result.put("result", 2);
            result.put("errMsg", "剩余寻访凭证不足");
            return result;
        }

        // 7. 执行抽卡
        for (int count = 0; count < pullCount; count++) {
            // 处理新手池
            Boolean Minimum = false;
            String poolObjectName;
            JSONObject Pool;

            if (JsonBody.getString("poolId").equals("BOOT_0_1_2")) {
                poolObjectName = "newbee";
                Pool = UserSyncData.getJSONObject("gacha").getJSONObject(poolObjectName);
                int cnt = Pool.getIntValue("cnt") - 1;
                UserSyncData.getJSONObject("gacha").getJSONObject(poolObjectName).put("cnt", cnt);
                if (cnt == 0) {
                    UserSyncData.getJSONObject("gacha").getJSONObject(poolObjectName).put("openFlag", 0);
                }
            } else {
                // 处理普通池
                poolObjectName = "normal";
                if (!UserSyncData.getJSONObject("gacha").getJSONObject(poolObjectName).containsKey(poolId)) {
                    JSONObject PoolJson = new JSONObject(true);
                    PoolJson.put("cnt", 0);
                    PoolJson.put("maxCnt", 10);
                    PoolJson.put("rarity", 4);
                    PoolJson.put("avail", true);
                    UserSyncData.getJSONObject("gacha").getJSONObject(poolObjectName).put(poolId, PoolJson);
                }

                Pool = UserSyncData.getJSONObject("gacha").getJSONObject(poolObjectName).getJSONObject(poolId);
                int cnt = Pool.getIntValue("cnt") + 1;
                UserSyncData.getJSONObject("gacha").getJSONObject(poolObjectName).getJSONObject(poolId).put("cnt", cnt);

                if (cnt == 10 && Pool.getBoolean("avail")) {
                    UserSyncData.getJSONObject("gacha").getJSONObject(poolObjectName).getJSONObject(poolId).put("avail", false);
                    Minimum = true;
                }
            }

            UserSyncData.getJSONObject("status").put("gachaCount",
                    UserSyncData.getJSONObject("status").getIntValue("gachaCount") + 1);

            // 8. 抽取干员
            JSONArray availCharInfo = poolJson.getJSONObject("detailInfo").getJSONObject("availCharInfo").getJSONArray("perAvailList");
            JSONArray upCharInfo = poolJson.getJSONObject("detailInfo").getJSONObject("upCharInfo").getJSONArray("perCharList");
            JSONArray randomRankArray = new JSONArray();

            // 计算概率
            for (int i = 0; i < availCharInfo.size(); i++) {
                int totalPercent = (int)(availCharInfo.getJSONObject(i).getFloat("totalPercent") * 200.0F);
                int rarityRank = availCharInfo.getJSONObject(i).getIntValue("rarityRank");

                if (rarityRank == 5) {
                    totalPercent += (UserSyncData.getJSONObject("status").getIntValue("gachaCount") + 50) / 50 * 2;
                }

                if (!Minimum || rarityRank >= Pool.getIntValue("rarity")) {
                    JSONObject randomRankObject = new JSONObject(true);
                    randomRankObject.put("rarityRank", rarityRank);
                    randomRankObject.put("index", i);
                    IntStream.range(0, totalPercent).forEach(n -> randomRankArray.add(randomRankObject));
                }
            }

            // 随机抽取
            Collections.shuffle(randomRankArray);
            JSONObject randomRank = randomRankArray.getJSONObject(new Random().nextInt(randomRankArray.size()));

            // 更新保底
            if (!JsonBody.getString("poolId").equals("BOOT_0_1_1") &&
                    randomRank.getIntValue("rarityRank") >= Pool.getIntValue("rarity")) {
                UserSyncData.getJSONObject("gacha").getJSONObject(poolObjectName).getJSONObject(poolId)
                        .put("avail", false);
            }

            if (randomRank.getIntValue("rarityRank") == 5) {
                UserSyncData.getJSONObject("status").put("gachaCount", 0);
            }

            // 9. 处理UP池
            JSONArray randomCharArray = availCharInfo.getJSONObject(randomRank.getIntValue("index"))
                    .getJSONArray("charIdList");

            for (int i = 0; i < upCharInfo.size(); i++) {
                if (upCharInfo.getJSONObject(i).getIntValue("rarityRank") == randomRank.getIntValue("rarityRank")) {
                    int upRate = (int)(upCharInfo.getJSONObject(i).getFloat("percent") * 100.0F) - 15;
                    JSONArray upCharIdList = upCharInfo.getJSONObject(i).getJSONArray("charIdList");

                    for (int n = 0; n < upCharIdList.size(); n++) {
                        String charId = upCharIdList.getString(n);
                        IntStream.range(0, upRate).forEach(p -> randomCharArray.add(charId));
                    }
                }
            }

            // 10. 随机选择干员
            Collections.shuffle(randomCharArray);
            String randomCharId = randomCharArray.getString(new Random().nextInt(randomCharArray.size()));

            // 11. 处理重复干员
            int repeatCharId = 0;
            for (int i = 0; i < UserSyncData.getJSONObject("troop").getJSONObject("chars").size(); i++) {
                if (UserSyncData.getJSONObject("troop").getJSONObject("chars")
                        .getJSONObject(String.valueOf(i + 1)).getString("charId").equals(randomCharId)) {
                    repeatCharId = i + 1;
                    break;
                }
            }

            // 12. 添加到结果中
            if (repeatCharId != 0) {
                // 处理重复干员逻辑
                handleRepeatCharacter(repeatCharId, randomCharId, randomRank, UserSyncData,
                        gachaResultList, charGet, chars, troop);
            } else {
                // 处理新干员逻辑
                handleNewCharacter(randomCharId, UserSyncData, gachaResultList, newChars,
                        charGet, chars, troop);
            }
        }

        // 13. 扣除资源
        if (useTkt != 1 && useTkt != 2) {
            UserSyncData.getJSONObject("status").put("diamondShard",
                    UserSyncData.getJSONObject("status").getIntValue("diamondShard") - useDiamondShard);
        } else {
            UserSyncData.getJSONObject("status").put(type,
                    UserSyncData.getJSONObject("status").getIntValue(type) - 1);
        }

        // 14. 更新数据
        UserSyncData.getJSONObject("troop").put("chars", chars);
        JSONObject playerDataDelta = new JSONObject(true);
        JSONObject modified = new JSONObject(true);
        modified.put("troop", UserSyncData.getJSONObject("troop"));
        modified.put("consumable", UserSyncData.getJSONObject("consumable"));
        modified.put("status", UserSyncData.getJSONObject("status"));
        modified.put("inventory", UserSyncData.getJSONObject("inventory"));
        modified.put("gacha", UserSyncData.getJSONObject("gacha"));

        playerDataDelta.put("deleted", new JSONObject(true));
        playerDataDelta.put("modified", modified);

        // 15. 保存数据
        userDao.setUserData(uid, UserSyncData);

        // 16. 返回结果
        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("charGet", charGet);
        result.put("gachaResultList", gachaResultList);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    private void handleRepeatCharacter(int repeatCharId, String randomCharId, JSONObject randomRank,
                                       JSONObject UserSyncData, JSONArray gachaResultList,
                                       JSONObject charGet, JSONObject chars, JSONObject troop) {
        JSONObject get_char = new JSONObject(true);
        get_char.put("charInstId", repeatCharId);
        get_char.put("charId", randomCharId);
        get_char.put("isNew", 0);
        JSONObject char_data = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(repeatCharId));
        int potentialRank = char_data.getIntValue("potentialRank");
        int rarity = randomRank.getIntValue("rarityRank");

        String itemName = null;
        String itemType = null;
        String itemId = null;
        int itemCount = 0;

        // 根据稀有度设置道具
        if (rarity <= 2) {
            itemName = "lggShard";
            itemType = "LGG_SHD";
            itemId = "4005";
            itemCount = rarity == 2 ? 5 : 1;
        } else if (rarity == 3) {
            itemName = "lggShard";
            itemType = "LGG_SHD";
            itemId = "4005";
            itemCount = 30;
        } else if (rarity >= 4) {
            itemName = "hggShard";
            itemType = "HGG_SHD";
            itemId = "4004";
            itemCount = rarity == 5 ? (potentialRank != 5 ? 10 : 15) : (potentialRank != 5 ? 5 : 8);
        }

        JSONArray itemGet = new JSONArray();
        JSONObject new_itemGet_1 = new JSONObject(true);
        new_itemGet_1.put("type", itemType);
        new_itemGet_1.put("id", itemId);
        new_itemGet_1.put("count", itemCount);
        itemGet.add(new_itemGet_1);

        UserSyncData.getJSONObject("status").put(itemName, UserSyncData.getJSONObject("status").getIntValue(itemName) + itemCount);

        JSONObject new_itemGet_3 = new JSONObject(true);
        new_itemGet_3.put("type", "MATERIAL");
        new_itemGet_3.put("id", "p_" + randomCharId);
        new_itemGet_3.put("count", 1);
        itemGet.add(new_itemGet_3);

        get_char.put("itemGet", itemGet);
        UserSyncData.getJSONObject("inventory").put("p_" + randomCharId, UserSyncData.getJSONObject("inventory").getIntValue("p_" + randomCharId) + 1);

        gachaResultList.add(get_char);
        charGet = get_char;

        JSONObject charinstId = new JSONObject(true);
        charinstId.put(String.valueOf(repeatCharId), UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(repeatCharId)));
        chars.put(String.valueOf(repeatCharId), UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(repeatCharId)));
        troop.put("chars", charinstId);
    }

    private void handleNewCharacter(String randomCharId, JSONObject UserSyncData,
                                    JSONArray gachaResultList, JSONArray newChars,
                                    JSONObject charGet, JSONObject chars, JSONObject troop) {
        JSONObject get_char = new JSONObject(true);
        JSONObject char_data = new JSONObject(true);
        JSONArray skilsArray = ArknightsApplication.characterJson.getJSONObject(randomCharId).getJSONArray("skills");
        JSONArray skils = new JSONArray();

        for (int i = 0; i < skilsArray.size(); i++) {
            JSONObject new_skils = new JSONObject(true);
            new_skils.put("skillId", skilsArray.getJSONObject(i).getString("skillId"));
            new_skils.put("state", 0);
            new_skils.put("specializeLevel", 0);
            new_skils.put("completeUpgradeTime", -1);
            new_skils.put("unlock", skilsArray.getJSONObject(i).getJSONObject("unlockCond").getIntValue("phase") == 0 ? 1 : 0);
            skils.add(new_skils);
        }

        int instId = UserSyncData.getJSONObject("troop").getJSONObject("chars").size() + 1;
        char_data.put("instId", instId);
        char_data.put("charId", randomCharId);
        char_data.put("favorPoint", 0);
        char_data.put("potentialRank", 0);
        char_data.put("mainSkillLvl", 1);
        char_data.put("skin", randomCharId + "#1");
        char_data.put("level", 1);
        char_data.put("exp", 0);
        char_data.put("evolvePhase", 0);
        char_data.put("gainTime", new Date().getTime() / 1000L);
        char_data.put("skills", skils);
        char_data.put("voiceLan", ArknightsApplication.charwordTable.getJSONObject("charDefaultTypeDict").getString(randomCharId));
        char_data.put("defaultSkillIndex", skils.isEmpty() ? -1 : 0);

        String itemType = randomCharId.substring(randomCharId.indexOf("_") + 1);
        String itemId = itemType.substring(itemType.indexOf("_") + 1);

        if (ArknightsApplication.uniequipTable.containsKey("uniequip_001_" + itemId)) {
            JSONObject charGroup = new JSONObject(true);
            JSONObject buildingChar = new JSONObject(true);
            buildingChar.put("hide", 0);
            buildingChar.put("locked", 0);
            buildingChar.put("level", 1);
            JSONObject new_itemGet_1 = new JSONObject(true);
            new_itemGet_1.put("hide", 0);
            new_itemGet_1.put("locked", 0);
            new_itemGet_1.put("level", 1);
            charGroup.put("uniequip_001_" + itemId, buildingChar);
            charGroup.put("uniequip_002_" + itemId, new_itemGet_1);
            char_data.put("equip", charGroup);
            char_data.put("currentEquip", "uniequip_001_" + itemId);
        } else {
            char_data.put("currentEquip", null);
        }

        UserSyncData.getJSONObject("troop").getJSONObject("chars").put(String.valueOf(instId), char_data);

        JSONObject charGroup = new JSONObject(true);
        charGroup.put("favorPoint", 0);
        UserSyncData.getJSONObject("troop").getJSONObject("charGroup").put(randomCharId, charGroup);

        JSONObject buildingChar = new JSONObject(true);
        buildingChar.put("charId", randomCharId);
        buildingChar.put("lastApAddTime", new Date().getTime() / 1000L);
        buildingChar.put("ap", 8640000);
        buildingChar.put("roomSlotId", "");
        buildingChar.put("index", -1);
        buildingChar.put("changeScale", 0);
        JSONObject new_itemGet_1 = new JSONObject(true);
        JSONObject new_itemGet_3 = new JSONObject(true);
        new_itemGet_3.put("add", -1);
        new_itemGet_3.put("ts", 0);
        new_itemGet_1.put("normal", new_itemGet_3);
        JSONObject charinstId = new JSONObject(true);
        charinstId.put("add", -1);
        charinstId.put("ts", -1);
        new_itemGet_1.put("assist", charinstId);
        buildingChar.put("bubble", new_itemGet_1);
        buildingChar.put("workTime", 0);
        UserSyncData.getJSONObject("building").getJSONObject("chars").put(String.valueOf(instId), buildingChar);

        get_char.put("charInstId", instId);
        get_char.put("charId", randomCharId);
        get_char.put("isNew", 1);
        JSONArray itemGet = new JSONArray();
        JSONObject new_itemGet = new JSONObject(true);
        new_itemGet.put("type", "HGG_SHD");
        new_itemGet.put("id", "4004");
        new_itemGet.put("count", 1);
        itemGet.add(new_itemGet);
        UserSyncData.getJSONObject("status").put("hggShard", UserSyncData.getJSONObject("status").getIntValue("hggShard") + 1);
        get_char.put("itemGet", itemGet);
        UserSyncData.getJSONObject("inventory").put("p_" + randomCharId, 0);

        gachaResultList.add(get_char);
        newChars.add(get_char);
        charGet = get_char;

        JSONObject charinstIdObj = new JSONObject(true);
        charinstIdObj.put(String.valueOf(instId), char_data);
        chars.put(String.valueOf(instId), char_data);
        troop.put("chars", charinstIdObj);
    }
}
