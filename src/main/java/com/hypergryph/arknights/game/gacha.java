package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.file.IOTools;
import com.hypergryph.arknights.core.pojo.Account;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gacha")
public class gacha {
    private final ArknightsApplication arknightsApplication;

    public gacha(ArknightsApplication arknightsApplication) {
        this.arknightsApplication = arknightsApplication;
    }

    // 配置文件路径


    @PostMapping("/syncNormalGacha")
    public JSONObject syncNormalGacha(HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/syncNormalGacha");

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            return createErrorResponse(400, "服务器维护中");
        }

        String secret = ArknightsApplication.getSecretByIP(clientIp);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            return createErrorResponse(2, "账户验证失败");
        }

        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            return createErrorResponse(403, "账号已被封禁");
        }

        JSONObject result = new JSONObject();
        JSONObject playerDataDelta = new JSONObject();
        JSONObject modified = new JSONObject();
        modified.put("recruit", JSONObject.parseObject(account.getUser()).getJSONObject("recruit"));
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject());
        result.put("playerDataDelta", playerDataDelta);

        return result;
    }

    @PostMapping("/normalGacha")
    public JSONObject normalGacha(@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/normalGacha");

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            return createErrorResponse(400, "服务器维护中");
        }

        String secret = ArknightsApplication.getSecretByIP(clientIp);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            return createErrorResponse(2, "账户验证失败");
        }

        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            return createErrorResponse(403, "账号已被封禁");
        }

        // 获取请求参数
        int slotId = jsonBody.getIntValue("slotId");
        JSONArray tagList = jsonBody.getJSONArray("tagList");
        int duration = jsonBody.getIntValue("duration");

        // 读取用户数据
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject recruitSlot = userData.getJSONObject("recruit")
                .getJSONObject("normal")
                .getJSONObject("slots")
                .getJSONObject(String.valueOf(slotId));

        // 检查招募许可
        int recruitLicense = userData.getJSONObject("status").getIntValue("recruitLicense");
        if (recruitLicense <= 0) {
            response.setStatus(400);
            return createErrorResponse(400, "招募许可不足");
        }

        // 更新招募信息
        long currentTime = System.currentTimeMillis() / 1000;
        recruitSlot.put("state", 2);
        recruitSlot.put("selectTags", createSelectTags(tagList));
        recruitSlot.put("startTs", currentTime);
        recruitSlot.put("durationInSec", duration);
        recruitSlot.put("maxFinishTs", currentTime + duration);
        recruitSlot.put("realFinishTs", currentTime + duration);

        // 更新招募许可数量
        userData.getJSONObject("status").put("recruitLicense", recruitLicense - 1);

        // 保存用户数据
        userDao.setUserData(account.getUid(), userData);

        // 构建响应
        JSONObject result = new JSONObject();
        JSONObject playerDataDelta = new JSONObject();
        JSONObject modified = new JSONObject();
        modified.put("recruit", userData.getJSONObject("recruit"));
        modified.put("status", userData.getJSONObject("status"));
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject());
        result.put("playerDataDelta", playerDataDelta);

        return result;
    }

    @PostMapping("/finishNormalGacha")
    public JSONObject finishNormalGacha(@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/finishNormalGacha");

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            return createErrorResponse(400, "服务器维护中");
        }

        String secret = ArknightsApplication.getSecretByIP(clientIp);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            return createErrorResponse(2, "账户验证失败");
        }

        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            return createErrorResponse(403, "账号已被封禁");
        }

        int slotId = jsonBody.getIntValue("slotId");
        JSONObject userData = JSONObject.parseObject(account.getUser());

        // 执行公开招募
        JSONObject gachaResult = performNormalGacha(userData);

        // 更新用户数据
        updateUserAfterNormalGacha(userData, gachaResult, slotId);
        userDao.setUserData(account.getUid(), userData);

        // 构建响应
        JSONObject result = new JSONObject();
        JSONObject playerDataDelta = new JSONObject();
        JSONObject modified = new JSONObject();
        modified.put("recruit", userData.getJSONObject("recruit"));
        modified.put("status", userData.getJSONObject("status"));
        modified.put("troop", userData.getJSONObject("troop"));
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject());
        result.put("playerDataDelta", playerDataDelta);
        result.put("charGet", gachaResult);

        return result;
    }

    @PostMapping("/getPoolDetail")
    public JSONObject getPoolDetail(@RequestBody JSONObject jsonBody, HttpServletResponse response) {
        String poolId = jsonBody.getString("poolId");
        String poolPath = "data/gacha/" + poolId + ".json";


        JSONObject poolData = IOTools.ReadJsonFile(poolPath);
        if (poolData == null) {
            response.setStatus(500);
            return createErrorResponse(500, "卡池数据读取失败");
        }

        return poolData;
    }

    @PostMapping("/advancedGacha")
    public JSONObject advancedGacha(@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/advancedGacha");

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            return createErrorResponse(400, "服务器维护中");
        }

        String secret = ArknightsApplication.getSecretByIP(clientIp);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            return createErrorResponse(2, "账户验证失败");
        }

        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            return createErrorResponse(403, "账号已被封禁");
        }

        String poolId = jsonBody.getString("poolId");
        int useTkt = jsonBody.getIntValue("useTkt");
        int diamondCost = poolId.startsWith("BOOT") ? 380 : 600;
        JSONObject userdata = JSON.parseObject(account.getUser());
        JSONObject gacha = userdata.getJSONObject("gacha");
        Long uid = account.getUid();
        if (poolId.startsWith("BOOT")){
            int cnt = gacha.getJSONObject("newbee").getIntValue("cnt");
            gacha.getJSONObject("newbee").put("cnt", cnt -1);
            userDao.setUserData(uid, userdata);
        }
        else{
            JSONObject normal = gacha.getJSONObject("normal");
            int cnt = normal.getJSONObject(poolId).getIntValue("cnt");
            normal.getJSONObject(poolId).put("cnt", cnt - 1);
            userDao.setUserData(uid, userdata);
        }

        return performAdvancedGacha(account, poolId, "gachaTicket", diamondCost, 1, useTkt, response);
    }

    @PostMapping("/tenAdvancedGacha")
    public JSONObject tenAdvancedGacha(@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /gacha/tenAdvancedGacha");
        ArknightsApplication.LOGGER.info("TAG:" + jsonBody);

        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            return createErrorResponse(400, "服务器维护中");
        }

        String secret = ArknightsApplication.getSecretByIP(clientIp);
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            return createErrorResponse(2, "账户验证失败");
        }

        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            return createErrorResponse(403, "账号已被封禁");
        }

        String poolId = jsonBody.getString("poolId");
        int useTkt = jsonBody.getIntValue("useTkt");
        int diamondCost = poolId.startsWith("BOOT") ? 3800 : 6000;

        return performAdvancedGacha(account, poolId, "tenGachaTicket", diamondCost, 10, useTkt, response);
    }

    // ==================== 私有方法 ====================

    private JSONObject createErrorResponse(int code, String message) {
        JSONObject result = new JSONObject();
        result.put("result", code);
        result.put("error", message);
        return result;
    }

    private JSONArray createSelectTags(JSONArray tagList) {
        JSONArray selectTags = new JSONArray();
        for (int i = 0; i < tagList.size(); i++) {
            JSONObject tag = new JSONObject();
            tag.put("pick", 1);
            tag.put("tagId", tagList.getString(i));
            selectTags.add(tag);
        }
        return selectTags;
    }

    private JSONObject performNormalGacha(JSONObject userData) {
        JSONObject chars = userData.getJSONObject("troop").getJSONObject("chars");
        JSONObject buildingChars = userData.getJSONObject("troop").getJSONObject("building").getJSONObject("chars");
        JSONArray availCharInfo = ArknightsApplication.normalGachaData.getJSONObject("detailInfo")
                .getJSONObject("availCharInfo")
                .getJSONArray("perAvailList");

        // 构建概率数组
        List<JSONObject> randomRankList = new ArrayList<>();
        for (int i = 0; i < availCharInfo.size(); i++) {
            JSONObject charInfo = availCharInfo.getJSONObject(i);
            int totalPercent = (int)(charInfo.getDoubleValue("totalPercent") * 100);
            int rarityRank = charInfo.getIntValue("rarityRank");

            JSONObject rankObj = new JSONObject();
            rankObj.put("rarityRank", rarityRank);
            rankObj.put("index", i);

            for (int j = 0; j < totalPercent; j++) {
                randomRankList.add(rankObj);
            }
        }

        // 随机选择
        Collections.shuffle(randomRankList);
        JSONObject selectedRank = randomRankList.get(new Random().nextInt(randomRankList.size()));
        JSONArray charIdList = availCharInfo.getJSONObject(selectedRank.getIntValue("index"))
                .getJSONArray("charIdList");
        Collections.shuffle(charIdList);
        String charId = charIdList.getString(new Random().nextInt(charIdList.size()));

        // 检查是否已有该角色
        int charInstId = 0;
        for (String key : chars.keySet()) {
            if (chars.getJSONObject(key).getString("charId").equals(charId)) {
                charInstId = Integer.parseInt(key);
                break;
            }
        }

        JSONObject result = new JSONObject();
        JSONArray itemGet = new JSONArray();
        boolean isNew = charInstId == 0;

        if (isNew) {
            // 新角色处理
            Pattern pattern = Pattern.compile("char_(\\d+)_");
            Matcher matcher = pattern.matcher(charId);
            ArknightsApplication.LOGGER.info("rewardSId:" + charId);

            int CharId = 0;
            if (matcher.find()) {
                CharId = Integer.parseInt(matcher.group(1));
                ArknightsApplication.LOGGER.info("提取到的数字是: " + CharId);
            } else {
                ArknightsApplication.LOGGER.info("未匹配到数字");
            }
            charInstId = CharId;
            JSONObject newChar = createNewCharacter(charId, charInstId);
            chars.put(String.valueOf(charInstId), newChar);

            // 添加基建数据
            JSONObject buildingChar = createBuildingCharacter(charId);
            buildingChars.put(String.valueOf(charInstId), buildingChar);

            // 添加获得物品
            JSONObject hggShard = new JSONObject();
            hggShard.put("type", "HGG_SHD");
            hggShard.put("id", "4004");
            hggShard.put("count", 1);
            itemGet.add(hggShard);

            // 更新库存
            userData.getJSONObject("status").put("hggShard",
                    userData.getJSONObject("status").getIntValue("hggShard") + 1);

            // 更新角色组
            JSONObject charGroup = new JSONObject();
            charGroup.put("favorPoint", 0);
            userData.getJSONObject("troop").getJSONObject("charGroup").put(charId, charGroup);
        } else {
            // 重复角色处理
            JSONObject existingChar = chars.getJSONObject(String.valueOf(charInstId));
            int rarity = selectedRank.getIntValue("rarityRank");
            int potentialRank = existingChar.getIntValue("potentialRank");

            // 根据稀有度添加物品
            JSONObject shardItem = new JSONObject();
            if (rarity <= 3) {
                shardItem.put("type", "LGG_SHD");
                shardItem.put("id", "4005");
                shardItem.put("count", rarity == 3 ? 30 : (rarity == 2 ? 5 : 1));
                userData.getJSONObject("status").put("lggShard",
                        userData.getJSONObject("status").getIntValue("lggShard") +
                                shardItem.getIntValue("count"));
            } else {
                shardItem.put("type", "HGG_SHD");
                shardItem.put("id", "4004");
                shardItem.put("count", rarity == 5 ?
                        (potentialRank < 5 ? 10 : 15) :
                        (potentialRank < 5 ? 5 : 8));
                userData.getJSONObject("status").put("hggShard",
                        userData.getJSONObject("status").getIntValue("hggShard") +
                                shardItem.getIntValue("count"));
            }
            itemGet.add(shardItem);

            // 添加潜能
            JSONObject potentialItem = new JSONObject();
            potentialItem.put("type", "MATERIAL");
            potentialItem.put("id", "p_" + charId);
            potentialItem.put("count", 1);
            itemGet.add(potentialItem);
            userData.getJSONObject("inventory").put("p_" + charId,
                    userData.getJSONObject("inventory").getIntValue("p_" + charId) + 1);
        }

        result.put("charId", charId);
        result.put("charInstId", charInstId);
        result.put("isNew", isNew ? 1 : 0);
        result.put("itemGet", itemGet);

        return result;
    }

    private JSONObject createNewCharacter(String charId, int instId) {

        JSONArray skilsArray = ArknightsApplication.characterJson.getJSONObject(charId).getJSONArray("skills");
        JSONArray skils = new JSONArray();
        for (int i = 0; i < skilsArray.size(); i++) {
            JSONObject new_skils = new JSONObject(true);
            new_skils.put("skillId", skilsArray.getJSONObject(i).getString("skillId"));
            new_skils.put("state", 0);
            new_skils.put("specializeLevel", 0);
            new_skils.put("completeUpgradeTime", -1);

            if (skilsArray.getJSONObject(i).getJSONObject("unlockCond").getIntValue("phase") == 0) {
                new_skils.put("unlock", 1);
            } else {
                new_skils.put("unlock", 0);
            }

            skils.add(new_skils);
        }
        JSONObject charData = new JSONObject();
        charData.put("charId", charId);
        charData.put("instId", instId);
        charData.put("level", 1);
        charData.put("exp", 0);
        charData.put("evolvePhase", 0);
        charData.put("favorPoint", 0);
        charData.put("potentialRank", 0);
        charData.put("mainSkillLvl", 1);
        charData.put("skin", charId + "#1");
        charData.put("gainTime", System.currentTimeMillis() / 1000);

        // 初始化技能
        JSONArray skills = new JSONArray();
        JSONObject charInfo = ArknightsApplication.characterJson.getJSONObject(charId);
        for (Object skillObj : charInfo.getJSONArray("skills")) {
            JSONObject skill = (JSONObject) skillObj;
            JSONObject newSkill = new JSONObject();
            newSkill.put("skillId", skill.getString("skillId"));
            newSkill.put("state", 0);
            newSkill.put("specializeLevel", 0);
            newSkill.put("completeUpgradeTime", -1);
            newSkill.put("unlock",
                    skill.getJSONObject("unlockCond").getIntValue("phase") == 0 ? 1 : 0);
            skills.add(newSkill);
        }
        charData.put("skills", skills);
        charData.put("currentEquip", null);
        JSONObject equip = new JSONObject();
        charData.put("equip", equip);
        // 设置语音语言
        charData.put("voiceLan", "JP");
        charData.put("defaultSkillIndex", -1);
        charData.put("starMark", skils.isEmpty() ? -1 : 0);

        return charData;
    }

    private JSONObject createBuildingCharacter(String charId) {
        JSONObject buildingChar = new JSONObject();
        buildingChar.put("charId", charId);
        buildingChar.put("lastApAddTime", System.currentTimeMillis() / 1000);
        buildingChar.put("ap", 8640000);
        buildingChar.put("roomSlotId", "");
        buildingChar.put("index", -1);
        buildingChar.put("changeScale", 0);

        JSONObject bubble = new JSONObject();
        JSONObject normal = new JSONObject();
        normal.put("add", -1);
        normal.put("ts", 0);
        bubble.put("normal", normal);

        JSONObject assist = new JSONObject();
        assist.put("add", -1);
        assist.put("ts", 0);
        bubble.put("assist", assist);

        buildingChar.put("bubble", bubble);
        buildingChar.put("workTime", 0);

        return buildingChar;
    }

    private void updateUserAfterNormalGacha(JSONObject userData, JSONObject gachaResult, int slotId) {
        // 重置招募位
        JSONObject recruitSlot = userData.getJSONObject("recruit")
                .getJSONObject("normal")
                .getJSONObject("slots")
                .getJSONObject(String.valueOf(slotId));
        recruitSlot.put("state", 1);
        recruitSlot.put("selectTags", new JSONArray());
    }

    private JSONObject createEmptyPoolResponse() {
        JSONObject result = new JSONObject();
        result.put("result", 1);
        result.put("errMsg", "卡池数据不存在");
        return result;
    }

    private JSONObject performAdvancedGacha(Account account, String poolId,
                                            String ticketType, int diamondCost,
                                            int drawCount, int useTkt,
                                            HttpServletResponse response) {
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject status = userData.getJSONObject("status");

        // 验证资源
        if (useTkt == 1 || useTkt == 2) {
            if (status.getIntValue(ticketType) < drawCount) {
                return createErrorResponse(400, "寻访凭证不足");
            }
        } else {
            if (status.getIntValue("diamondShard") < diamondCost * drawCount) {
                return createErrorResponse(400, "合成玉不足");
            }
        }

        // 读取卡池数据
        String poolPath = "data/gacha/" + poolId + ".json";
        JSONObject poolData = IOTools.ReadJsonFile(poolPath);
        if (poolData == null) {
            poolData = IOTools.ReadJsonFile("data/gacha/DEFAULT.json");
            if (poolData == null) {
                response.setStatus(500);
                return createErrorResponse(500, "卡池数据加载失败");
            }
        }

        // 获取保底数据
        JSONObject UserSyncData = JSON.parseObject(account.getUser());
        JSONObject exGachaData = UserSyncData.getJSONObject("exgachadata");
        if (exGachaData == null) {
            exGachaData = new JSONObject();
        }

        String gachaType = poolId.startsWith("CLASSIC") ? "CLASSIC" :
                poolId.startsWith("BOOT") ? "BOOT" : "NORM";
        int gachaCount = exGachaData.getIntValue(gachaType);

        JSONArray gachaResults = new JSONArray();
        JSONObject chars = userData.getJSONObject("troop").getJSONObject("chars");
        JSONObject buildingChars = userData.getJSONObject("troop").getJSONObject("building").getJSONObject("chars");
        JSONArray availCharInfo = poolData.getJSONObject("detailInfo")
                .getJSONObject("availCharInfo")
                .getJSONArray("perAvailList");

        for (int i = 0; i < drawCount; i++) {
            // 执行单次抽卡
            JSONObject gachaResult = performSingleGacha(userData, chars, buildingChars,
                    availCharInfo, gachaCount, poolId);
            gachaResults.add(gachaResult);

            // 更新保底计数
            if (gachaResult.getIntValue("rarity") == 5) {
                gachaCount = 0;
            } else {
                gachaCount++;
            }
        }
        Long uid = account.getUid();

        // 保存保底数据
        exGachaData.put(gachaType, gachaCount);
        userDao.setUserData(uid, UserSyncData);

        // 扣除资源
        if (useTkt == 1 || useTkt == 2) {
            status.put(ticketType, status.getIntValue(ticketType) - drawCount);
        } else {
            status.put("diamondShard", status.getIntValue("diamondShard") - diamondCost * drawCount);
        }

        // 保存用户数据
        userDao.setUserData(account.getUid(), userData);

        // 构建响应
        JSONObject result = new JSONObject();
        result.put("result", 0);

        if (drawCount == 1) {
            result.put("charGet", gachaResults.getJSONObject(0));
        } else {
            result.put("gachaResultList", gachaResults);
        }

        JSONObject playerDataDelta = new JSONObject();
        JSONObject modified = new JSONObject();
        modified.put("status", status);
        modified.put("troop", userData.getJSONObject("troop"));
        modified.put("inventory", userData.getJSONObject("inventory"));
        modified.put("gacha", userData.getJSONObject("gacha"));
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject());
        result.put("playerDataDelta", playerDataDelta);

        return result;
    }

    private JSONObject performSingleGacha(JSONObject userData, JSONObject chars,
                                          JSONObject buildingChars, JSONArray availCharInfo,
                                          int gachaCount, String poolId) {
        // 构建概率数组
        List<JSONObject> randomRankList = new ArrayList<>();
        for (int i = 0; i < availCharInfo.size(); i++) {
            JSONObject charInfo = availCharInfo.getJSONObject(i);
            int totalPercent = (int)(charInfo.getDoubleValue("totalPercent") * 100);
            int rarityRank = charInfo.getIntValue("rarityRank");

            // 保底加成
            if (rarityRank == 5) {
                totalPercent += (gachaCount + 50) / 50 * 2;
            }

            JSONObject rankObj = new JSONObject();
            rankObj.put("rarityRank", rarityRank);
            rankObj.put("index", i);

            for (int j = 0; j < totalPercent; j++) {
                randomRankList.add(rankObj);
            }
        }

        // 随机选择
        Collections.shuffle(randomRankList);
        JSONObject selectedRank = randomRankList.get(new Random().nextInt(randomRankList.size()));
        JSONArray charIdList = availCharInfo.getJSONObject(selectedRank.getIntValue("index"))
                .getJSONArray("charIdList");
        JSONObject poolData = IOTools.ReadJsonFile("data/gacha/" + poolId + ".json");

        // 处理UP角色
        if (!poolId.startsWith("BOOT")) {
            try {
                JSONArray upCharInfo = poolData.getJSONObject("detailInfo")
                        .getJSONObject("upCharInfo")
                        .getJSONArray("perCharList");
                for (int i = 0; i < upCharInfo.size(); i++) {
                    JSONObject upChar = upCharInfo.getJSONObject(i);
                    if (upChar.getIntValue("rarityRank") == selectedRank.getIntValue("rarityRank")) {
                        int upRate = (int)(upChar.getDoubleValue("percent") * 100) - 15;
                        JSONArray upCharIds = upChar.getJSONArray("charIdList");
                        for (int j = 0; j < upCharIds.size(); j++) {
                            String upCharId = upCharIds.getString(j);
                            for (int k = 0; k < upRate; k++) {
                                charIdList.add(upCharId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                ArknightsApplication.LOGGER.error("处理UP角色异常", e);
            }
        }

        Collections.shuffle(charIdList);
        String charId = charIdList.getString(new Random().nextInt(charIdList.size()));

        // 检查是否已有角色
        Pattern pattern = Pattern.compile("char_(\\d+)_");
        Matcher matcher = pattern.matcher(charId);
        ArknightsApplication.LOGGER.info("rewardSId:" + charId);

        int CharId = 0;
        if (matcher.find()) {
            CharId = Integer.parseInt(matcher.group(1));
            ArknightsApplication.LOGGER.info("提取到的数字是: " + CharId);
        } else {
            ArknightsApplication.LOGGER.info("未匹配到数字");
        }
        boolean isNew = true;
        int charInstId = CharId;
        for (String key : chars.keySet()) {
            if (chars.getJSONObject(key).getString("charId").equals(charId)) {
                isNew = false;
                charInstId = Integer.parseInt(key);
                break;
            }
        }

        // 构建结果
        JSONObject result = new JSONObject();
        result.put("charId", charId);
        result.put("rarity", selectedRank.getIntValue("rarityRank"));
        JSONArray itemGet = new JSONArray();

        if (isNew) {
            // 新角色处理
            charInstId = CharId;
            JSONObject newChar = createNewCharacter(charId, charInstId);
            chars.put(String.valueOf(charInstId), newChar);

            // 添加基建数据
            JSONObject buildingChar = createBuildingCharacter(charId);
            buildingChars.put(String.valueOf(charInstId), buildingChar);

            // 添加角色组
            JSONObject charGroup = new JSONObject();
            charGroup.put("favorPoint", 0);
            userData.getJSONObject("troop").getJSONObject("charGroup").put(charId, charGroup);

            // 添加获得物品
            JSONObject hggShard = new JSONObject();
            hggShard.put("type", "HGG_SHD");
            hggShard.put("id", "4004");
            hggShard.put("count", 1);
            itemGet.add(hggShard);
            userData.getJSONObject("status").put("hggShard",
                    userData.getJSONObject("status").getIntValue("hggShard") + 1);

            // 初始化潜能
            userData.getJSONObject("inventory").put("p_" + charId, 0);
        } else {
            // 重复角色处理
            JSONObject existingChar = chars.getJSONObject(String.valueOf(charInstId));
            int rarity = selectedRank.getIntValue("rarityRank");
            int potentialRank = existingChar.getIntValue("potentialRank");

            // 根据稀有度添加物品
            JSONObject shardItem = new JSONObject();
            if (rarity <= 3) {
                shardItem.put("type", "LGG_SHD");
                shardItem.put("id", "4005");
                shardItem.put("count", rarity == 3 ? 30 : (rarity == 2 ? 5 : 1));
                userData.getJSONObject("status").put("lggShard",
                        userData.getJSONObject("status").getIntValue("lggShard") +
                                shardItem.getIntValue("count"));
            } else {
                shardItem.put("type", "HGG_SHD");
                shardItem.put("id", "4004");
                shardItem.put("count", rarity == 5 ?
                        (potentialRank < 5 ? 10 : 15) :
                        (potentialRank < 5 ? 5 : 8));
                userData.getJSONObject("status").put("hggShard",
                        userData.getJSONObject("status").getIntValue("hggShard") +
                                shardItem.getIntValue("count"));
            }
            itemGet.add(shardItem);

            // 添加潜能
            JSONObject potentialItem = new JSONObject();
            potentialItem.put("type", "MATERIAL");
            potentialItem.put("id", "p_" + charId);
            potentialItem.put("count", 1);
            itemGet.add(potentialItem);
            userData.getJSONObject("inventory").put("p_" + charId,
                    userData.getJSONObject("inventory").getIntValue("p_" + charId) + 1);
        }

        result.put("charInstId", charInstId);
        result.put("isNew", isNew ? 1 : 0);
        result.put("itemGet", itemGet);

        return result;
    }
}