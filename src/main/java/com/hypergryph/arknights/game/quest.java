package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArKnightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.decrypt.Utils;
import com.hypergryph.arknights.core.pojo.Account;
import com.hypergryph.arknights.core.pojo.SearchAssistCharList;
import com.hypergryph.arknights.core.pojo.UserInfo;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        String clientIp = ArKnightsApplication.getIpAddr(request);
        String secret = ArKnightsApplication.getSecretByIP(clientIp);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /quest/battleStart");
        if (!ArKnightsApplication.enableServer) {
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
                    JSONObject stage_table = ArKnightsApplication.stageTable.getJSONObject(stageId);
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

    @PostMapping(
            value = {"/battleFinish"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject BattleFinish(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        String secret = ArKnightsApplication.getSecretByIP(clientIp);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /quest/battleFinish");
        if (!ArKnightsApplication.enableServer) {
            response.setStatus(400);
            JSONObject result = new JSONObject(true);
            result.put("statusCode", 400);
            result.put("error", "Bad Request");
            result.put("message", "server is close");
            return result;
        } else {
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
                    JSONObject BattleData = Utils.BattleData_decrypt(JsonBody.getString("data"), UserSyncData.getJSONObject("pushFlags").getString("status"));
                    String stageId = BattleData.getString("battleId");
                    JSONObject stage_table = ArKnightsApplication.stageTable.getJSONObject(stageId);
                    JSONObject stageClear = new JSONObject();
                    if (ArKnightsApplication.mainStage.containsKey(stageId)) {
                        stageClear = ArKnightsApplication.mainStage.getJSONObject(stageId);
                    } else {
                        stageClear.put("next", (Object)null);
                        stageClear.put("star", (Object)null);
                        stageClear.put("sub", (Object)null);
                        stageClear.put("hard", (Object)null);
                    }

                    JSONObject chars;
                    JSONObject troop;
                    JSONObject result;
                    if (UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getIntValue("practiceTimes") == 1) {
                        if (UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getIntValue("state") == 0) {
                            UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("state", 1);
                        }

                        UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("practiceTimes", 0);
                        userDao.setUserData(uid, UserSyncData);
                        chars = new JSONObject(true);
                        troop = new JSONObject(true);
                        result = new JSONObject(true);
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
                    } else {
                        chars = UserSyncData.getJSONObject("troop").getJSONObject("chars");
                        troop = new JSONObject(true);
                        result = new JSONObject(true);
                        int DropRate = ArKnightsApplication.serverConfig.getJSONObject("battle").getIntValue("dropRate");
                        int completeState = BattleData.getIntValue("completeState");
                        if (ArKnightsApplication.serverConfig.getJSONObject("battle").getBooleanValue("debug")) {
                            completeState = 3;
                        }

                        int apCost = stage_table.getIntValue("apCost");
                        int expGain = stage_table.getIntValue("expGain");
                        int goldGain = stage_table.getIntValue("goldGain");
                        result.put("goldScale", 1);
                        result.put("expScale", 1);
                        if (completeState == 3) {
                            expGain = (int)((double)expGain * 1.2);
                            goldGain = (int)((double)goldGain * 1.2);
                            result.put("goldScale", 1.2);
                            result.put("expScale", 1.2);
                        }

                        int nowTime = (int)((new Date()).getTime() / 1000L);
                        int addAp = (nowTime - UserSyncData.getJSONObject("status").getIntValue("lastApAddTime")) / 360;
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
                        if (completeState == 1) {
                            int apFailReturn = stage_table.getIntValue("apFailReturn");
                            if (UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getIntValue("noCostCnt") == 1) {
                                apFailReturn = stage_table.getIntValue("apCost");
                                UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("noCostCnt", 0);
                            }

                            nowTime = (int)((new Date()).getTime() / 1000L);
                            addAp = (UserSyncData.getJSONObject("status").getIntValue("lastApAddTime") - nowTime) / 360;
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
                            JSONObject playerDataDelta = new JSONObject(true);
                            JSONObject modified = new JSONObject(true);
                            result.put("additionalRewards", new JSONArray());
                            result.put("alert", new JSONArray());
                            result.put("firstRewards", new JSONArray());
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
                        } else {
                            if (UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getIntValue("state") == 0) {
                                UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("state", 1);
                            }

                            JSONObject stages_data = UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId);
                            JSONArray unlockStages = new JSONArray();
                            JSONArray unlockStagesObject = new JSONArray();
                            JSONArray additionalRewards = new JSONArray();
                            JSONArray unusualRewards = new JSONArray();
                            JSONArray furnitureRewards = new JSONArray();
                            JSONArray firstRewards = new JSONArray();
                            JSONArray rewards = new JSONArray();
                            result.put("result", 0);
                            result.put("alert", new JSONArray());
                            result.put("suggestFriend", false);
                            result.put("apFailReturn", 0);
                            Boolean FirstClear = false;
                            if (stages_data.getIntValue("state") != 3 && completeState == 3) {
                                FirstClear = true;
                            }

                            if (stages_data.getIntValue("state") == 3 && completeState == 4) {
                                FirstClear = true;
                            }

                            String reward_type;
                            int completeFavor;
                            JSONObject charList;
                            JSONObject char_data;
                            JSONObject dungeon;
                            if (stages_data.getIntValue("state") == 1 && (completeState == 3 || completeState == 2)) {
                                if (stageId.equals("main_08-16")) {
                                    Iterator var32 = UserSyncData.getJSONObject("troop").getJSONObject("chars").entrySet().iterator();

                                    while(var32.hasNext()) {
                                        Map.Entry entry = (Map.Entry)var32.next();
                                        JSONObject charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(entry.getKey().toString());
                                        String charId = charData.getString("charId");
                                        if (charId.equals("char_002_amiya")) {
                                            JSONArray amiya_skills = charData.getJSONArray("skills");
                                            reward_type = charData.getString("skin");
                                            completeFavor = charData.getIntValue("defaultSkillIndex");
                                            charData.put("skin", (Object)null);
                                            charData.put("defaultSkillIndex", -1);
                                            charData.put("skills", new JSONArray());
                                            charData.put("currentTmpl", "char_1001_amiya2");
                                            JSONObject tmpl = new JSONObject(true);
                                            charList = new JSONObject(true);
                                            charList.put("skinId", reward_type);
                                            charList.put("defaultSkillIndex", completeFavor);
                                            charList.put("skills", amiya_skills);
                                            charList.put("currentEquip", (Object)null);
                                            charList.put("equip", new JSONObject(true));
                                            tmpl.put("char_002_amiya", charList);
                                            JSONArray sword_amiya_skills = new JSONArray();
                                            char_data = new JSONObject(true);
                                            char_data.put("skillId", "skchr_amiya2_1");
                                            char_data.put("unlock", 1);
                                            char_data.put("state", 0);
                                            char_data.put("specializeLevel", 0);
                                            char_data.put("completeUpgradeTime", -1);
                                            sword_amiya_skills.add(char_data);
                                            dungeon = new JSONObject(true);
                                            dungeon.put("skillId", "skchr_amiya2_1");
                                            dungeon.put("unlock", 1);
                                            dungeon.put("state", 0);
                                            dungeon.put("specializeLevel", 0);
                                            dungeon.put("completeUpgradeTime", -1);
                                            sword_amiya_skills.add(dungeon);
                                            charData = new JSONObject(true);
                                            charData.put("skinId", "char_1001_amiya2#2");
                                            charData.put("defaultSkillIndex", 0);
                                            charData.put("skills", sword_amiya_skills);
                                            charData.put("currentEquip", (Object)null);
                                            charData.put("equip", new JSONObject(true));
                                            tmpl.put("char_1001_amiya2", charData);
                                            charData.put("tmpl", tmpl);
                                            JSONObject charinstId = new JSONObject(true);
                                            charinstId.put(entry.getKey().toString(), charData);
                                            troop.put("chars", charinstId);
                                            UserSyncData.getJSONObject("troop").getJSONObject("chars").put(entry.getKey().toString(), charData);
                                            break;
                                        }
                                    }
                                }

                                String hard;
                                JSONObject hard_unlockStage;
                                if (stageClear.getString("next") != null) {
                                    hard = stageClear.getString("next");
                                    hard_unlockStage = new JSONObject(true);
                                    hard_unlockStage.put("hasBattleReplay", 0);
                                    hard_unlockStage.put("noCostCnt", 1);
                                    hard_unlockStage.put("practiceTimes", 0);
                                    hard_unlockStage.put("completeTimes", 0);
                                    hard_unlockStage.put("state", 0);
                                    hard_unlockStage.put("stageId", hard);
                                    hard_unlockStage.put("startTimes", 0);
                                    UserSyncData.getJSONObject("dungeon").getJSONObject("stages").put(hard, hard_unlockStage);
                                    if (stage_table.getString("stageType").equals("MAIN") || stage_table.getString("stageType").equals("SUB")) {
                                        UserSyncData.getJSONObject("status").put("mainStageProgress", hard);
                                    }

                                    unlockStages.add(hard);
                                    unlockStagesObject.add(hard_unlockStage);
                                }

                                if (stageClear.getString("sub") != null) {
                                    hard = stageClear.getString("sub");
                                    hard_unlockStage = new JSONObject(true);
                                    hard_unlockStage.put("hasBattleReplay", 0);
                                    hard_unlockStage.put("noCostCnt", 1);
                                    hard_unlockStage.put("practiceTimes", 0);
                                    hard_unlockStage.put("completeTimes", 0);
                                    hard_unlockStage.put("state", 0);
                                    hard_unlockStage.put("stageId", hard);
                                    hard_unlockStage.put("startTimes", 0);
                                    UserSyncData.getJSONObject("dungeon").getJSONObject("stages").put(hard, hard_unlockStage);
                                    unlockStages.add(hard);
                                    unlockStagesObject.add(hard_unlockStage);
                                }

                                if (completeState == 3) {
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
                                }

                                result.put("unlockStages", unlockStages);
                            }

                            JSONObject normal_reward;
                            JSONArray playerExpMap;
                            int gold;
                            int dropType;
                            int reward_count;
                            int reward_rarity;
                            JSONObject get_char;
                            int instId;
                            String itemName;
                            String itemType;
                            int cur;
                            if (FirstClear) {
                                playerExpMap = stage_table.getJSONObject("stageDropInfo").getJSONArray("displayDetailRewards");

                                for(int i = 0; i < playerExpMap.size(); ++i) {
                                    gold = playerExpMap.getJSONObject(i).getIntValue("dropType");
                                    reward_count = 1 * DropRate;
                                    String reward_id = playerExpMap.getJSONObject(i).getString("id");
                                    reward_type = playerExpMap.getJSONObject(i).getString("type");
                                    if (gold == 1 || gold == 8) {
                                        JSONObject charGet;
                                        if (!reward_type.equals("CHAR")) {
                                            if (reward_type.equals("MATERIAL")) {
                                                UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                            }

                                            if (reward_type.equals("CARD_EXP")) {
                                                UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                            }

                                            if (reward_type.equals("DIAMOND")) {
                                                UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                                                UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                                            }

                                            if (reward_type.equals("GOLD")) {
                                                UserSyncData.getJSONObject("status").put("gold", UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                                            }

                                            if (reward_type.equals("TKT_RECRUIT")) {
                                                UserSyncData.getJSONObject("status").put("recruitLicense", UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                                            }

                                            if (reward_type.equals("FURN")) {
                                                if (!UserSyncData.getJSONObject("building").getJSONObject("furniture").containsKey(reward_id)) {
                                                    charGet = new JSONObject(true);
                                                    charGet.put("count", 1);
                                                    charGet.put("inUse", 0);
                                                    UserSyncData.getJSONObject("building").getJSONObject("furniture").put(reward_id, charGet);
                                                }

                                                UserSyncData.getJSONObject("building").getJSONObject("furniture").getJSONObject(reward_id).put("count", UserSyncData.getJSONObject("building").getJSONObject("furniture").getJSONObject(reward_id).getIntValue("count") + 1);
                                            }

                                            charGet = new JSONObject(true);
                                            charGet.put("count", reward_count);
                                            charGet.put("id", reward_id);
                                            charGet.put("type", reward_type);
                                            firstRewards.add(charGet);
                                        } else {
                                            new JSONObject(true);
                                            String randomCharId = reward_id;
                                            dropType = 0;

                                            for(reward_count = 0; reward_count < UserSyncData.getJSONObject("troop").getJSONObject("chars").size(); ++reward_count) {
                                                if (UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(reward_count + 1)).getString("charId").equals(randomCharId)) {
                                                    dropType = reward_count + 1;
                                                    break;
                                                }
                                            }

                                            String itemId;
                                            JSONObject new_itemGet_1;
                                            JSONObject new_itemGet_3;
                                            JSONArray itemGet;
                                            if (dropType == 0) {
                                                get_char = new JSONObject(true);
                                                char_data = new JSONObject(true);
                                                JSONArray skilsArray = ArKnightsApplication.characterJson.getJSONObject(randomCharId).getJSONArray("skills");
                                                JSONArray skils = new JSONArray();

                                                for(instId = 0; instId < skilsArray.size(); ++instId) {
                                                    JSONObject new_skils = new JSONObject(true);
                                                    new_skils.put("skillId", skilsArray.getJSONObject(instId).getString("skillId"));
                                                    new_skils.put("state", 0);
                                                    new_skils.put("specializeLevel", 0);
                                                    new_skils.put("completeUpgradeTime", -1);
                                                    if (skilsArray.getJSONObject(instId).getJSONObject("unlockCond").getIntValue("phase") == 0) {
                                                        new_skils.put("unlock", 1);
                                                    } else {
                                                        new_skils.put("unlock", 0);
                                                    }

                                                    skils.add(new_skils);
                                                }

                                                instId = UserSyncData.getJSONObject("troop").getJSONObject("chars").size() + 1;
                                                char_data.put("instId", instId);
                                                char_data.put("charId", randomCharId);
                                                char_data.put("favorPoint", 0);
                                                char_data.put("potentialRank", 0);
                                                char_data.put("mainSkillLvl", 1);
                                                char_data.put("skin", randomCharId + "#1");
                                                char_data.put("level", 1);
                                                char_data.put("exp", 0);
                                                char_data.put("evolvePhase", 0);
                                                char_data.put("gainTime", (new Date()).getTime() / 1000L);
                                                char_data.put("skills", skils);
                                                char_data.put("voiceLan", ArKnightsApplication.charwordTable.getJSONObject("charDefaultTypeDict").getString(randomCharId));
                                                if (skils == new JSONArray()) {
                                                    char_data.put("defaultSkillIndex", -1);
                                                } else {
                                                    char_data.put("defaultSkillIndex", 0);
                                                }

                                                itemType = randomCharId.substring(randomCharId.indexOf("_") + 1);
                                                itemId = itemType.substring(itemType.indexOf("_") + 1);
                                                JSONObject charGroup;
                                                if (ArKnightsApplication.uniequipTable.containsKey("uniequip_001_" + itemId)) {
                                                    charGroup = new JSONObject(true);
                                                    normal_reward = new JSONObject(true);
                                                    normal_reward.put("hide", 0);
                                                    normal_reward.put("locked", 0);
                                                    normal_reward.put("level", 1);
                                                    new_itemGet_1 = new JSONObject(true);
                                                    new_itemGet_1.put("hide", 0);
                                                    new_itemGet_1.put("locked", 0);
                                                    new_itemGet_1.put("level", 1);
                                                    charGroup.put("uniequip_001_" + itemId, normal_reward);
                                                    charGroup.put("uniequip_002_" + itemId, new_itemGet_1);
                                                    char_data.put("equip", charGroup);
                                                    char_data.put("currentEquip", "uniequip_001_" + itemId);
                                                } else {
                                                    char_data.put("currentEquip", (Object)null);
                                                }

                                                UserSyncData.getJSONObject("troop").getJSONObject("chars").put(String.valueOf(instId), char_data);
                                                charGroup = new JSONObject(true);
                                                charGroup.put("favorPoint", 0);
                                                UserSyncData.getJSONObject("troop").getJSONObject("charGroup").put(randomCharId, charGroup);
                                                get_char.put("charInstId", instId);
                                                get_char.put("charId", randomCharId);
                                                get_char.put("isNew", 1);
                                                itemGet = new JSONArray();
                                                new_itemGet_1 = new JSONObject(true);
                                                new_itemGet_1.put("type", "HGG_SHD");
                                                new_itemGet_1.put("id", "4004");
                                                new_itemGet_1.put("count", 1);
                                                itemGet.add(new_itemGet_1);
                                                UserSyncData.getJSONObject("status").put("hggShard", UserSyncData.getJSONObject("status").getIntValue("hggShard") + 1);
                                                get_char.put("itemGet", itemGet);
                                                UserSyncData.getJSONObject("inventory").put("p_" + randomCharId, 0);
                                                charGet = get_char;
                                                new_itemGet_3 = new JSONObject(true);
                                                new_itemGet_3.put(String.valueOf(instId), char_data);
                                                chars.put(String.valueOf(instId), char_data);
                                                troop.put("chars", new_itemGet_3);
                                            } else {
                                                get_char = new JSONObject(true);
                                                get_char.put("charInstId", dropType);
                                                get_char.put("charId", randomCharId);
                                                get_char.put("isNew", 0);
                                                char_data = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(dropType));
                                                int potentialRank = char_data.getIntValue("potentialRank");
                                                reward_rarity = ArKnightsApplication.characterJson.getJSONObject(randomCharId).getIntValue("rarity");
                                                itemName = null;
                                                itemType = null;
                                                itemId = null;
                                                cur = 0;
                                                if (reward_rarity == 0) {
                                                    itemName = "lggShard";
                                                    itemType = "LGG_SHD";
                                                    itemId = "4005";
                                                    cur = 1;
                                                }

                                                if (reward_rarity == 1) {
                                                    itemName = "lggShard";
                                                    itemType = "LGG_SHD";
                                                    itemId = "4005";
                                                    cur = 1;
                                                }

                                                if (reward_rarity == 2) {
                                                    itemName = "lggShard";
                                                    itemType = "LGG_SHD";
                                                    itemId = "4005";
                                                    cur = 5;
                                                }

                                                if (reward_rarity == 3) {
                                                    itemName = "lggShard";
                                                    itemType = "LGG_SHD";
                                                    itemId = "4005";
                                                    cur = 30;
                                                }

                                                if (reward_rarity == 4) {
                                                    itemName = "hggShard";
                                                    itemType = "HGG_SHD";
                                                    itemId = "4004";
                                                    if (potentialRank != 5) {
                                                        cur = 5;
                                                    } else {
                                                        cur = 8;
                                                    }
                                                }

                                                if (reward_rarity == 5) {
                                                    itemName = "hggShard";
                                                    itemType = "HGG_SHD";
                                                    itemId = "4004";
                                                    if (potentialRank != 5) {
                                                        cur = 10;
                                                    } else {
                                                        cur = 15;
                                                    }
                                                }

                                                itemGet = new JSONArray();
                                                new_itemGet_1 = new JSONObject(true);
                                                new_itemGet_1.put("type", itemType);
                                                new_itemGet_1.put("id", itemId);
                                                new_itemGet_1.put("count", cur);
                                                itemGet.add(new_itemGet_1);
                                                UserSyncData.getJSONObject("status").put(itemName, UserSyncData.getJSONObject("status").getIntValue(itemName) + cur);
                                                new_itemGet_3 = new JSONObject(true);
                                                new_itemGet_3.put("type", "MATERIAL");
                                                new_itemGet_3.put("id", "p_" + randomCharId);
                                                new_itemGet_3.put("count", 1);
                                                itemGet.add(new_itemGet_3);
                                                get_char.put("itemGet", itemGet);
                                                UserSyncData.getJSONObject("inventory").put("p_" + randomCharId, UserSyncData.getJSONObject("inventory").getIntValue("p_" + randomCharId) + 1);
                                                charGet = get_char;
                                                JSONObject charinstId = new JSONObject(true);
                                                charinstId.put(String.valueOf(dropType), UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(dropType)));
                                                chars.put(String.valueOf(dropType), UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(dropType)));
                                                troop.put("chars", charinstId);
                                            }

                                            get_char = new JSONObject(true);
                                            get_char.put("count", 1);
                                            get_char.put("id", reward_id);
                                            get_char.put("type", reward_type);
                                            get_char.put("charGet", charGet);
                                            firstRewards.add(get_char);
                                        }
                                    }
                                }
                            }

                            result.put("firstRewards", firstRewards);
                            if (UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).getIntValue("state") != 3) {
                                UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("state", completeState);
                            }

                            if (completeState == 4) {
                                UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("state", completeState);
                            }

                            UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId).put("completeTime", BattleData.getJSONObject("battleData").getIntValue("completeTime"));
                            playerExpMap = JSON.parseArray("[500,800,1240,1320,1400,1480,1560,1640,1720,1800,1880,1960,2040,2120,2200,2280,2360,2440,2520,2600,2680,2760,2840,2920,3000,3080,3160,3240,3350,3460,3570,3680,3790,3900,4200,4500,4800,5100,5400,5700,6000,6300,6600,6900,7200,7500,7800,8100,8400,8700,9000,9500,10000,10500,11000,11500,12000,12500,13000,13500,14000,14500,15000,15500,16000,17000,18000,19000,20000,21000,22000,23000,24000,25000,26000,27000,28000,29000,30000,31000,32000,33000,34000,35000,36000,37000,38000,39000,40000,41000,42000,43000,44000,45000,46000,47000,48000,49000,50000,51000,52000,54000,56000,58000,60000,62000,64000,66000,68000,70000,73000,76000,79000,82000,85000,88000,91000,94000,97000,100000]");
                            JSONArray playerApMap = JSON.parseArray("[82,84,86,88,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,120,120,120,120,121,121,121,121,121,122,122,122,122,122,123,123,123,123,123,124,124,124,124,124,125,125,125,125,125,126,126,126,126,126,127,127,127,127,127,128,128,128,128,128,129,129,129,129,129,130,130,130,130,130,130,130,130,130,130,130,130,130,130,130,130,131,131,131,131,132,132,132,132,133,133,133,133,134,134,134,134,135,135,135,135]");
                            gold = UserSyncData.getJSONObject("status").getIntValue("gold");
                            reward_count = UserSyncData.getJSONObject("status").getIntValue("exp");
                            int level = UserSyncData.getJSONObject("status").getIntValue("level");
                            if (goldGain != 0) {
                                UserSyncData.getJSONObject("status").put("gold", gold + goldGain);
                                JSONObject rewards_gold = new JSONObject(true);
                                rewards_gold.put("count", goldGain);
                                rewards_gold.put("id", 4001);
                                rewards_gold.put("type", "GOLD");
                                rewards.add(rewards_gold);
                            }

                            if (level < 120 && expGain != 0) {
                                UserSyncData.getJSONObject("status").put("exp", reward_count + expGain);

                                for(int i = 0; i < playerExpMap.size(); ++i) {
                                    if (level == i + 1) {
                                        if (Integer.valueOf(playerExpMap.get(i).toString()) - UserSyncData.getJSONObject("status").getIntValue("exp") <= 0) {
                                            if (i + 2 == 120) {
                                                UserSyncData.getJSONObject("status").put("level", 120);
                                                UserSyncData.getJSONObject("status").put("exp", 0);
                                                UserSyncData.getJSONObject("status").put("maxAp", playerApMap.get(i + 1));
                                                UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("ap") + UserSyncData.getJSONObject("status").getIntValue("maxAp"));
                                            } else {
                                                UserSyncData.getJSONObject("status").put("level", i + 2);
                                                UserSyncData.getJSONObject("status").put("exp", UserSyncData.getJSONObject("status").getIntValue("exp") - Integer.valueOf(playerExpMap.get(i).toString()));
                                                UserSyncData.getJSONObject("status").put("maxAp", playerApMap.get(i + 1));
                                                UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("ap") + UserSyncData.getJSONObject("status").getIntValue("maxAp"));
                                            }

                                            UserSyncData.getJSONObject("status").put("lastApAddTime", (new Date()).getTime() / 1000L);
                                        }
                                        break;
                                    }
                                }
                            }

                            JSONArray displayDetailRewards = stage_table.getJSONObject("stageDropInfo").getJSONArray("displayDetailRewards");

                            int occPercent;
                            int addPercent;
                            for(completeFavor = 0; completeFavor < displayDetailRewards.size(); ++completeFavor) {
                                occPercent = displayDetailRewards.getJSONObject(completeFavor).getIntValue("occPercent");
                                dropType = displayDetailRewards.getJSONObject(completeFavor).getIntValue("dropType");
                                reward_count = 1 * DropRate;
                                String reward_id = displayDetailRewards.getJSONObject(completeFavor).getString("id");
                                reward_type = displayDetailRewards.getJSONObject(completeFavor).getString("type");
                                reward_rarity = 0;
                                int Percent = 0;
                                addPercent = 0;
                                JSONArray dropArray;
                                if (completeState == 3 && !reward_type.equals("FURN") && !reward_type.equals("CHAR")) {
                                    reward_rarity = ArKnightsApplication.itemTable.getJSONObject(reward_id).getIntValue("rarity");
                                    if (reward_rarity == 0) {
                                        dropArray = new JSONArray();
                                        JSONArray finalDropArray = dropArray;
                                        IntStream.range(0, 70).forEach((n) -> {
                                            finalDropArray.add(0);
                                        });
                                        JSONArray finalDropArray1 = dropArray;
                                        IntStream.range(0, 20).forEach((n) -> {
                                            finalDropArray1.add(1);
                                        });
                                        JSONArray finalDropArray2 = dropArray;
                                        IntStream.range(0, 10).forEach((n) -> {
                                            finalDropArray2.add(2);
                                        });
                                        Collections.shuffle(dropArray);
                                        cur = dropArray.getIntValue((new Random()).nextInt(dropArray.size()));
                                        reward_count += cur;
                                        Percent = 10;
                                        addPercent = 0;
                                    }

                                    if (reward_rarity == 1) {
                                        dropArray = new JSONArray();
                                        JSONArray finalDropArray3 = dropArray;
                                        IntStream.range(0, 70).forEach((n) -> {
                                            finalDropArray3.add(0);
                                        });
                                        JSONArray finalDropArray4 = dropArray;
                                        IntStream.range(0, 10).forEach((n) -> {
                                            finalDropArray4.add(1);
                                        });
                                        JSONArray finalDropArray5 = dropArray;
                                        IntStream.range(0, 5).forEach((n) -> {
                                            finalDropArray5.add(2);
                                        });
                                        Collections.shuffle(dropArray);
                                        cur = dropArray.getIntValue((new Random()).nextInt(dropArray.size()));
                                        reward_count += cur;
                                        Percent = 5;
                                        addPercent = 0;
                                    }

                                    if (reward_rarity == 2) {
                                        Percent = 0;
                                        addPercent = 110;
                                    }

                                    if (reward_rarity == 3) {
                                        Percent = 0;
                                        addPercent = 120;
                                    }

                                    if (reward_rarity == 4) {
                                        Percent = 0;
                                        addPercent = 130;
                                    }
                                }

                                if (completeState == 2 && !reward_type.equals("FURN") && !reward_type.equals("CHAR")) {
                                    reward_rarity = ArKnightsApplication.itemTable.getJSONObject(reward_id).getIntValue("rarity");
                                    if (reward_rarity == 0) {
                                        dropArray = new JSONArray();
                                        JSONArray finalDropArray6 = dropArray;
                                        IntStream.range(0, 90 + Percent).forEach((n) -> {
                                            finalDropArray6.add(0);
                                        });
                                        JSONArray finalDropArray7 = dropArray;
                                        IntStream.range(0, 12 + Percent).forEach((n) -> {
                                            finalDropArray7.add(1);
                                        });
                                        JSONArray finalDropArray8 = dropArray;
                                        IntStream.range(0, 8 + addPercent).forEach((n) -> {
                                            finalDropArray8.add(2);
                                        });
                                        Collections.shuffle(dropArray);
                                        cur = dropArray.getIntValue((new Random()).nextInt(dropArray.size()));
                                        reward_count += cur;
                                        Percent = 0;
                                        addPercent = 0;
                                    }

                                    if (reward_rarity == 1) {
                                        dropArray = new JSONArray();
                                        JSONArray finalDropArray9 = dropArray;
                                        IntStream.range(0, 110 + Percent).forEach((n) -> {
                                            finalDropArray9.add(0);
                                        });
                                        JSONArray finalDropArray10 = dropArray;
                                        IntStream.range(0, 8 + Percent).forEach((n) -> {
                                            finalDropArray10.add(1);
                                        });
                                        JSONArray finalDropArray11 = dropArray;
                                        IntStream.range(0, 2 + addPercent).forEach((n) -> {
                                            finalDropArray11.add(2);
                                        });
                                        Collections.shuffle(dropArray);
                                        cur = dropArray.getIntValue((new Random()).nextInt(dropArray.size()));
                                        reward_count += cur;
                                        Percent = 0;
                                        addPercent = 0;
                                    }

                                    if (reward_rarity == 2) {
                                        Percent = 0;
                                        addPercent = 120;
                                    }

                                    if (reward_rarity == 3) {
                                        Percent = 0;
                                        addPercent = 140;
                                    }

                                    if (reward_rarity == 4) {
                                        Percent = 0;
                                        addPercent = 160;
                                    }
                                }

                                if (occPercent == 0 && dropType == 2) {
                                    if (reward_type.equals("MATERIAL")) {
                                        if (stageId.equals("wk_toxic_1")) {
                                            if (completeState == 3) {
                                                reward_count = 4;
                                            } else {
                                                reward_count = 3;
                                            }
                                        }

                                        if (stageId.equals("wk_toxic_2")) {
                                            if (completeState == 3) {
                                                reward_count = 7;
                                            } else {
                                                reward_count = 3;
                                            }
                                        }

                                        if (stageId.equals("wk_toxic_3")) {
                                            if (completeState == 3) {
                                                reward_count = 11;
                                            } else {
                                                reward_count = 6;
                                            }
                                        }

                                        if (stageId.equals("wk_toxic_4")) {
                                            if (completeState == 3) {
                                                reward_count = 15;
                                            } else {
                                                reward_count = 7;
                                            }
                                        }

                                        if (stageId.equals("wk_toxic_5")) {
                                            if (completeState == 3) {
                                                reward_count = 21;
                                            } else {
                                                reward_count = 8;
                                            }
                                        }

                                        if (stageId.equals("wk_fly_1")) {
                                            if (completeState == 3) {
                                                reward_count = 3;
                                            } else {
                                                reward_count = 1;
                                            }
                                        }

                                        if (stageId.equals("wk_fly_2")) {
                                            if (completeState == 3) {
                                                reward_count = 5;
                                            } else {
                                                reward_count = 3;
                                            }
                                        }

                                        if (stageId.equals("wk_fly_3")) {
                                            if (completeState == 3) {
                                                if (reward_rarity == 1) {
                                                    reward_count = 1;
                                                }

                                                if (reward_rarity == 2) {
                                                    reward_count = 3;
                                                }
                                            } else {
                                                if (reward_rarity == 1) {
                                                    reward_count = 1;
                                                }

                                                if (reward_rarity == 2) {
                                                    reward_count = 1;
                                                }
                                            }
                                        }

                                        if (stageId.equals("wk_fly_4")) {
                                            if (completeState == 3) {
                                                if (reward_rarity == 1) {
                                                    reward_count = 1;
                                                }

                                                if (reward_rarity == 2) {
                                                    reward_count = 1;
                                                }

                                                if (reward_rarity == 3) {
                                                    reward_count = 2;
                                                }
                                            } else {
                                                if (reward_rarity == 1) {
                                                    reward_count = 1;
                                                }

                                                if (reward_rarity == 2) {
                                                    reward_count = 1;
                                                }

                                                if (reward_rarity == 3) {
                                                    reward_count = 1;
                                                }
                                            }
                                        }

                                        if (stageId.equals("wk_fly_5")) {
                                            if (completeState == 3) {
                                                if (reward_rarity == 1) {
                                                    reward_count = 1;
                                                }

                                                if (reward_rarity == 2) {
                                                    reward_count = 2;
                                                }

                                                if (reward_rarity == 3) {
                                                    reward_count = 3;
                                                }
                                            } else {
                                                if (reward_rarity == 1) {
                                                    reward_count = 1;
                                                }

                                                if (reward_rarity == 2) {
                                                    reward_count = 1;
                                                }

                                                if (reward_rarity == 3) {
                                                    reward_count = 2;
                                                }
                                            }
                                        }

                                        UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                    }

                                    if (reward_type.equals("CARD_EXP")) {
                                        UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                    }

                                    if (reward_type.equals("DIAMOND")) {
                                        UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                                        UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                                    }

                                    if (reward_type.equals("TKT_RECRUIT")) {
                                        UserSyncData.getJSONObject("status").put("recruitLicense", UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                                    }

                                    if (reward_type.equals("GOLD")) {
                                        if (stageId.equals("main_01-01")) {
                                            if (completeState == 3) {
                                                reward_count = 660;
                                            } else {
                                                reward_count = 550;
                                            }
                                        }

                                        if (stageId.equals("main_02-07")) {
                                            if (completeState == 3) {
                                                reward_count = 1500;
                                            } else {
                                                reward_count = 1250;
                                            }
                                        }

                                        if (stageId.equals("main_03-06")) {
                                            if (completeState == 3) {
                                                reward_count = 2040;
                                            } else {
                                                reward_count = 1700;
                                            }
                                        }

                                        if (stageId.equals("main_04-01")) {
                                            if (completeState == 3) {
                                                reward_count = 2700;
                                            } else {
                                                reward_count = 2250;
                                            }
                                        }

                                        if (stageId.equals("main_06-01")) {
                                            if (completeState == 3) {
                                                reward_count = 1216;
                                            } else {
                                                reward_count = 1013;
                                            }
                                        }

                                        if (stageId.equals("main_07-02")) {
                                            if (completeState == 3) {
                                                reward_count = 1216;
                                            } else {
                                                reward_count = 1013;
                                            }
                                        }

                                        if (stageId.equals("main_08-01")) {
                                            if (completeState == 3) {
                                                reward_count = 2700;
                                            } else {
                                                reward_count = 2250;
                                            }
                                        }

                                        if (stageId.equals("main_08-04")) {
                                            if (completeState == 3) {
                                                reward_count = 1216;
                                            } else {
                                                reward_count = 1013;
                                            }
                                        }

                                        if (stageId.equals("main_09-01")) {
                                            if (completeState == 3) {
                                                reward_count = 2700;
                                            } else {
                                                reward_count = 2250;
                                            }
                                        }

                                        if (stageId.equals("main_09-02")) {
                                            if (completeState == 3) {
                                                reward_count = 1216;
                                            } else {
                                                reward_count = 1013;
                                            }
                                        }

                                        if (stageId.equals("sub_02-02")) {
                                            if (completeState == 3) {
                                                reward_count = 1020;
                                            } else {
                                                reward_count = 850;
                                            }
                                        }

                                        if (stageId.equals("sub_04-2-3")) {
                                            if (completeState == 3) {
                                                reward_count = 3480;
                                            } else {
                                                reward_count = 2900;
                                            }
                                        }

                                        if (stageId.equals("sub_05-1-2")) {
                                            if (completeState == 3) {
                                                reward_count = 2700;
                                            } else {
                                                reward_count = 2250;
                                            }
                                        }

                                        if (stageId.equals("sub_05-2-1")) {
                                            if (completeState == 3) {
                                                reward_count = 1216;
                                            } else {
                                                reward_count = 1013;
                                            }
                                        }

                                        if (stageId.equals("sub_05-3-1")) {
                                            if (completeState == 3) {
                                                reward_count = 1216;
                                            } else {
                                                reward_count = 1013;
                                            }
                                        }

                                        if (stageId.equals("sub_06-1-2")) {
                                            if (completeState == 3) {
                                                reward_count = 1216;
                                            } else {
                                                reward_count = 1013;
                                            }
                                        }

                                        if (stageId.equals("sub_06-2-2")) {
                                            if (completeState == 3) {
                                                reward_count = 2700;
                                            } else {
                                                reward_count = 2250;
                                            }
                                        }

                                        if (stageId.equals("sub_07-1-1")) {
                                            if (completeState == 3) {
                                                reward_count = 2700;
                                            } else {
                                                reward_count = 2250;
                                            }
                                        }

                                        if (stageId.equals("sub_07-1-2")) {
                                            if (completeState == 3) {
                                                reward_count = 1216;
                                            } else {
                                                reward_count = 1013;
                                            }
                                        }

                                        if (stageId.equals("wk_melee_1")) {
                                            if (completeState == 3) {
                                                reward_count = 1700;
                                            } else {
                                                reward_count = 1416;
                                            }
                                        }

                                        if (stageId.equals("wk_melee_2")) {
                                            if (completeState == 3) {
                                                reward_count = 2800;
                                            } else {
                                                reward_count = 2333;
                                            }
                                        }

                                        if (stageId.equals("wk_melee_3")) {
                                            if (completeState == 3) {
                                                reward_count = 4100;
                                            } else {
                                                reward_count = 3416;
                                            }
                                        }

                                        if (stageId.equals("wk_melee_4")) {
                                            if (completeState == 3) {
                                                reward_count = 5700;
                                            } else {
                                                reward_count = 4750;
                                            }
                                        }

                                        if (stageId.equals("wk_melee_5")) {
                                            if (completeState == 3) {
                                                reward_count = 7500;
                                            } else {
                                                reward_count = 6250;
                                            }
                                        }

                                        UserSyncData.getJSONObject("status").put("gold", UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                                    }

                                    normal_reward = new JSONObject(true);
                                    normal_reward.put("count", reward_count);
                                    normal_reward.put("id", reward_id);
                                    normal_reward.put("type", reward_type);
                                    rewards.add(normal_reward);
                                }

                                if (occPercent == 1 && dropType == 2) {
                                    dropArray = new JSONArray();
                                    JSONArray finalDropArray12 = dropArray;
                                    IntStream.range(0, 80 + Percent).forEach((n) -> {
                                        finalDropArray12.add(1);
                                    });
                                    JSONArray finalDropArray13 = dropArray;
                                    IntStream.range(0, 20 + addPercent).forEach((n) -> {
                                        finalDropArray13.add(0);
                                    });
                                    Collections.shuffle(dropArray);
                                    cur = dropArray.getIntValue((new Random()).nextInt(dropArray.size()));
                                    if (cur == 1) {
                                        if (reward_type.equals("MATERIAL")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("CARD_EXP")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("DIAMOND")) {
                                            UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                                            UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                                        }

                                        if (reward_type.equals("GOLD")) {
                                            UserSyncData.getJSONObject("status").put("gold", UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                                        }

                                        if (reward_type.equals("TKT_RECRUIT")) {
                                            UserSyncData.getJSONObject("status").put("recruitLicense", UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                                        }

                                        normal_reward = new JSONObject(true);
                                        normal_reward.put("count", reward_count);
                                        normal_reward.put("id", reward_id);
                                        normal_reward.put("type", reward_type);
                                        rewards.add(normal_reward);
                                    }
                                }

                                if (occPercent == 2 && dropType == 2) {
                                    if (stageId.indexOf("pro_") != -1) {
                                        dropArray = new JSONArray();
                                        JSONArray finalDropArray14 = dropArray;
                                        IntStream.range(0, 5).forEach((n) -> {
                                            finalDropArray14.add(1);
                                        });
                                        JSONArray finalDropArray15 = dropArray;
                                        IntStream.range(0, 5).forEach((n) -> {
                                            finalDropArray15.add(0);
                                        });
                                        Collections.shuffle(dropArray);
                                        cur = dropArray.getIntValue((new Random()).nextInt(dropArray.size()));
                                        reward_id = displayDetailRewards.getJSONObject(cur).getString("id");
                                        reward_type = displayDetailRewards.getJSONObject(cur).getString("type");
                                        if (reward_type.equals("MATERIAL")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        normal_reward = new JSONObject(true);
                                        normal_reward.put("count", reward_count);
                                        normal_reward.put("id", reward_id);
                                        normal_reward.put("type", reward_type);
                                        rewards.add(normal_reward);
                                        break;
                                    }

                                    dropArray = new JSONArray();
                                    JSONArray finalDropArray16 = dropArray;
                                    IntStream.range(0, 50 + Percent).forEach((n) -> {
                                        finalDropArray16.add(1);
                                    });
                                    JSONArray finalDropArray17 = dropArray;
                                    IntStream.range(0, 50 + addPercent).forEach((n) -> {
                                        finalDropArray17.add(0);
                                    });
                                    Collections.shuffle(dropArray);
                                    cur = dropArray.getIntValue((new Random()).nextInt(dropArray.size()));
                                    if (cur == 1) {
                                        if (reward_type.equals("MATERIAL")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("CARD_EXP")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("DIAMOND")) {
                                            UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                                            UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                                        }

                                        if (reward_type.equals("GOLD")) {
                                            UserSyncData.getJSONObject("status").put("gold", UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                                        }

                                        if (reward_type.equals("TKT_RECRUIT")) {
                                            UserSyncData.getJSONObject("status").put("recruitLicense", UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                                        }

                                        normal_reward = new JSONObject(true);
                                        normal_reward.put("count", reward_count);
                                        normal_reward.put("id", reward_id);
                                        normal_reward.put("type", reward_type);
                                        rewards.add(normal_reward);
                                    }
                                }

                                if (occPercent == 3 && dropType == 2) {
                                    dropArray = new JSONArray();
                                    JSONArray finalDropArray18 = dropArray;
                                    IntStream.range(0, 15 + Percent).forEach((n) -> {
                                        finalDropArray18.add(1);
                                    });
                                    JSONArray finalDropArray19 = dropArray;
                                    IntStream.range(0, 90 + addPercent).forEach((n) -> {
                                        finalDropArray19.add(0);
                                    });
                                    Collections.shuffle(dropArray);
                                    cur = dropArray.getIntValue((new Random()).nextInt(dropArray.size()));
                                    if (cur == 1) {
                                        if (reward_type.equals("MATERIAL")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("CARD_EXP")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("DIAMOND")) {
                                            UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                                            UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                                        }

                                        if (reward_type.equals("GOLD")) {
                                            UserSyncData.getJSONObject("status").put("gold", UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                                        }

                                        if (reward_type.equals("TKT_RECRUIT")) {
                                            UserSyncData.getJSONObject("status").put("recruitLicense", UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                                        }

                                        if (reward_type.equals("FURN")) {
                                            if (!UserSyncData.getJSONObject("building").getJSONObject("furniture").containsKey(reward_id)) {
                                                normal_reward = new JSONObject(true);
                                                normal_reward.put("count", 1);
                                                normal_reward.put("inUse", 0);
                                                UserSyncData.getJSONObject("building").getJSONObject("furniture").put(reward_id, normal_reward);
                                            }

                                            UserSyncData.getJSONObject("building").getJSONObject("furniture").getJSONObject(reward_id).put("count", UserSyncData.getJSONObject("building").getJSONObject("furniture").getJSONObject(reward_id).getIntValue("count") + 1);
                                        }

                                        normal_reward = new JSONObject(true);
                                        normal_reward.put("count", reward_count);
                                        normal_reward.put("id", reward_id);
                                        normal_reward.put("type", reward_type);
                                        if (!reward_type.equals("FURN")) {
                                            rewards.add(normal_reward);
                                        } else {
                                            furnitureRewards.add(normal_reward);
                                        }
                                    }
                                }

                                if (occPercent == 4 && dropType == 2) {
                                    dropArray = new JSONArray();
                                    JSONArray finalDropArray20 = dropArray;
                                    IntStream.range(0, 10 + Percent).forEach((n) -> {
                                        finalDropArray20.add(1);
                                    });
                                    JSONArray finalDropArray21 = dropArray;
                                    IntStream.range(0, 90 + addPercent).forEach((n) -> {
                                        finalDropArray21.add(0);
                                    });
                                    Collections.shuffle(dropArray);
                                    cur = dropArray.getIntValue((new Random()).nextInt(dropArray.size()));
                                    if (cur == 1) {
                                        if (reward_type.equals("MATERIAL")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("CARD_EXP")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("DIAMOND")) {
                                            UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                                            UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                                        }

                                        if (reward_type.equals("GOLD")) {
                                            UserSyncData.getJSONObject("status").put("gold", UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                                        }

                                        if (reward_type.equals("TKT_RECRUIT")) {
                                            UserSyncData.getJSONObject("status").put("recruitLicense", UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                                        }

                                        if (reward_type.equals("FURN")) {
                                            if (!UserSyncData.getJSONObject("building").getJSONObject("furniture").containsKey(reward_id)) {
                                                normal_reward = new JSONObject(true);
                                                normal_reward.put("count", 1);
                                                normal_reward.put("inUse", 0);
                                                UserSyncData.getJSONObject("building").getJSONObject("furniture").put(reward_id, normal_reward);
                                            }

                                            UserSyncData.getJSONObject("building").getJSONObject("furniture").getJSONObject(reward_id).put("count", UserSyncData.getJSONObject("building").getJSONObject("furniture").getJSONObject(reward_id).getIntValue("count") + 1);
                                        }

                                        normal_reward = new JSONObject(true);
                                        normal_reward.put("count", reward_count);
                                        normal_reward.put("id", reward_id);
                                        normal_reward.put("type", reward_type);
                                        if (!reward_type.equals("FURN")) {
                                            rewards.add(normal_reward);
                                        } else {
                                            furnitureRewards.add(normal_reward);
                                        }
                                    }
                                }

                                if (occPercent == 0 && dropType == 3) {
                                    if (reward_type.equals("MATERIAL")) {
                                        UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                    }

                                    if (reward_type.equals("CARD_EXP")) {
                                        UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                    }

                                    if (reward_type.equals("DIAMOND")) {
                                        UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                                        UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                                    }

                                    if (reward_type.equals("GOLD")) {
                                        UserSyncData.getJSONObject("status").put("gold", UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                                    }

                                    if (reward_type.equals("TKT_RECRUIT")) {
                                        UserSyncData.getJSONObject("status").put("recruitLicense", UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                                    }

                                    normal_reward = new JSONObject(true);
                                    normal_reward.put("count", reward_count);
                                    normal_reward.put("id", reward_id);
                                    normal_reward.put("type", reward_type);
                                    unusualRewards.add(normal_reward);
                                }

                                if (occPercent == 3 && dropType == 3) {
                                    dropArray = new JSONArray();
                                    JSONArray finalDropArray22 = dropArray;
                                    IntStream.range(0, 5 + Percent).forEach((n) -> {
                                        finalDropArray22.add(1);
                                    });
                                    JSONArray finalDropArray23 = dropArray;
                                    IntStream.range(0, 95 + addPercent).forEach((n) -> {
                                        finalDropArray23.add(0);
                                    });
                                    Collections.shuffle(dropArray);
                                    cur = dropArray.getIntValue((new Random()).nextInt(dropArray.size()));
                                    if (cur == 1) {
                                        if (reward_type.equals("MATERIAL")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("CARD_EXP")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("DIAMOND")) {
                                            UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                                            UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                                        }

                                        if (reward_type.equals("GOLD")) {
                                            UserSyncData.getJSONObject("status").put("gold", UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                                        }

                                        if (reward_type.equals("TKT_RECRUIT")) {
                                            UserSyncData.getJSONObject("status").put("recruitLicense", UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                                        }

                                        normal_reward = new JSONObject(true);
                                        normal_reward.put("count", reward_count);
                                        normal_reward.put("id", reward_id);
                                        normal_reward.put("type", reward_type);
                                        unusualRewards.add(normal_reward);
                                    }
                                }

                                if (occPercent == 4 && dropType == 3) {
                                    dropArray = new JSONArray();
                                    JSONArray finalDropArray24 = dropArray;
                                    IntStream.range(0, 5 + Percent).forEach((n) -> {
                                        finalDropArray24.add(1);
                                    });
                                    JSONArray finalDropArray25 = dropArray;
                                    IntStream.range(0, 95 + addPercent).forEach((n) -> {
                                        finalDropArray25.add(0);
                                    });
                                    Collections.shuffle(dropArray);
                                    cur = dropArray.getIntValue((new Random()).nextInt(dropArray.size()));
                                    if (cur == 1) {
                                        if (reward_type.equals("MATERIAL")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("CARD_EXP")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("DIAMOND")) {
                                            UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                                            UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                                        }

                                        if (reward_type.equals("GOLD")) {
                                            UserSyncData.getJSONObject("status").put("gold", UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                                        }

                                        if (reward_type.equals("TKT_RECRUIT")) {
                                            UserSyncData.getJSONObject("status").put("recruitLicense", UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                                        }

                                        normal_reward = new JSONObject(true);
                                        normal_reward.put("count", reward_count);
                                        normal_reward.put("id", reward_id);
                                        normal_reward.put("type", reward_type);
                                        unusualRewards.add(normal_reward);
                                    }
                                }

                                if (occPercent == 3 && dropType == 4) {
                                    dropArray = new JSONArray();
                                    JSONArray finalDropArray26 = dropArray;
                                    IntStream.range(0, 5 + Percent).forEach((n) -> {
                                        finalDropArray26.add(1);
                                    });
                                    JSONArray finalDropArray27 = dropArray;
                                    IntStream.range(0, 95 + addPercent).forEach((n) -> {
                                        finalDropArray27.add(0);
                                    });
                                    Collections.shuffle(dropArray);
                                    cur = dropArray.getIntValue((new Random()).nextInt(dropArray.size()));
                                    if (cur == 1) {
                                        if (reward_type.equals("MATERIAL")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("CARD_EXP")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("DIAMOND")) {
                                            UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                                            UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                                        }

                                        if (reward_type.equals("GOLD")) {
                                            UserSyncData.getJSONObject("status").put("gold", UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                                        }

                                        if (reward_type.equals("TKT_RECRUIT")) {
                                            UserSyncData.getJSONObject("status").put("recruitLicense", UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                                        }

                                        normal_reward = new JSONObject(true);
                                        normal_reward.put("count", reward_count);
                                        normal_reward.put("id", reward_id);
                                        normal_reward.put("type", reward_type);
                                        additionalRewards.add(normal_reward);
                                    }
                                }

                                if (occPercent == 4 && dropType == 4) {
                                    dropArray = new JSONArray();
                                    JSONArray finalDropArray28 = dropArray;
                                    IntStream.range(0, 25 + Percent).forEach((n) -> {
                                        finalDropArray28.add(1);
                                    });
                                    JSONArray finalDropArray29 = dropArray;
                                    IntStream.range(0, 75 + addPercent).forEach((n) -> {
                                        finalDropArray29.add(0);
                                    });
                                    Collections.shuffle(dropArray);
                                    cur = dropArray.getIntValue((new Random()).nextInt(dropArray.size()));
                                    if (cur == 1) {
                                        if (reward_type.equals("MATERIAL")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("CARD_EXP")) {
                                            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
                                        }

                                        if (reward_type.equals("DIAMOND")) {
                                            UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
                                            UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
                                        }

                                        if (reward_type.equals("GOLD")) {
                                            UserSyncData.getJSONObject("status").put("gold", UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
                                        }

                                        if (reward_type.equals("TKT_RECRUIT")) {
                                            UserSyncData.getJSONObject("status").put("recruitLicense", UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
                                        }

                                        normal_reward = new JSONObject(true);
                                        normal_reward.put("count", reward_count);
                                        normal_reward.put("id", reward_id);
                                        normal_reward.put("type", reward_type);
                                        additionalRewards.add(normal_reward);
                                    }
                                }
                            }

                            result.put("rewards", rewards);
                            result.put("additionalRewards", additionalRewards);
                            result.put("unusualRewards", unusualRewards);
                            result.put("furnitureRewards", furnitureRewards);
                            completeFavor = stage_table.getIntValue("completeFavor");
                            occPercent = stage_table.getIntValue("passFavor");
                            charList = BattleData.getJSONObject("battleData").getJSONObject("stats").getJSONObject("charList");
                            Iterator var96 = charList.entrySet().iterator();
                            JSONObject charData;

                            while(true) {
                                while(true) {
                                    do {
                                        if (!var96.hasNext()) {
                                            get_char = new JSONObject(true);
                                            char_data = new JSONObject(true);
                                            dungeon = new JSONObject(true);
                                            charData = new JSONObject(true);

                                            for(instId = 0; instId < unlockStagesObject.size(); ++instId) {
                                                itemType = unlockStagesObject.getJSONObject(instId).getString("stageId");
                                                charData.put(itemType, UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(itemType));
                                            }

                                            charData.put(stageId, UserSyncData.getJSONObject("dungeon").getJSONObject("stages").getJSONObject(stageId));
                                            dungeon.put("stages", charData);
                                            char_data.put("dungeon", dungeon);
                                            char_data.put("status", UserSyncData.getJSONObject("status"));
                                            char_data.put("troop", troop);
                                            char_data.put("inventory", UserSyncData.getJSONObject("inventory"));
                                            get_char.put("deleted", new JSONObject(true));
                                            get_char.put("modified", char_data);
                                            result.put("playerDataDelta", get_char);
                                            userDao.setUserData(uid, UserSyncData);
                                            return result;
                                        }

                                        Map.Entry<String, Object> entry = (Map.Entry)var96.next();
                                        reward_type = (String)entry.getKey();
                                    } while(!UserSyncData.getJSONObject("troop").getJSONObject("chars").containsKey(reward_type));

                                    charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(reward_type);
                                    itemName = charData.getString("charId");
                                    addPercent = charData.getIntValue("favorPoint");
                                    if (completeState != 3 && completeState != 4) {
                                        charData.put("favorPoint", addPercent + occPercent);
                                        if (UserSyncData.getJSONObject("troop").getJSONObject("charGroup").containsKey(itemName)) {
                                            UserSyncData.getJSONObject("troop").getJSONObject("charGroup").getJSONObject(itemName).put("favorPoint", addPercent + occPercent);
                                        }
                                    } else {
                                        charData.put("favorPoint", addPercent + completeFavor);
                                        if (UserSyncData.getJSONObject("troop").getJSONObject("charGroup").containsKey(itemName)) {
                                            UserSyncData.getJSONObject("troop").getJSONObject("charGroup").getJSONObject(itemName).put("favorPoint", addPercent + completeFavor);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @PostMapping(
            value = {"/squadFormation"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject SquadFormation(@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArKnightsApplication.getIpAddr(request);
        String secret = ArKnightsApplication.getSecretByIP(clientIp);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /quest/squadFormation");
        if (!ArKnightsApplication.enableServer) {
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
        String clientIp = ArKnightsApplication.getIpAddr(request);
        String secret = ArKnightsApplication.getSecretByIP(clientIp);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /quest/saveBattleReplay");
        JSONObject BattleData;
        if (!ArKnightsApplication.enableServer) {
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
        String clientIp = ArKnightsApplication.getIpAddr(request);
        String secret = ArKnightsApplication.getSecretByIP(clientIp);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /quest/getBattleReplay");
        if (!ArKnightsApplication.enableServer) {
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
        String clientIp = ArKnightsApplication.getIpAddr(request);
        String secret = ArKnightsApplication.getSecretByIP(clientIp);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /quest/changeSquadName");
        if (!ArKnightsApplication.enableServer) {
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
        String clientIp = ArKnightsApplication.getIpAddr(request);
        String secret = ArKnightsApplication.getSecretByIP(clientIp);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /quest/getAssistList");
        if (!ArKnightsApplication.enableServer) {
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
        String clientIp = ArKnightsApplication.getIpAddr(request);
        String secret = ArKnightsApplication.getSecretByIP(clientIp);
        ArKnightsApplication.LOGGER.info("[/" + clientIp + "] /quest/finishStoryStage");
        if (!ArKnightsApplication.enableServer) {
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
                    JSONObject stageClear = ArKnightsApplication.mainStage.getJSONObject(stageId);
                    JSONArray rewards = new JSONArray();
                    JSONArray unlockStages = new JSONArray();
                    JSONArray unlockStagesObject = new JSONArray();
                    JSONArray alert = new JSONArray();
                    int DropRate = ArKnightsApplication.serverConfig.getJSONObject("battle").getIntValue("dropRate");
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
