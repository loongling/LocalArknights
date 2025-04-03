package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.decrypt.Utils;
import com.hypergryph.arknights.core.pojo.Account;
import com.hypergryph.arknights.core.pojo.SearchAssistCharList;
import com.hypergryph.arknights.core.pojo.UserInfo;

import java.util.*;
import java.util.stream.IntStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.hypergryph.arknights.ArknightsApplication.mainStage;
import static com.hypergryph.arknights.ArknightsApplication.stageTable;

@RestController
@RequestMapping({"/quest"})
public class quest {
    public quest() {
    }

    @PostMapping(
            value = {"/battleStart"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject BattleStart(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        ArknightsApplication.LOGGER.info("Received JSON: " + JsonBody.toJSONString());
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /quest/battleStart");
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String stageId = JsonBody.getString("stageId");
            int isReplay = JsonBody.getIntValue("isReplay");
            int startTs = JsonBody.getIntValue("startTs");
            int usePracticeTicket = JsonBody.getIntValue("usePracticeTicket");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            } else {
                Long uid = ((Account)Accounts.get(0)).getUid();
                JSONObject UserSyncData;
                if (((Account)Accounts.get(0)).getBan() == 1L) {
                    response.setStatus(500);
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("statusCode", 403);
                    UserSyncData.put("error", "Bad Request");
                    UserSyncData.put("message", "error");
                    return UserSyncData;
                } else {
                    UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                    JSONObject stage_table = stageTable.getJSONObject(stageId);
                    JSONObject result;
                    if (!UserSyncData.getJSONObject("dungeon").getJSONObject("stages").containsKey(stageId)) {
                        result = new JSONObject(true);
                        result.put("completeTimes", 0);
                        result.put("hasBattleReplay", 0);
                        result.put("noCostCnt", 1);
                        result.put("practiceTimes", 0);
                        result.put("stageId", stageId);
                        result.put("startTimes", 0);
                        result.put("state", 0);
                        UserSyncData.getJSONObject("dungeon").getJSONObject("stages").put(stageId, result);
                    }

                    if (usePracticeTicket == 1) {
                        UserSyncData.getJSONObject("status").put("practiceTicket", UserSyncData.getJSONObject("status").getIntValue("practiceTicket") - 1);
                        UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("practiceTimes", 1);
                    }
                    UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("startTimes", UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getIntValue("startTimes") + 1);
                    userDao.setUserData(uid, UserSyncData);
                    result = new JSONObject(true);
                    JSONObject playerDataDelta = new JSONObject(true);
                    JSONObject modified = new JSONObject(true);
                    JSONObject dungeon = new JSONObject(true);
                    JSONObject stages = new JSONObject(true);
                    stages.put(stageId, UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId));
                    dungeon.put("stages", stages);
                    modified.put("dungeon", dungeon);
                    modified.put("status", UserSyncData.getJSONObject("status"));
                    playerDataDelta.put("deleted", new JSONObject(true));
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    result.put("result", 0);
                    result.put("battleId", stageId);
                    result.put("isApProtect", 0);
                    result.put("apFailReturn", stage_table.getIntValue("apFailReturn"));
                    if (UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getIntValue("noCostCnt") == 1) {
                        result.put("isApProtect", 1);
                        result.put("apFailReturn", stage_table.getIntValue("apCost"));
                    }

                    if (stage_table.getIntValue("apCost") == 0) {
                        result.put("isApProtect", 0);
                        result.put("apFailReturn", 0);
                    }

                    if (usePracticeTicket == 1) {
                        result.put("isApProtect", 0);
                        result.put("apFailReturn", 0);
                    }

                    return result;
                }
            }
        }
    }
JSONArray firstReward = new JSONArray();
    @PostMapping(
            value = {"/battleFinish"},
            produces = {"application/json;charset=UTF-8"}
    )
        public JSONObject BattleFinish(@RequestBody JSONObject JsonBody,
                                       HttpServletResponse response,
                                       HttpServletRequest request) {
            // 1. 初始化与基础验证
            String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
            ArknightsApplication.LOGGER.info("[/" + clientIp + "] /quest/battleFinish");

            JSONObject result = new JSONObject(true);

            if (!ArknightsApplication.enableServer) {
                response.setStatus(400);
                result.put("statusCode", 400);
                result.put("error", "Bad Request");
                result.put("message", "server is close");
                return result;
            }

            // 2. 验证用户
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            }

            Account account = Accounts.get(0);
            Long uid = account.getUid();

            if (account.getBan() == 1L) {
                response.setStatus(500);
                JSONObject banResult = new JSONObject(true);
                banResult.put("statusCode", 403);
                banResult.put("error", "Bad Request");
                banResult.put("message", "error");
                return banResult;
            }

            // 3. 获取用户数据
            JSONObject UserSyncData = JSON.parseObject(account.getUser());
            JSONObject BattleData = Utils.BattleData_decrypt(JsonBody.getString("data"),
                    UserSyncData.getJSONObject("pushFlags").getString("status"));

            String stageId = BattleData.getString("battleId");
            JSONObject stage_table = stageTable.getJSONObject(stageId);

            // 4. 获取关卡解锁信息
            JSONObject stageClear = new JSONObject();
            if (ArknightsApplication.mainStage.containsKey(stageId)) {
                stageClear = ArknightsApplication.mainStage.getJSONObject(stageId);
            } else {
                stageClear.put("next", (Object)null);
                stageClear.put("star", (Object)null);
                stageClear.put("sub", (Object)null);
                stageClear.put("hard", (Object)null);
            }

            // 5. 处理练习模式
            if (UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getIntValue("practiceTimes") == 1) {
                return handlePracticeMode(UserSyncData, uid, stageId);
            }

            // 6. 处理正常战斗
            JSONObject chars = UserSyncData.getJSONObject("troop").getJSONObject("chars");
            JSONObject troop = new JSONObject(true);

            int DropRate = ArknightsApplication.serverConfig.getJSONObject("battle").getIntValue("dropRate");
            int completeState = BattleData.getIntValue("completeState");

            if (ArknightsApplication.serverConfig.getJSONObject("battle").getBooleanValue("debug")) {
                completeState = 3;
            }

            int apCost = stage_table.getIntValue("apCost");
            int expGain = stage_table.getIntValue("expGain");
            int goldGain = stage_table.getIntValue("goldGain");

            // 7. 处理AP消耗
            handleApConsumption(UserSyncData, apCost, completeState, stage_table);

            // 8. 战斗失败处理
            if (completeState == 1) {
                return handleBattleFailure(UserSyncData, uid, stage_table, stageId);
            }

            // 9. 战斗成功处理
            updateStageState(UserSyncData, stageId, completeState);

            // 10. 首次通关奖励
        boolean FirstClear = checkFirstClear(UserSyncData, stageId);
            if (FirstClear) {
                handleFirstClearRewards(UserSyncData, stageId, chars, troop);
            }

            // 11. 关卡解锁
            JSONArray unlockStages = new JSONArray();
            JSONArray unlockStagesObject = new JSONArray();
            handleStageUnlock(UserSyncData, stageId, completeState, stageClear, unlockStages, unlockStagesObject);

            // 12. 发放奖励
            JSONObject rewards = calculateRewards(UserSyncData, stage_table, completeState, FirstClear, DropRate);

            // 13. 更新干员好感度
            updateOperatorFavor(UserSyncData, BattleData, stage_table, completeState);

            // 14. 构建返回结果
            result.put("result", 0);
            result.put("alert", new JSONArray());
            result.put("suggestFriend", false);
            result.put("apFailReturn", 0);
            result.putAll(rewards);
            result.put("unlockStages", unlockStages);

            // 15. 构建玩家数据变更
            JSONObject playerDataDelta = buildPlayerDataDelta(UserSyncData, unlockStagesObject, stageId, troop);
            result.put("playerDataDelta", playerDataDelta);
            UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("completeTimes", UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getIntValue("completeTimes") + 1);

            // 16. 保存数据
            userDao.setUserData(uid, UserSyncData);
            ArknightsApplication.LOGGER.info(result.toJSONString());

            return result;
        }

        // ========== 辅助方法实现 ==========

        private JSONObject handlePracticeMode(JSONObject UserSyncData, Long uid, String stageId) {
            UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("practiceTimes", 0);

            if (UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getIntValue("state") == 0) {
                UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("state", 1);
            }

            userDao.setUserData(uid, UserSyncData);

            JSONObject chars = new JSONObject(true);
            JSONObject troop = new JSONObject(true);
            JSONObject result = new JSONObject(true);

            JSONObject dungeon = new JSONObject(true);
            JSONObject stages = new JSONObject(true);
            stages.put(stageId, UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId));
            dungeon.put("stages", stages);

            result.put("status", UserSyncData.getJSONObject("status"));
            result.put("dungeon", dungeon);

            troop.put("deleted", new JSONObject(true));
            troop.put("modified", result);

            chars.put("playerDataDelta", troop);
            chars.put("result", 0);

            return chars;
        }

        private void handleApConsumption(JSONObject UserSyncData, int apCost, int completeState, JSONObject stage_table) {
            int nowTime = (int)(new Date().getTime() / 1000L);
            int lastApAddTime = UserSyncData.getJSONObject("status").getIntValue("lastApAddTime");
            int addAp = (nowTime - lastApAddTime) / 360;

            if (UserSyncData.getJSONObject("status").getIntValue("ap") < UserSyncData.getJSONObject("status").getIntValue("maxAp")) {
                if (UserSyncData.getJSONObject("status").getIntValue("ap") + addAp >= UserSyncData.getJSONObject("status").getIntValue("maxAp")) {
                    UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("maxAp"));
                    UserSyncData.getJSONObject("status").put("lastApAddTime", nowTime);
                } else if (addAp != 0) {
                    UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("ap") + addAp);
                    UserSyncData.getJSONObject("status").put("lastApAddTime", nowTime);
                }
            }

            UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("ap") - apCost);
        }

        private JSONObject handleBattleFailure(JSONObject UserSyncData, Long uid, JSONObject stage_table, String stageId) {
            int apFailReturn = stage_table.getIntValue("apFailReturn");
            if (UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getIntValue("noCostCnt") == 1) {
                apFailReturn = stage_table.getIntValue("apCost");
                UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("noCostCnt", 0);
            }

            int nowTime = (int)(new Date().getTime() / 1000L);
            int lastApAddTime = UserSyncData.getJSONObject("status").getIntValue("lastApAddTime");
            int addAp = (lastApAddTime - nowTime) / 360;

            if (UserSyncData.getJSONObject("status").getIntValue("ap") < UserSyncData.getJSONObject("status").getIntValue("maxAp")) {
                if (UserSyncData.getJSONObject("status").getIntValue("ap") + addAp >= UserSyncData.getJSONObject("status").getIntValue("maxAp")) {
                    UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("maxAp"));
                    UserSyncData.getJSONObject("status").put("lastApAddTime", nowTime);
                } else {
                    UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("ap") + addAp);
                    UserSyncData.getJSONObject("status").put("lastApAddTime", nowTime);
                }
            }

            UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("ap") + apFailReturn);
            UserSyncData.getJSONObject("status").put("lastApAddTime", nowTime);
            userDao.setUserData(uid, UserSyncData);

            JSONObject result = new JSONObject(true);
            JSONObject playerDataDelta = new JSONObject(true);
            JSONObject modified = new JSONObject(true);

            result.put("additionalRewards", new JSONArray());
            result.put("alert", new JSONArray());
            result.put("firstRewards", firstReward);
            result.put("furnitureRewards", new JSONArray());
            result.put("unlockStages", new JSONArray());
            result.put("unusualRewards", new JSONArray());
            result.put("rewards", new JSONArray());
            result.put("expScale", 0);
            result.put("goldScale", 0);
            result.put("apFailReturn", apFailReturn);

            modified.put("status", UserSyncData.getJSONObject("status"));

            JSONObject dungeon = new JSONObject(true);
            JSONObject stages = new JSONObject(true);
            stages.put(stageId, UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId));
            dungeon.put("stages", stages);
            modified.put("dungeon", dungeon);

            playerDataDelta.put("deleted", new JSONObject(true));
            playerDataDelta.put("modified", modified);

            result.put("playerDataDelta", playerDataDelta);
            result.put("result", 0);

            return result;
        }

        private void updateStageState(JSONObject UserSyncData, String stageId, int completeState) {
            JSONObject stages_data = UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId);

            if (stages_data.getIntValue("state") == 0) {
                stages_data.put("state", 1);
            }

            if (stages_data.getIntValue("state") != 3) {
                stages_data.put("state", completeState);
            }

            if (completeState == 4) {
                stages_data.put("state", completeState);
            }
        }

        private boolean checkFirstClear(JSONObject UserSyncData, String stageId) {
            int complete = UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getIntValue("completeTimes");
            return complete == 0;
        }

    private void handleFirstClearRewards(JSONObject UserSyncData, String stageId,
                                         JSONObject chars, JSONObject troop) {
        if (stageId.equals("main_08-16")) {
            handleAmiyaTransform(UserSyncData, chars, troop);
        }

        JSONArray displayDetailRewards = stageTable.getJSONObject(stageId).getJSONObject("stageDropInfo").getJSONArray("displayDetailRewards");
        ArknightsApplication.LOGGER.info("DDR:" + displayDetailRewards.toJSONString());

        for (int i = 0; i < displayDetailRewards.size(); i++) {
            JSONObject reward = displayDetailRewards.getJSONObject(i);
            int dropType = reward.getIntValue("dropType");
            String reward_id = reward.getString("id");
            String reward_type = reward.getString("type");

            if (dropType == 1 || dropType == 8) {
                handleFirstClearReward(UserSyncData, reward_id, reward_type, chars, troop);
                JSONObject filteredReward = new JSONObject();
                filteredReward.put("type", reward.getString("type"));
                filteredReward.put("id", reward.getString("id"));
                filteredReward.put("count", 1);
                firstReward.add(filteredReward);
            }
        }
        ArknightsApplication.LOGGER.info("First rewards extracted: " + firstReward.toJSONString());
    }

        private void handleAmiyaTransform(JSONObject UserSyncData, JSONObject chars, JSONObject troop) {
            for (Map.Entry<String, Object> entry : UserSyncData.getJSONObject("troop").getJSONObject("chars").entrySet()) {
                JSONObject charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(entry.getKey());
                String charId = charData.getString("charId");

                if (charId.equals("char_002_amiya")) {
                    JSONArray amiya_skills = charData.getJSONArray("skills");
                    String skin = charData.getString("skin");
                    int defaultSkillIndex = charData.getIntValue("defaultSkillIndex");

                    charData.put("skin", (Object)null);
                    charData.put("defaultSkillIndex", -1);
                    charData.put("skills", new JSONArray());
                    charData.put("currentTmpl", "char_1001_amiya2");

                    JSONObject tmpl = new JSONObject(true);
                    JSONObject charList = new JSONObject(true);
                    charList.put("skinId", skin);
                    charList.put("defaultSkillIndex", defaultSkillIndex);
                    charList.put("skills", amiya_skills);
                    charList.put("currentEquip", (Object)null);
                    charList.put("equip", new JSONObject(true));
                    tmpl.put("char_002_amiya", charList);

                    JSONArray sword_amiya_skills = new JSONArray();
                    JSONObject skill1 = new JSONObject(true);
                    skill1.put("skillId", "skchr_amiya2_1");
                    skill1.put("unlock", 1);
                    skill1.put("state", 0);
                    skill1.put("specializeLevel", 0);
                    skill1.put("completeUpgradeTime", -1);
                    sword_amiya_skills.add(skill1);

                    JSONObject skill2 = new JSONObject(true);
                    skill2.put("skillId", "skchr_amiya2_1");
                    skill2.put("unlock", 1);
                    skill2.put("state", 0);
                    skill2.put("specializeLevel", 0);
                    skill2.put("completeUpgradeTime", -1);
                    sword_amiya_skills.add(skill2);

                    JSONObject newCharData = new JSONObject(true);
                    newCharData.put("skinId", "char_1001_amiya2#2");
                    newCharData.put("defaultSkillIndex", 0);
                    newCharData.put("skills", sword_amiya_skills);
                    newCharData.put("currentEquip", (Object)null);
                    newCharData.put("equip", new JSONObject(true));
                    tmpl.put("char_1001_amiya2", newCharData);

                    charData.put("tmpl", tmpl);
                    JSONObject charinstId = new JSONObject(true);
                    charinstId.put(entry.getKey(), charData);
                    troop.put("chars", charinstId);
                    UserSyncData.getJSONObject("troop").getJSONObject("chars").put(entry.getKey(), charData);
                    break;
                }
            }
        }

        private void handleFirstClearReward(JSONObject UserSyncData, String reward_id, String reward_type,
                                            JSONObject chars, JSONObject troop) {
            if (!reward_type.equals("CHAR")) {
                // 处理非干员奖励
                handleNonCharFirstClearReward(UserSyncData, reward_id, reward_type);
            } else {
                // 处理干员奖励
                handleCharFirstClearReward(UserSyncData, reward_id, chars, troop);
            }
        }

        private void handleNonCharFirstClearReward(JSONObject UserSyncData, String reward_id, String reward_type) {
            switch (reward_type) {
                case "MATERIAL":
                case "CARD_EXP":
                    UserSyncData.getJSONObject("inventory").put(reward_id,
                            UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + 1);
                    break;
                case "DIAMOND":
                    UserSyncData.getJSONObject("status").put("androidDiamond",
                            UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + 1);
                    UserSyncData.getJSONObject("status").put("iosDiamond",
                            UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + 1);
                    break;
                case "GOLD":
                    UserSyncData.getJSONObject("status").put("gold",
                            UserSyncData.getJSONObject("status").getIntValue("gold") + 1);
                    break;
                case "TKT_RECRUIT":
                    UserSyncData.getJSONObject("status").put("recruitLicense",
                            UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + 1);
                    break;
                case "FURN":
                    if (!UserSyncData.getJSONObject("building").getJSONObject("furniture").containsKey(reward_id)) {
                        JSONObject furniture = new JSONObject(true);
                        furniture.put("count", 1);
                        furniture.put("inUse", 0);
                        UserSyncData.getJSONObject("building").getJSONObject("furniture").put(reward_id, furniture);
                    } else {
                        UserSyncData.getJSONObject("building").getJSONObject("furniture").getJSONObject(reward_id)
                                .put("count", UserSyncData.getJSONObject("building").getJSONObject("furniture").getJSONObject(reward_id).getIntValue("count") + 1);
                    }
                    break;
            }
        }

        private void handleCharFirstClearReward(JSONObject UserSyncData, String reward_id,
                                                JSONObject chars, JSONObject troop) {
            String randomCharId = reward_id;
            int dropType = 0;

            // 检查是否已拥有该干员
            for (int i = 0; i < UserSyncData.getJSONObject("troop").getJSONObject("chars").size(); i++) {
                if (UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(i + 1))
                        .getString("charId").equals(randomCharId)) {
                    dropType = i + 1;
                    break;
                }
            }

            if (dropType == 0) {
                // 新干员
                handleNewCharReward(UserSyncData, randomCharId, chars, troop);
            } else {
                // 已有干员
                handleExistingCharReward(UserSyncData, randomCharId, dropType, chars, troop);
            }
        }

        private void handleNewCharReward(JSONObject UserSyncData, String randomCharId,
                                         JSONObject chars, JSONObject troop) {
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

                if (skilsArray.getJSONObject(i).getJSONObject("unlockCond").getIntValue("phase") == 0) {
                    new_skils.put("unlock", 1);
                } else {
                    new_skils.put("unlock", 0);
                }

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
                JSONObject equip1 = new JSONObject(true);
                equip1.put("hide", 0);
                equip1.put("locked", 0);
                equip1.put("level", 1);

                JSONObject equip2 = new JSONObject(true);
                equip2.put("hide", 0);
                equip2.put("locked", 0);
                equip2.put("level", 1);

                charGroup.put("uniequip_001_" + itemId, equip1);
                charGroup.put("uniequip_002_" + itemId, equip2);

                char_data.put("equip", charGroup);
                char_data.put("currentEquip", "uniequip_001_" + itemId);
            } else {
                char_data.put("currentEquip", (Object)null);
            }

            UserSyncData.getJSONObject("troop").getJSONObject("chars").put(String.valueOf(instId), char_data);

            JSONObject charGroup = new JSONObject(true);
            charGroup.put("favorPoint", 0);
            UserSyncData.getJSONObject("troop").getJSONObject("charGroup").put(randomCharId, charGroup);

            get_char.put("charInstId", instId);
            get_char.put("charId", randomCharId);
            get_char.put("isNew", 1);

            JSONArray itemGet = new JSONArray();
            JSONObject hggShard = new JSONObject(true);
            hggShard.put("type", "HGG_SHD");
            hggShard.put("id", "4004");
            hggShard.put("count", 1);
            itemGet.add(hggShard);

            UserSyncData.getJSONObject("status").put("hggShard", UserSyncData.getJSONObject("status").getIntValue("hggShard") + 1);
            get_char.put("itemGet", itemGet);
            UserSyncData.getJSONObject("inventory").put("p_" + randomCharId, 0);

            JSONObject newCharInst = new JSONObject(true);
            newCharInst.put(String.valueOf(instId), char_data);
            chars.put(String.valueOf(instId), char_data);
            troop.put("chars", newCharInst);
        }

        private void handleExistingCharReward(JSONObject UserSyncData, String randomCharId, int dropType,
                                              JSONObject chars, JSONObject troop) {
            JSONObject get_char = new JSONObject(true);
            get_char.put("charInstId", dropType);
            get_char.put("charId", randomCharId);
            get_char.put("isNew", 0);

            JSONObject char_data = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(dropType));
            int potentialRank = char_data.getIntValue("potentialRank");
            int reward_rarity = ArknightsApplication.characterJson.getJSONObject(randomCharId).getIntValue("rarity");

            String itemName = null;
            String itemType = null;
            String itemId = null;
            int cur = 0;

            switch (reward_rarity) {
                case 0:
                case 1:
                    itemName = "lggShard";
                    itemType = "LGG_SHD";
                    itemId = "4005";
                    cur = reward_rarity == 0 ? 1 : 1;
                    break;
                case 2:
                    itemName = "lggShard";
                    itemType = "LGG_SHD";
                    itemId = "4005";
                    cur = 5;
                    break;
                case 3:
                    itemName = "lggShard";
                    itemType = "LGG_SHD";
                    itemId = "4005";
                    cur = 30;
                    break;
                case 4:
                    itemName = "hggShard";
                    itemType = "HGG_SHD";
                    itemId = "4004";
                    cur = potentialRank != 5 ? 5 : 8;
                    break;
                case 5:
                    itemName = "hggShard";
                    itemType = "HGG_SHD";
                    itemId = "4004";
                    cur = potentialRank != 5 ? 10 : 15;
                    break;
            }

            JSONArray itemGet = new JSONArray();
            JSONObject shard = new JSONObject(true);
            shard.put("type", itemType);
            shard.put("id", itemId);
            shard.put("count", cur);
            itemGet.add(shard);

            UserSyncData.getJSONObject("status").put(itemName, UserSyncData.getJSONObject("status").getIntValue(itemName) + cur);

            JSONObject potentialItem = new JSONObject(true);
            potentialItem.put("type", "MATERIAL");
            potentialItem.put("id", "p_" + randomCharId);
            potentialItem.put("count", 1);
            itemGet.add(potentialItem);

            get_char.put("itemGet", itemGet);
            UserSyncData.getJSONObject("inventory").put("p_" + randomCharId,
                    UserSyncData.getJSONObject("inventory").getIntValue("p_" + randomCharId) + 1);

            JSONObject charinstId = new JSONObject(true);
            charinstId.put(String.valueOf(dropType), char_data);
            chars.put(String.valueOf(dropType), char_data);
            troop.put("chars", charinstId);
        }

        private void handleStageUnlock(JSONObject UserSyncData, String stageId, int completeState,
                                       JSONObject stageClear, JSONArray unlockStages, JSONArray unlockStagesObject) {
            JSONObject stages_data = UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId);
            JSONObject stage_table = mainStage.getJSONObject(stageId);

            if (stages_data.getIntValue("state") == 1 && (completeState == 3 || completeState == 2)) {
                // 解锁后续关卡
                if (stageClear.getString("next") != null) {
                    String nextStageId = stageClear.getString("next");
                    JSONObject hard_unlockStage = createNewStageEntry(nextStageId);
                    UserSyncData.getJSONObject("dungeon").getJSONObject("stages").put(nextStageId, hard_unlockStage);

                    if (stage_table.getString("stageType").equals("MAIN") || stage_table.getString("stageType").equals("SUB")) {
                        UserSyncData.getJSONObject("status").put("mainStageProgress", nextStageId);
                    }

                    unlockStages.add(nextStageId);
                    unlockStagesObject.add(hard_unlockStage);
                }

                // 解锁支线关卡
                if (stageClear.getString("sub") != null) {
                    String subStageId = stageClear.getString("sub");
                    JSONObject sub_unlockStage = createNewStageEntry(subStageId);
                    UserSyncData.getJSONObject("dungeon").getJSONObject("stages").put(subStageId, sub_unlockStage);
                    unlockStages.add(subStageId);
                    unlockStagesObject.add(sub_unlockStage);
                }

                // 三星通关解锁
                if (completeState == 3) {
                    if (stageClear.getString("star") != null) {
                        String starStageId = stageClear.getString("star");
                        JSONObject star_unlockStage = createNewStageEntry(starStageId);
                        UserSyncData.getJSONObject("dungeon").getJSONObject("stages").put(starStageId, star_unlockStage);
                        unlockStages.add(starStageId);
                        unlockStagesObject.add(star_unlockStage);
                    }

                    if (stageClear.getString("hard") != null) {
                        String hardStageId = stageClear.getString("hard");
                        JSONObject hard_unlockStage = createNewStageEntry(hardStageId);
                        UserSyncData.getJSONObject("dungeon").getJSONObject("stages").put(hardStageId, hard_unlockStage);
                        unlockStages.add(hardStageId);
                        unlockStagesObject.add(hard_unlockStage);
                    }
                }
            }
        }

        private JSONObject createNewStageEntry(String stageId) {
            JSONObject newStage = new JSONObject(true);
            newStage.put("stageId", stageId);
            newStage.put("hasBattleReplay", 0);
            newStage.put("noCostCnt", 1);
            newStage.put("practiceTimes", 0);
            newStage.put("completeTimes", 0);
            newStage.put("state", 0);
            newStage.put("startTimes", 0);
            return newStage;
        }

        private JSONObject calculateRewards(JSONObject UserSyncData, JSONObject stage_table,
                                            int completeState, boolean FirstClear, int DropRate) {
            JSONObject result = new JSONObject();

            // 初始化奖励数组
            JSONArray additionalRewards = new JSONArray();
            JSONArray unusualRewards = new JSONArray();
            JSONArray furnitureRewards = new JSONArray();
            JSONArray firstRewards = new JSONArray();
            JSONArray rewards = new JSONArray();

            // 处理基础奖励
            int expGain = stage_table.getIntValue("expGain");
            int goldGain = stage_table.getIntValue("goldGain");

            if (completeState == 3) {
                expGain = (int)(expGain * 1.2);
                goldGain = (int)(goldGain * 1.2);
                result.put("goldScale", 1.2);
                result.put("expScale", 1.2);
            } else {
                result.put("goldScale", 1);
                result.put("expScale", 1);
            }

            // 发放金币
            if (goldGain != 0) {
                UserSyncData.getJSONObject("status").put("gold",
                        UserSyncData.getJSONObject("status").getIntValue("gold") + goldGain);

                JSONObject goldReward = new JSONObject(true);
                goldReward.put("count", goldGain);
                goldReward.put("id", 4001);
                goldReward.put("type", "GOLD");
                rewards.add(goldReward);
            }

            // 发放经验
            if (expGain != 0) {
                handleExpReward(UserSyncData, expGain);
            }

            // 处理掉落奖励
            JSONArray displayDetailRewards = stage_table.getJSONObject("stageDropInfo").getJSONArray("displayDetailRewards");

            for (int i = 0; i < displayDetailRewards.size(); i++) {
                JSONObject reward = displayDetailRewards.getJSONObject(i);
                int occPercent = reward.getIntValue("occPercent");
                int dropType = reward.getIntValue("dropType");
                int reward_count = 1 * DropRate;
                String reward_id = reward.getString("id");
                String reward_type = reward.getString("type");

                // 根据掉落类型和概率处理奖励
                handleDropReward(UserSyncData, occPercent, dropType, reward_count, reward_id, reward_type,
                        completeState, FirstClear, additionalRewards, unusualRewards,
                        furnitureRewards, firstRewards, rewards);
            }

            // 设置返回结果
            result.put("additionalRewards", additionalRewards);
            result.put("unusualRewards", unusualRewards);
            result.put("furnitureRewards", furnitureRewards);
            result.put("firstRewards", firstReward);
            result.put("rewards", rewards);

            return result;
        }

        private void handleExpReward(JSONObject UserSyncData, int expGain) {
            JSONArray playerExpMap = JSON.parseArray("[500,800,1240,1320,1400,1480,1560,1640,1720,1800,1880,1960,2040,2120,2200,2280,2360,2440,2520,2600,2680,2760,2840,2920,3000,3080,3160,3240,3350,3460,3570,3680,3790,3900,4200,4500,4800,5100,5400,5700,6000,6300,6600,6900,7200,7500,7800,8100,8400,8700,9000,9500,10000,10500,11000,11500,12000,12500,13000,13500,14000,14500,15000,15500,16000,17000,18000,19000,20000,21000,22000,23000,24000,25000,26000,27000,28000,29000,30000,31000,32000,33000,34000,35000,36000,37000,38000,39000,40000,41000,42000,43000,44000,45000,46000,47000,48000,49000,50000,51000,52000,54000,56000,58000,60000,62000,64000,66000,68000,70000,73000,76000,79000,82000,85000,88000,91000,94000,97000,100000]");
            JSONArray playerApMap = JSON.parseArray("[82,84,86,88,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,120,120,120,120,121,121,121,121,121,122,122,122,122,122,123,123,123,123,123,124,124,124,124,124,125,125,125,125,125,126,126,126,126,126,127,127,127,127,127,128,128,128,128,128,129,129,129,129,129,130,130,130,130,130,130,130,130,130,130,130,130,130,130,130,130,131,131,131,131,132,132,132,132,133,133,133,133,134,134,134,134,135,135,135,135]");

            int level = UserSyncData.getJSONObject("status").getIntValue("level");
            if (level >= 120) return;

            UserSyncData.getJSONObject("status").put("exp",
                    UserSyncData.getJSONObject("status").getIntValue("exp") + expGain);

            for (int i = 0; i < playerExpMap.size(); i++) {
                if (level == i + 1) {
                    if (playerExpMap.getIntValue(i) - UserSyncData.getJSONObject("status").getIntValue("exp") <= 0) {
                        if (i + 2 == 120) {
                            // 满级
                            UserSyncData.getJSONObject("status").put("level", 120);
                            UserSyncData.getJSONObject("status").put("exp", 0);
                            UserSyncData.getJSONObject("status").put("maxAp", playerApMap.getIntValue(i + 1));
                            UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("maxAp"));
                        } else {
                            // 升级
                            UserSyncData.getJSONObject("status").put("level", i + 2);
                            UserSyncData.getJSONObject("status").put("exp",
                                    UserSyncData.getJSONObject("status").getIntValue("exp") - playerExpMap.getIntValue(i));
                            UserSyncData.getJSONObject("status").put("maxAp", playerApMap.getIntValue(i + 1));
                            UserSyncData.getJSONObject("status").put("ap",
                                    UserSyncData.getJSONObject("status").getIntValue("ap") + UserSyncData.getJSONObject("status").getIntValue("maxAp"));
                        }
                        UserSyncData.getJSONObject("status").put("lastApAddTime", (int)(new Date().getTime() / 1000L));
                    }
                    break;
                }
            }
        }

        private void handleDropReward(JSONObject UserSyncData, int occPercent, int dropType, int reward_count,
                                      String reward_id, String reward_type, int completeState, boolean FirstClear,
                                      JSONArray additionalRewards, JSONArray unusualRewards,
                                      JSONArray furnitureRewards, JSONArray firstRewards, JSONArray rewards) {
            // 根据掉落类型和概率处理奖励
            switch (dropType) {
                case 2: // 普通掉落
                    handleNormalDrop(UserSyncData, occPercent, reward_count, reward_id, reward_type,
                            completeState, FirstClear, furnitureRewards, rewards);
                    break;
                case 3: // 稀有掉落
                    handleRareDrop(UserSyncData, occPercent, reward_count, reward_id, reward_type,
                            unusualRewards);
                    break;
                case 4: // 额外掉落
                    handleExtraDrop(UserSyncData, occPercent, reward_count, reward_id, reward_type,
                            additionalRewards);
                    break;
            }
        }

        private void handleNormalDrop(JSONObject UserSyncData, int occPercent, int reward_count,
                                      String reward_id, String reward_type, int completeState,
                                      boolean FirstClear, JSONArray furnitureRewards, JSONArray rewards) {
            JSONArray dropArray = new JSONArray();
            int cur = 0;

            // 根据概率初始化掉落数组
            switch (occPercent) {
                case 0: // 必定掉落
                    handleGuaranteedDrop(UserSyncData, reward_count, reward_id, reward_type, rewards);
                    break;
                case 1: // 80%概率
                    IntStream.range(0, 80).forEach(n -> dropArray.add(1));
                    IntStream.range(0, 20).forEach(n -> dropArray.add(0));
                    Collections.shuffle(dropArray);
                    cur = dropArray.getIntValue(new Random().nextInt(dropArray.size()));
                    if (cur == 1) {
                        grantReward(UserSyncData, reward_id, reward_type, reward_count, furnitureRewards, rewards);
                    }
                    break;
                case 2: // 50%概率
                    IntStream.range(0, 50).forEach(n -> dropArray.add(1));
                    IntStream.range(0, 50).forEach(n -> dropArray.add(0));
                    Collections.shuffle(dropArray);
                    cur = dropArray.getIntValue(new Random().nextInt(dropArray.size()));
                    if (cur == 1) {
                        grantReward(UserSyncData, reward_id, reward_type, reward_count, furnitureRewards, rewards);
                    }
                    break;
                case 3: // 15%概率
                    IntStream.range(0, 15).forEach(n -> dropArray.add(1));
                    IntStream.range(0, 85).forEach(n -> dropArray.add(0));
                    Collections.shuffle(dropArray);
                    cur = dropArray.getIntValue(new Random().nextInt(dropArray.size()));
                    if (cur == 1) {
                        grantReward(UserSyncData, reward_id, reward_type, reward_count, furnitureRewards, rewards);
                    }
                    break;
                case 4: // 10%概率
                    IntStream.range(0, 10).forEach(n -> dropArray.add(1));
                    IntStream.range(0, 90).forEach(n -> dropArray.add(0));
                    Collections.shuffle(dropArray);
                    cur = dropArray.getIntValue(new Random().nextInt(dropArray.size()));
                    if (cur == 1) {
                        grantReward(UserSyncData, reward_id, reward_type, reward_count, furnitureRewards, rewards);
                    }
                    break;
            }
        }

        private void handleGuaranteedDrop(JSONObject UserSyncData, int reward_count,
                                          String reward_id, String reward_type, JSONArray rewards) {
            JSONArray furnitureRewards = new JSONArray();
            if (furnitureRewards == null) {
                furnitureRewards = new JSONArray();
            }
            switch (reward_type) {
                case "MATERIAL":
                    UserSyncData.getJSONObject("inventory").put(reward_id,
                            UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                    break;
                case "CARD_EXP":
                    UserSyncData.getJSONObject("inventory").put(reward_id,
                            UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                    break;
                case "DIAMOND":
                    UserSyncData.getJSONObject("status").put("androidDiamond",
                            UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                    UserSyncData.getJSONObject("status").put("iosDiamond",
                            UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                    break;
                case "GOLD":
                    UserSyncData.getJSONObject("status").put("gold",
                            UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                    break;
                case "TKT_RECRUIT":
                    UserSyncData.getJSONObject("status").put("recruitLicense",
                            UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                    break;
                case "FURN":
                    if (!UserSyncData.getJSONObject("building").getJSONObject("furniture").containsKey(reward_id)) {
                        JSONObject furniture = new JSONObject(true);
                        furniture.put("count", 1);
                        furniture.put("inUse", 0);
                        UserSyncData.getJSONObject("building").getJSONObject("furniture").put(reward_id, furniture);
                    } else {
                        UserSyncData.getJSONObject("building").getJSONObject("furniture").getJSONObject(reward_id)
                                .put("count", UserSyncData.getJSONObject("building").getJSONObject("furniture").getJSONObject(reward_id).getIntValue("count") + 1);
                    }
                    break;
            }

            JSONObject reward = new JSONObject(true);
            reward.put("count", reward_count);
            reward.put("id", reward_id);
            reward.put("type", reward_type);

            if (!reward_type.equals("FURN")) {
                rewards.add(reward);
            } else {
                furnitureRewards.add(reward);
            }
        }

        private void grantReward(JSONObject UserSyncData, String reward_id, String reward_type,
                                 int reward_count, JSONArray furnitureRewards, JSONArray rewards) {
            switch (reward_type) {
                case "MATERIAL":
                    UserSyncData.getJSONObject("inventory").put(reward_id,
                            UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                    break;
                case "CARD_EXP":
                    UserSyncData.getJSONObject("inventory").put(reward_id,
                            UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                    break;
                case "DIAMOND":
                    UserSyncData.getJSONObject("status").put("androidDiamond",
                            UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                    UserSyncData.getJSONObject("status").put("iosDiamond",
                            UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                    break;
                case "GOLD":
                    UserSyncData.getJSONObject("status").put("gold",
                            UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                    break;
                case "TKT_RECRUIT":
                    UserSyncData.getJSONObject("status").put("recruitLicense",
                            UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                    break;
                case "FURN":
                    if (!UserSyncData.getJSONObject("building").getJSONObject("furniture").containsKey(reward_id)) {
                        JSONObject furniture = new JSONObject(true);
                        furniture.put("count", 1);
                        furniture.put("inUse", 0);
                        UserSyncData.getJSONObject("building").getJSONObject("furniture").put(reward_id, furniture);
                    } else {
                        UserSyncData.getJSONObject("building").getJSONObject("furniture").getJSONObject(reward_id)
                                .put("count", UserSyncData.getJSONObject("building").getJSONObject("furniture").getJSONObject(reward_id).getIntValue("count") + 1);
                    }
                    break;
            }

            JSONObject reward = new JSONObject(true);
            reward.put("count", reward_count);
            reward.put("id", reward_id);
            reward.put("type", reward_type);

            if (!reward_type.equals("FURN")) {
                rewards.add(reward);
            } else {
                furnitureRewards.add(reward);
            }
        }

        private void handleRareDrop(JSONObject UserSyncData, int occPercent, int reward_count,
                                    String reward_id, String reward_type, JSONArray unusualRewards) {
            JSONArray dropArray = new JSONArray();
            int cur = 0;

            switch (occPercent) {
                case 0: // 必定掉落
                    grantReward(UserSyncData, reward_id, reward_type, reward_count, unusualRewards);
                    break;
                case 3: // 5%概率
                    IntStream.range(0, 5).forEach(n -> dropArray.add(1));
                    IntStream.range(0, 95).forEach(n -> dropArray.add(0));
                    Collections.shuffle(dropArray);
                    cur = dropArray.getIntValue(new Random().nextInt(dropArray.size()));
                    if (cur == 1) {
                        grantReward(UserSyncData, reward_id, reward_type, reward_count, unusualRewards);
                    }
                    break;
                case 4: // 5%概率
                    IntStream.range(0, 5).forEach(n -> dropArray.add(1));
                    IntStream.range(0, 95).forEach(n -> dropArray.add(0));
                    Collections.shuffle(dropArray);
                    cur = dropArray.getIntValue(new Random().nextInt(dropArray.size()));
                    if (cur == 1) {
                        grantReward(UserSyncData, reward_id, reward_type, reward_count, unusualRewards);
                    }
                    break;
            }
        }

        private void grantReward(JSONObject UserSyncData, String reward_id, String reward_type,
                                 int reward_count, JSONArray unusualRewards) {
            switch (reward_type) {
                case "MATERIAL":
                    UserSyncData.getJSONObject("inventory").put(reward_id,
                            UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                    break;
                case "CARD_EXP":
                    UserSyncData.getJSONObject("inventory").put(reward_id,
                            UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                    break;
                case "DIAMOND":
                    UserSyncData.getJSONObject("status").put("androidDiamond",
                            UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                    UserSyncData.getJSONObject("status").put("iosDiamond",
                            UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                    break;
                case "GOLD":
                    UserSyncData.getJSONObject("status").put("gold",
                            UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                    break;
                case "TKT_RECRUIT":
                    UserSyncData.getJSONObject("status").put("recruitLicense",
                            UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                    break;
            }

            JSONObject reward = new JSONObject(true);
            reward.put("count", reward_count);
            reward.put("id", reward_id);
            reward.put("type", reward_type);
            unusualRewards.add(reward);
        }

        private void handleExtraDrop(JSONObject UserSyncData, int occPercent, int reward_count,
                                     String reward_id, String reward_type, JSONArray additionalRewards) {
            JSONArray dropArray = new JSONArray();
            int cur = 0;

            switch (occPercent) {
                case 3: // 5%概率
                    IntStream.range(0, 5).forEach(n -> dropArray.add(1));
                    IntStream.range(0, 95).forEach(n -> dropArray.add(0));
                    Collections.shuffle(dropArray);
                    cur = dropArray.getIntValue(new Random().nextInt(dropArray.size()));
                    if (cur == 1) {
                        grantReward(UserSyncData, reward_id, reward_type, reward_count, additionalRewards);
                    }
                    break;
                case 4: // 25%概率
                    IntStream.range(0, 25).forEach(n -> dropArray.add(1));
                    IntStream.range(0, 75).forEach(n -> dropArray.add(0));
                    Collections.shuffle(dropArray);
                    cur = dropArray.getIntValue(new Random().nextInt(dropArray.size()));
                    if (cur == 1) {
                        grantReward(UserSyncData, reward_id, reward_type, reward_count, additionalRewards);
                    }
                    break;
            }
        }


        private void updateOperatorFavor(JSONObject UserSyncData, JSONObject BattleData,
                                         JSONObject stage_table, int completeState) {
            int completeFavor = stage_table.getIntValue("completeFavor");
            int passFavor = stage_table.getIntValue("passFavor");
            JSONObject charList = BattleData.getJSONObject("battleData").getJSONObject("stats").getJSONObject("charList");

            for (Map.Entry<String, Object> entry : charList.entrySet()) {
                String charInstId = entry.getKey();
                if (!UserSyncData.getJSONObject("troop").getJSONObject("chars").containsKey(charInstId)) {
                    continue;
                }

                JSONObject charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(charInstId);
                String charId = charData.getString("charId");
                int currentFavor = charData.getIntValue("favorPoint");

                if (completeState != 3 && completeState != 4) {
                    // 非三星通关
                    charData.put("favorPoint", currentFavor + passFavor);
                    if (UserSyncData.getJSONObject("troop").getJSONObject("charGroup").containsKey(charId)) {
                        UserSyncData.getJSONObject("troop").getJSONObject("charGroup").getJSONObject(charId)
                                .put("favorPoint", currentFavor + passFavor);
                    }
                } else {
                    // 三星通关
                    charData.put("favorPoint", currentFavor + completeFavor);
                    if (UserSyncData.getJSONObject("troop").getJSONObject("charGroup").containsKey(charId)) {
                        UserSyncData.getJSONObject("troop").getJSONObject("charGroup").getJSONObject(charId)
                                .put("favorPoint", currentFavor + completeFavor);
                    }
                }
            }
        }

        private JSONObject buildPlayerDataDelta(JSONObject UserSyncData, JSONArray unlockStagesObject,
                                                String stageId, JSONObject troop) {
            JSONObject playerDataDelta = new JSONObject(true);
            JSONObject modified = new JSONObject(true);

            // 构建关卡数据
            JSONObject dungeon = new JSONObject(true);
            JSONObject stages = new JSONObject(true);

            for (int i = 0; i < unlockStagesObject.size(); i++) {
                String unlockedStageId = unlockStagesObject.getJSONObject(i).getString("stageId");
                stages.put(unlockedStageId, UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(unlockedStageId));
            }

            stages.put(stageId, UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId));
            dungeon.put("stages", stages);

            modified.put("dungeon", dungeon);
            modified.put("status", UserSyncData.getJSONObject("status"));
            modified.put("troop", troop);
            modified.put("inventory", UserSyncData.getJSONObject("inventory"));

            playerDataDelta.put("deleted", new JSONObject(true));
            playerDataDelta.put("modified", modified);

            return playerDataDelta;
        }

    @PostMapping(
            value = {"/squadFormation"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject SquadFormation(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /quest/squadFormation");
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String squadId = JsonBody.getString("squadId");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            } else {
                Long uid = ((Account)Accounts.get(0)).getUid();
                JSONObject UserSyncData;
                if (((Account)Accounts.get(0)).getBan() == 1L) {
                    response.setStatus(500);
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("statusCode", 403);
                    UserSyncData.put("error", "Bad Request");
                    UserSyncData.put("message", "error");
                    return UserSyncData;
                } else {
                    UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                    UserSyncData.getJSONObject("troop").getJSONObject("squads").getJSONObject(squadId).put("slots", JsonBody.getJSONArray("slots"));
                    userDao.setUserData(uid, UserSyncData);
                    JSONObject result = new JSONObject(true);
                    JSONObject playerDataDelta = new JSONObject(true);
                    JSONObject modified = new JSONObject(true);
                    JSONObject troop = new JSONObject(true);
                    JSONObject squads = new JSONObject(true);
                    JSONObject squad = UserSyncData.getJSONObject("troop").getJSONObject("squads").getJSONObject(squadId);
                    squads.put(squadId, squad);
                    troop.put("squads", squads);
                    modified.put("troop", troop);
                    playerDataDelta.put("modified", modified);
                    playerDataDelta.put("deleted", new JSONObject(true));
                    result.put("playerDataDelta", playerDataDelta);
                    return result;
                }
            }
        }
    }

    @PostMapping(
            value = {"/saveBattleReplay"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject SaveBattleReplay(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /quest/saveBattleReplay");
        JSONObject BattleData;
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            BattleData = new JSONObject(true);
            BattleData.put("statusCode", 400);
            BattleData.put("error", "Bad Request");
            BattleData.put("message", "server is close");
            return BattleData;
        } else {
            BattleData = Utils.BattleReplay_decrypt(JsonBody.getString("battleReplay"));
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            } else {
                Long uid = ((Account)Accounts.get(0)).getUid();
                JSONObject UserSyncData;
                if (((Account)Accounts.get(0)).getBan() == 1L) {
                    response.setStatus(500);
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("statusCode", 403);
                    UserSyncData.put("error", "Bad Request");
                    UserSyncData.put("message", "error");
                    return UserSyncData;
                } else {
                    UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                    String stageId = BattleData.getJSONObject("journal").getJSONObject("metadata").getString("stageId");
                    JSONObject stages_data = UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId);
                    stages_data.put("hasBattleReplay", 1);
                    stages_data.put("battleReplay", JsonBody.getString("battleReplay"));
                    userDao.setUserData(uid, UserSyncData);
                    JSONObject result = new JSONObject(true);
                    JSONObject playerDataDelta = new JSONObject(true);
                    JSONObject modified = new JSONObject(true);
                    JSONObject dungeon = new JSONObject(true);
                    JSONObject stages = new JSONObject(true);
                    stages.put(stageId, UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId));
                    dungeon.put("stages", stages);
                    modified.put("dungeon", dungeon);
                    playerDataDelta.put("deleted", new JSONObject(true));
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    return result;
                }
            }
        }
    }

    @PostMapping(
            value = {"/getBattleReplay"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject GetBattleReplay(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /quest/getBattleReplay");
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String stageId = JsonBody.getString("stageId");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            } else {
                Long uid = ((Account)Accounts.get(0)).getUid();
                JSONObject UserSyncData;
                if (((Account)Accounts.get(0)).getBan() == 1L) {
                    response.setStatus(500);
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("statusCode", 403);
                    UserSyncData.put("error", "Bad Request");
                    UserSyncData.put("message", "error");
                    return UserSyncData;
                } else {
                    UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                    JSONObject result = new JSONObject(true);
                    JSONObject playerDataDelta = new JSONObject(true);
                    JSONObject modified = new JSONObject(true);
                    playerDataDelta.put("deleted", new JSONObject(true));
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    result.put("battleReplay", UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getString("battleReplay"));
                    return result;
                }
            }
        }
    }

    @PostMapping(
            value = {"/changeSquadName"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject ChangeSquadName(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /quest/changeSquadName");
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String squadId = JsonBody.getString("squadId");
            String name = JsonBody.getString("name");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            } else {
                Long uid = ((Account)Accounts.get(0)).getUid();
                JSONObject UserSyncData;
                if (((Account)Accounts.get(0)).getBan() == 1L) {
                    response.setStatus(500);
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("statusCode", 403);
                    UserSyncData.put("error", "Bad Request");
                    UserSyncData.put("message", "error");
                    return UserSyncData;
                } else {
                    UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                    UserSyncData.getJSONObject("troop").getJSONObject("squads").getJSONObject(squadId).put("name", name);
                    userDao.setUserData(uid, UserSyncData);
                    JSONObject result = new JSONObject(true);
                    JSONObject playerDataDelta = new JSONObject(true);
                    JSONObject modified = new JSONObject(true);
                    JSONObject troop = new JSONObject(true);
                    JSONObject squads = new JSONObject(true);
                    JSONObject squad = UserSyncData.getJSONObject("troop").getJSONObject("squads").getJSONObject(squadId);
                    squads.put(squadId, squad);
                    troop.put("squads", squads);
                    modified.put("troop", troop);
                    playerDataDelta.put("modified", modified);
                    playerDataDelta.put("deleted", new JSONObject(true));
                    result.put("playerDataDelta", playerDataDelta);
                    return result;
                }
            }
        }
    }

    @PostMapping(
            value = {"/getAssistList"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getAssistList(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /quest/getAssistList");
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String profession = JsonBody.getString("profession");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            JSONObject result;
            if (Accounts.size() != 1) {
                result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            } else if (((Account)Accounts.get(0)).getBan() == 1L) {
                response.setStatus(500);
                result = new JSONObject(true);
                result.put("statusCode", 403);
                result.put("error", "Bad Request");
                result.put("message", "error");
                return result;
            } else {
                JSONArray assistCharArray = new JSONArray();
                JSONArray assistList = new JSONArray();
                long uid = ((Account)Accounts.get(0)).getUid();
                JSONArray FriendList = JSONObject.parseObject(((Account)Accounts.get(0)).getFriend()).getJSONArray("list");
                JSONArray FriendArray = new JSONArray();
                Collections.shuffle(FriendList);

                JSONArray charList;
                JSONObject userStatus;
                JSONObject chars;
                JSONObject assistCharData;
                JSONObject charData;
                for(int i = 0; i < FriendList.size() && assistList.size() != 6; ++i) {
                    long friendUid = FriendList.getJSONObject(i).getLongValue("uid");
                    String friendAlias = FriendList.getJSONObject(i).getString("alias");
                    FriendArray.add(friendUid);
                    List<UserInfo> userInfo = userDao.queryUserInfo(friendUid);
                    charList = JSONArray.parseArray(((UserInfo)userInfo.get(0)).getSocialAssistCharList());
                    userStatus = JSONObject.parseObject(((UserInfo)userInfo.get(0)).getAssistCharList());
                    chars = JSONObject.parseObject(((UserInfo)userInfo.get(0)).getStatus());
                    assistCharData = JSONObject.parseObject(((UserInfo)userInfo.get(0)).getChars());
                    if (userStatus.containsKey(profession)) {
                        charList = userStatus.getJSONArray(profession);
                        Collections.shuffle(charList);
                        assistCharData = charList.getJSONObject(0);
                        String charId = assistCharData.getString("charId");
                        String charInstId = assistCharData.getString("charInstId");
                        if (!assistCharArray.contains(charId)) {
                            assistCharArray.add(charId);
                            JSONArray assistCharList = new JSONArray();
                            charData = new JSONObject();
                            charData.put("aliasName", friendAlias);
                            charData.put("avatarId", chars.getIntValue("avatarId"));
                            charData.put("avatar", chars.getJSONObject("avatar"));
                            charData.put("canRequestFriend", false);
                            charData.put("isFriend", true);
                            charData.put("lastOnlineTime", chars.getIntValue("lastOnlineTs"));
                            charData.put("level", chars.getIntValue("level"));
                            charData.put("nickName", chars.getString("nickName"));
                            charData.put("nickNumber", chars.getString("nickNumber"));
                            charData.put("uid", friendUid);
                            charData.put("powerScore", 140);

                            for(int m = 0; m < charList.size(); ++m) {
                                if (charList.getJSONObject(m) != null) {
                                    charData = assistCharData.getJSONObject(charList.getJSONObject(m).getString("charInstId"));
                                    charData.put("skillIndex", charList.getJSONObject(m).getIntValue("skillIndex"));
                                    assistCharList.add(charData);
                                    if (charList.getJSONObject(m).getString("charInstId").equals(charInstId)) {
                                        charData.put("assistSlotIndex", m);
                                    }
                                }
                            }

                            charData.put("assistCharList", assistCharList);
                            assistList.add(charData);
                        }
                    }
                }

                List<SearchAssistCharList> searchAssist = userDao.SearchAssistCharList("$." + profession);

                int i;
                for(i = 0; i < searchAssist.size(); ++i) {
                    if (((SearchAssistCharList)searchAssist.get(i)).getUid() == uid) {
                        ((SearchAssistCharList)searchAssist.get(i)).setUid(-1L);
                    }

                    if (FriendArray.contains(((SearchAssistCharList)searchAssist.get(i)).getUid())) {
                        ((SearchAssistCharList)searchAssist.get(i)).setUid(-1L);
                    }
                }

                Collections.shuffle(searchAssist);

                for(i = 0; i < searchAssist.size(); ++i) {
                    long friendUid = ((SearchAssistCharList)searchAssist.get(i)).getUid();
                    if (friendUid != -1L) {
                        if (assistList.size() == 9) {
                            break;
                        }

                        JSONArray userSocialAssistCharList = JSONArray.parseArray(((SearchAssistCharList)searchAssist.get(i)).getSocialAssistCharList());
                        charList = JSONArray.parseArray(((SearchAssistCharList)searchAssist.get(i)).getAssistCharList());
                        userStatus = JSONObject.parseObject(((SearchAssistCharList)searchAssist.get(i)).getStatus());
                        chars = JSONObject.parseObject(((SearchAssistCharList)searchAssist.get(i)).getChars());
                        Collections.shuffle(charList);
                        assistCharData = charList.getJSONObject(0);
                        String charId = assistCharData.getString("charId");
                        String charInstId = assistCharData.getString("charInstId");
                        if (!assistCharArray.contains(charId)) {
                            assistCharArray.add(charId);
                            JSONArray assistCharList = new JSONArray();
                            JSONObject assistInfo = new JSONObject();
                            assistInfo.put("aliasName", "");
                            assistInfo.put("avatarId", userStatus.getIntValue("avatarId"));
                            assistInfo.put("avatar", userStatus.getJSONObject("avatar"));
                            assistInfo.put("canRequestFriend", true);
                            assistInfo.put("isFriend", false);
                            assistInfo.put("lastOnlineTime", userStatus.getIntValue("lastOnlineTs"));
                            assistInfo.put("level", userStatus.getIntValue("level"));
                            assistInfo.put("nickName", userStatus.getString("nickName"));
                            assistInfo.put("nickNumber", userStatus.getString("nickNumber"));
                            assistInfo.put("uid", friendUid);
                            assistInfo.put("powerScore", 140);

                            for(int m = 0; m < userSocialAssistCharList.size(); ++m) {
                                if (userSocialAssistCharList.getJSONObject(m) != null) {
                                    charData = chars.getJSONObject(userSocialAssistCharList.getJSONObject(m).getString("charInstId"));
                                    charData.put("skillIndex", userSocialAssistCharList.getJSONObject(m).getIntValue("skillIndex"));
                                    assistCharList.add(charData);
                                    if (userSocialAssistCharList.getJSONObject(m).getString("charInstId").equals(charInstId)) {
                                        assistInfo.put("assistSlotIndex", m);
                                    }
                                }
                            }

                            assistInfo.put("assistCharList", assistCharList);
                            assistList.add(assistInfo);
                        }
                    }
                }

                result = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                JSONObject modified = new JSONObject(true);
                playerDataDelta.put("modified", modified);
                playerDataDelta.put("deleted", new JSONObject(true));
                result.put("playerDataDelta", playerDataDelta);
                result.put("allowAskTs", 1636483552);
                result.put("assistList", assistList);
                return result;
            }
        }
    }

    @PostMapping(
            value = {"/finishStoryStage"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject finishStoryStage(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /quest/finishStoryStage");
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
            String stageId = JsonBody.getString("stageId");
            List<Account> Accounts = userDao.queryAccountBySecret(secret);
            if (Accounts.size() != 1) {
                JSONObject result = new JSONObject(true);
                result.put("result", 2);
                result.put("error", "无法查询到此账户");
                return result;
            } else {
                Long uid = ((Account)Accounts.get(0)).getUid();
                JSONObject UserSyncData;
                if (((Account)Accounts.get(0)).getBan() == 1L) {
                    response.setStatus(500);
                    UserSyncData = new JSONObject(true);
                    UserSyncData.put("statusCode", 403);
                    UserSyncData.put("error", "Bad Request");
                    UserSyncData.put("message", "error");
                    return UserSyncData;
                } else {
                    UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                    int stage_state = UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getIntValue("state");
                    JSONObject stageClear = ArknightsApplication.mainStage.getJSONObject(stageId);
                    JSONArray rewards = new JSONArray();
                    JSONArray unlockStages = new JSONArray();
                    JSONArray unlockStagesObject = new JSONArray();
                    JSONArray alert = new JSONArray();
                    int DropRate = ArknightsApplication.serverConfig.getJSONObject("battle").getIntValue("dropRate");
                    JSONObject hard_unlockStage;
                    JSONObject reward;
                    if (stage_state != 3) {
                        UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("state", 3);
                        String hard;
                        if (stageClear.getString("next") != null) {
                            hard = stageClear.getString("next");
                            hard_unlockStage = new JSONObject(true);
                            hard_unlockStage.put("hasBattleReplay", 0);
                            hard_unlockStage.put("noCostCnt", 0);
                            hard_unlockStage.put("practiceTimes", 0);
                            hard_unlockStage.put("completeTimes", 0);
                            hard_unlockStage.put("state", 0);
                            hard_unlockStage.put("stageId", hard);
                            hard_unlockStage.put("startTimes", 0);
                            UserSyncData.getJSONObject("dungeon").getJSONObject("stages").put(hard, hard_unlockStage);
                            unlockStages.add(hard);
                            unlockStagesObject.add(hard_unlockStage);
                        }

                        if (stageClear.getString("sub") != null) {
                            hard = stageClear.getString("sub");
                            hard_unlockStage = new JSONObject(true);
                            hard_unlockStage.put("hasBattleReplay", 0);
                            hard_unlockStage.put("noCostCnt", 0);
                            hard_unlockStage.put("practiceTimes", 0);
                            hard_unlockStage.put("completeTimes", 0);
                            hard_unlockStage.put("state", 0);
                            hard_unlockStage.put("stageId", hard);
                            hard_unlockStage.put("startTimes", 0);
                            UserSyncData.getJSONObject("dungeon").getJSONObject("stages").put(hard, hard_unlockStage);
                            unlockStages.add(hard);
                            unlockStagesObject.add(hard_unlockStage);
                        }

                        if (stageClear.getString("star") != null) {
                            hard = stageClear.getString("star");
                            hard_unlockStage = new JSONObject(true);
                            hard_unlockStage.put("hasBattleReplay", 0);
                            hard_unlockStage.put("noCostCnt", 0);
                            hard_unlockStage.put("practiceTimes", 0);
                            hard_unlockStage.put("completeTimes", 0);
                            hard_unlockStage.put("state", 0);
                            hard_unlockStage.put("stageId", hard);
                            hard_unlockStage.put("startTimes", 0);
                            UserSyncData.getJSONObject("dungeon").getJSONObject("stages").put(hard, hard_unlockStage);
                            unlockStages.add(hard);
                            unlockStagesObject.add(hard_unlockStage);
                        }

                        if (stageClear.getString("hard") != null) {
                            hard = stageClear.getString("hard");
                            hard_unlockStage = new JSONObject(true);
                            hard_unlockStage.put("hasBattleReplay", 0);
                            hard_unlockStage.put("noCostCnt", 0);
                            hard_unlockStage.put("practiceTimes", 0);
                            hard_unlockStage.put("completeTimes", 0);
                            hard_unlockStage.put("state", 0);
                            hard_unlockStage.put("stageId", hard);
                            hard_unlockStage.put("startTimes", 0);
                            UserSyncData.getJSONObject("dungeon").getJSONObject("stages").put(hard, hard_unlockStage);
                            unlockStages.add(hard);
                            unlockStagesObject.add(hard_unlockStage);
                        }

                        reward = new JSONObject(true);
                        reward.put("type", "DIAMOND");
                        reward.put("id", "4002");
                        reward.put("count", 1 * DropRate);
                        rewards.add(reward);
                        UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + 1 * DropRate);
                        UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + 1 * DropRate);
                    }

                    reward = new JSONObject(true);
                    hard_unlockStage = new JSONObject(true);
                    JSONObject modified = new JSONObject(true);
                    JSONObject status = new JSONObject(true);
                    status.put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond"));
                    status.put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond"));
                    JSONObject dungeon = new JSONObject(true);
                    JSONObject stages = new JSONObject(true);

                    for(int i = 0; i < unlockStagesObject.size(); ++i) {
                        String unlock_stageId = unlockStagesObject.getJSONObject(i).getString("stageId");
                        stages.put(unlock_stageId, UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(unlock_stageId));
                    }

                    stages.put(stageId, UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId));
                    dungeon.put("stages", stages);
                    modified.put("status", status);
                    modified.put("dungeon", dungeon);
                    hard_unlockStage.put("deleted", new JSONObject(true));
                    hard_unlockStage.put("modified", modified);
                    reward.put("playerDataDelta", hard_unlockStage);
                    reward.put("rewards", rewards);
                    reward.put("unlockStages", unlockStages);
                    reward.put("alert", alert);
                    reward.put("result", 0);
                    userDao.setUserData(uid, UserSyncData);
                    return reward;
                }
            }
        }
    }
}
