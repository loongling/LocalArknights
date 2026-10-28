package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.decrypt.Utils;
import com.hypergryph.arknights.core.pojo.Account;

import java.awt.image.AreaAveragingScaleFilter;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.*;

import static com.hypergryph.arknights.ArknightsApplication.getCharRarity;
import static com.hypergryph.arknights.core.work.checkAccount.checkAccount;
import static com.hypergryph.arknights.core.work.checkAccount.createError;

@RestController
@RequestMapping({"/charBuild"})
public class charBuild {
    public charBuild() {
    }

    @PostMapping(
            value = {"/upgradeChar"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject UpgradeChar(@RequestHeader(value = "secret", required = false)String secret, @RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charBuild/upgradeChar");
        ArknightsApplication.LOGGER.info("UC:" + JsonBody);
        int charInstId = JsonBody.getIntValue("charInstId");
        JSONArray expMats = JsonBody.getJSONArray("expMats");
        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (checkAccount(secret)) {
            Long uid = ((Account)Accounts.get(0)).getUid();
            JSONObject UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
            JSONObject chars = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(charInstId));
            String charid = chars.getString("charId");
            int evolvePhase = chars.getIntValue("evolvePhase");
            int level = chars.getIntValue("level");
            int exp = chars.getIntValue("exp");
            JSONObject inventory = new JSONObject(true);
            int AddExp = 0;

            int rarity;
            String itemid;
            for(rarity = 0; rarity < expMats.size(); ++rarity) {
                itemid = expMats.getJSONObject(rarity).getString("id");
                int count = expMats.getJSONObject(rarity).getInteger("count");
                if (itemid.equals("2001")) {
                    AddExp += 200 * count;
                } else if (itemid.equals("2002")) {
                    AddExp += 400 * count;
                } else if (itemid.equals("2003")) {
                    AddExp += 1000 * count;
                } else if (itemid.equals("2004")) {
                    AddExp += 2000 * count;
                }

                UserSyncData.getJSONObject("inventory").put(itemid, UserSyncData.getJSONObject("inventory").getIntValue(itemid) - count);
                inventory.put(itemid, UserSyncData.getJSONObject("inventory").getIntValue(itemid));
            }

            rarity =  getCharRarity(charid);
            itemid = "[0,100,117,134,151,168,185,202,219,236,253,270,287,304,321,338,355,372,389,406,423,440,457,474,491,508,525,542,559,574,589,605,621,637,653,669,685,701,716,724,739,749,759,770,783,804,820,836,852,888,-1]";
            String e_0_cost = "[0,30,36,43,50,57,65,73,81,90,99,108,118,128,138,149,160,182,206,231,258,286,315,346,378,411,446,482,520,557,595,635,677,720,764,809,856,904,952,992,1042,1086,1131,1178,1229,1294,1353,1413,1474,1572,-1]";
            String evolve_1 = "[0,120,172,224,276,328,380,432,484,536,588,640,692,744,796,848,900,952,1004,1056,1108,1160,1212,1264,1316,1368,1420,1472,1524,1576,1628,1706,1784,1862,1940,2018,2096,2174,2252,2330,2408,2584,2760,2936,3112,3288,3464,3640,3816,3992,4168,4344,4520,4696,4890,5326,6019,6312,6505,6838,7391,7657,7823,8089,8355,8621,8887,9153,9419,9605,9951,10448,10945,11442,11939,12436,12933,13430,13927,14549,-1]";
            String e_1_cost = "[0,48,71,95,120,146,173,201,231,262,293,326,361,396,432,470,508,548,589,631,675,719,765,811,859,908,958,1010,1062,1116,1171,1245,1322,1400,1480,1562,1645,1731,1817,1906,1996,2171,2349,2531,2717,2907,3100,3298,3499,3705,3914,4127,4344,4565,4807,5294,6049,6413,6681,7098,7753,8116,8378,8752,9132,9518,9909,10306,10709,11027,11533,12224,12926,13639,14363,15097,15843,16599,17367,18303,-1]";
            String evolve_2 = "[0,191,303,415,527,639,751,863,975,1087,1199,1311,1423,1535,1647,1759,1871,1983,2095,2207,2319,2431,2543,2655,2767,2879,2991,3103,3215,3327,3439,3602,3765,3928,4091,4254,4417,4580,4743,4906,5069,5232,5395,5558,5721,5884,6047,6210,6373,6536,6699,6902,7105,7308,7511,7714,7917,8120,8323,8526,8729,9163,9597,10031,10465,10899,11333,11767,12201,12729,13069,13747,14425,15103,15781,16459,17137,17815,18493,19171,19849,21105,22361,23617,24873,26129,27385,28641,29897,31143,-1]";
            String e_2_cost = "[0,76,124,173,225,279,334,392,451,513,577,642,710,780,851,925,1001,1079,1159,1240,1324,1410,1498,1588,1680,1773,1869,1967,2067,2169,2273,2413,2556,2702,2851,3003,3158,3316,3477,3640,3807,3976,4149,4324,4502,4684,4868,5055,5245,5438,5634,5867,6103,6343,6587,6835,7086,7340,7599,7861,8127,8613,9108,9610,10120,10637,11163,11696,12238,12882,13343,14159,14988,15828,16681,17545,18422,19311,20213,21126,22092,23722,25380,27065,28778,30519,32287,34083,35906,37745,-1]";
            JSONArray characterExpMap = new JSONArray();
            JSONArray characterUpgradeCostMap = new JSONArray();
            int List = 0;
            int lv = 0;
            int ep = 0;
            if (evolvePhase == 0) {
                characterExpMap = JSONArray.parseArray(itemid);
                characterUpgradeCostMap = JSONArray.parseArray(e_0_cost);
            } else if (evolvePhase == 1) {
                characterExpMap = JSONArray.parseArray(evolve_1);
                characterUpgradeCostMap = JSONArray.parseArray(e_1_cost);
            } else if (evolvePhase == 2) {
                characterExpMap = JSONArray.parseArray(evolve_2);
                characterUpgradeCostMap = JSONArray.parseArray(e_2_cost);
            }

            int levelexp;
            int AddedExp;
            int i;
            if (rarity == 5) {
                levelexp = 0;

                for(AddedExp = 0; AddedExp < characterExpMap.size(); ++AddedExp) {
                    List += characterExpMap.getIntValue(AddedExp);
                    if (level == AddedExp + 1) {
                        levelexp = List;
                        break;
                    }
                }

                List = 0;
                AddedExp = levelexp + exp + AddExp;

                for(i = 0; i < characterExpMap.size(); ++i) {
                    List += characterExpMap.getIntValue(i);
                    if (AddedExp < List) {
                        lv = i;
                        ep = characterExpMap.getIntValue(i) - (List - AddedExp);
                        chars.put("level", lv);
                        chars.put("exp", ep);
                        break;
                    }

                    if (evolvePhase == 0) {
                        if (AddedExp >= 24400) {
                            lv = 50;
                            ep = 0;
                            chars.put("level", lv);
                            chars.put("exp", ep);
                            break;
                        }
                    } else if (evolvePhase == 1) {
                        if (AddedExp >= 337000) {
                            lv = 80;
                            ep = 0;
                            chars.put("level", lv);
                            chars.put("exp", ep);
                            break;
                        }
                    } else if (evolvePhase == 2 && AddedExp >= 750000) {
                        lv = 90;
                        ep = 0;
                        chars.put("level", lv);
                        chars.put("exp", ep);
                        break;
                    }
                }
            } else if (rarity == 4) {
                levelexp = 0;

                for(AddedExp = 0; AddedExp < characterExpMap.size(); ++AddedExp) {
                    List += characterExpMap.getIntValue(AddedExp);
                    if (level == AddedExp + 1) {
                        levelexp = List;
                        break;
                    }
                }

                List = 0;
                AddedExp = levelexp + exp + AddExp;

                for(i = 0; i < characterExpMap.size(); ++i) {
                    List += characterExpMap.getIntValue(i);
                    if (AddedExp < List) {
                        lv = i;
                        ep = characterExpMap.getIntValue(i) - (List - AddedExp);
                        chars.put("level", lv);
                        chars.put("exp", ep);
                        break;
                    }

                    if (evolvePhase == 0) {
                        if (AddedExp >= 24400) {
                            lv = 50;
                            ep = 0;
                            chars.put("level", lv);
                            chars.put("exp", ep);
                            break;
                        }
                    } else if (evolvePhase == 1) {
                        if (AddedExp >= 215000) {
                            lv = 70;
                            ep = 0;
                            chars.put("level", lv);
                            chars.put("exp", ep);
                            break;
                        }
                    } else if (evolvePhase == 2 && AddedExp >= 495000) {
                        lv = 80;
                        ep = 0;
                        chars.put("level", lv);
                        chars.put("exp", ep);
                        break;
                    }
                }
            } else if (rarity == 3) {
                levelexp = 0;

                for(AddedExp = 0; AddedExp < characterExpMap.size(); ++AddedExp) {
                    List += characterExpMap.getIntValue(AddedExp);
                    if (level == AddedExp + 1) {
                        levelexp = List;
                        break;
                    }
                }

                List = 0;
                AddedExp = levelexp + exp + AddExp;

                for(i = 0; i < characterExpMap.size(); ++i) {
                    List += characterExpMap.getIntValue(i);
                    if (AddedExp < List) {
                        lv = i;
                        ep = characterExpMap.getIntValue(i) - (List - AddedExp);
                        chars.put("level", lv);
                        chars.put("exp", ep);
                        break;
                    }

                    if (evolvePhase == 0) {
                        if (AddedExp >= 20200) {
                            lv = 45;
                            ep = 0;
                            chars.put("level", lv);
                            chars.put("exp", ep);
                            break;
                        }
                    } else if (evolvePhase == 1) {
                        if (AddedExp >= 130000) {
                            lv = 60;
                            ep = 0;
                            chars.put("level", lv);
                            chars.put("exp", ep);
                            break;
                        }
                    } else if (evolvePhase == 2 && AddedExp >= 333800) {
                        lv = 70;
                        ep = 0;
                        chars.put("level", lv);
                        chars.put("exp", ep);
                        break;
                    }
                }
            } else if (rarity == 2) {
                levelexp = 0;

                for(AddedExp = 0; AddedExp < characterExpMap.size(); ++AddedExp) {
                    List += characterExpMap.getIntValue(AddedExp);
                    if (level == AddedExp + 1) {
                        levelexp = List;
                        break;
                    }
                }

                List = 0;
                AddedExp = levelexp + exp + AddExp;

                for(i = 0; i < characterExpMap.size(); ++i) {
                    List += characterExpMap.getIntValue(i);
                    if (AddedExp < List) {
                        lv = i;
                        ep = characterExpMap.getIntValue(i) - (List - AddedExp);
                        chars.put("level", lv);
                        chars.put("exp", ep);
                        break;
                    }

                    if (evolvePhase == 0) {
                        if (AddedExp >= 16400) {
                            lv = 40;
                            ep = 0;
                            chars.put("level", lv);
                            chars.put("exp", ep);
                            break;
                        }
                    } else if (evolvePhase == 1 && AddedExp >= 99000) {
                        lv = 55;
                        ep = 0;
                        chars.put("level", lv);
                        chars.put("exp", ep);
                        break;
                    }
                }
            } else if (rarity == 1) {
                levelexp = 0;

                for(AddedExp = 0; AddedExp < characterExpMap.size(); ++AddedExp) {
                    List += characterExpMap.getIntValue(AddedExp);
                    if (level == AddedExp + 1) {
                        levelexp = List;
                        break;
                    }
                }

                List = 0;
                AddedExp = levelexp + exp + AddExp;

                for(i = 0; i < characterExpMap.size(); ++i) {
                    List += characterExpMap.getIntValue(i);
                    if (AddedExp < List) {
                        lv = i;
                        ep = characterExpMap.getIntValue(i) - (List - AddedExp);
                        chars.put("level", lv);
                        chars.put("exp", ep);
                        break;
                    }

                    if (evolvePhase == 0 && AddedExp >= 9800) {
                        lv = 30;
                        ep = 0;
                        chars.put("level", lv);
                        chars.put("exp", ep);
                        break;
                    }
                }
            } else if (rarity == 0) {
                levelexp = 0;

                for(AddedExp = 0; AddedExp < characterExpMap.size(); ++AddedExp) {
                    List += characterExpMap.getIntValue(AddedExp);
                    if (level == AddedExp + 1) {
                        levelexp = List;
                        break;
                    }
                }

                List = 0;
                AddedExp = levelexp + exp + AddExp;

                for(i = 0; i < characterExpMap.size(); ++i) {
                    List += characterExpMap.getIntValue(i);
                    if (AddedExp < List) {
                        lv = i;
                        ep = characterExpMap.getIntValue(i) - (List - AddedExp);
                        chars.put("level", lv);
                        chars.put("exp", ep);
                        break;
                    }

                    if (evolvePhase == 0 && AddedExp >= 9800) {
                        lv = 30;
                        ep = 0;
                        chars.put("level", lv);
                        chars.put("exp", ep);
                        break;
                    }
                }
            }

            levelexp = 0;
            AddedExp = Math.round(Float.parseFloat((new DecimalFormat("0.00")).format((double)((float)characterUpgradeCostMap.getIntValue(level) * (float)exp / (float)characterExpMap.getIntValue(level)))));

            for(i = 0; i < level; ++i) {
                levelexp += characterUpgradeCostMap.getIntValue(i);
            }

            i = 0;
            int addExpCost = Math.round(Float.parseFloat((new DecimalFormat("0.00")).format((double)((float)characterUpgradeCostMap.getIntValue(chars.getIntValue("level")) * (float)chars.getIntValue("exp") / (float)characterExpMap.getIntValue(chars.getIntValue("level"))))));

            int UpgradeCost;
            for(UpgradeCost = 0; UpgradeCost < chars.getIntValue("level"); ++UpgradeCost) {
                i += characterUpgradeCostMap.getIntValue(UpgradeCost);
            }

            UpgradeCost = i + addExpCost - (levelexp + AddedExp);
            UserSyncData.getJSONObject("status").put("gold", UserSyncData.getJSONObject("status").getIntValue("gold") - UpgradeCost);
            userDao.setUserData(uid, UserSyncData);
            JSONObject result = new JSONObject(true);
            JSONObject modified = new JSONObject(true);
            JSONObject playerDataDelta = new JSONObject(true);
            JSONObject troop = new JSONObject(true);
            JSONObject charss = new JSONObject(true);
            JSONObject chars_id = new JSONObject(true);
            JSONObject status = new JSONObject(true);
            status.put("gold", UserSyncData.getJSONObject("status").getIntValue("gold"));
            chars_id.put("exp", ep);
            chars_id.put("level", lv);
            chars_id.put("favorPoint", chars.getIntValue("favorPoint"));
            charss.put(String.valueOf(charInstId), chars_id);
            troop.put("chars", charss);
            modified.put("troop", troop);
            modified.put("status", status);
            modified.put("inventory", inventory);
            playerDataDelta.put("deleted", new JSONObject(true));
            playerDataDelta.put("modified", modified);
            result.put("playerDataDelta", playerDataDelta);
            result.put("result", 0);
            return result;
        }
        else {
            createError(secret, response);
        }
        return null;
    }

    @PostMapping(
            value = {"/upgradeSkill"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject UpgradeSkill(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 技能升级/charBuild/upgradeSkill");
        int charInstId = JsonBody.getIntValue("charInstId");
        int targetLevel = JsonBody.getIntValue("targetLevel");
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
                JSONObject inventory = new JSONObject(true);
                JSONObject chars = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(charInstId));
                String charid = chars.getString("charId");
                JSONArray allSkillLvlup = ArknightsApplication.characterJson.getJSONObject(String.valueOf(charid)).getJSONArray("allSkillLvlup");

                for(int i = 0; i < allSkillLvlup.size(); ++i) {
                    if (targetLevel - 2 == i) {
                        JSONArray lvlUpCost = allSkillLvlup.getJSONObject(i).getJSONArray("lvlUpCost");

                        for(int l = 0; l < lvlUpCost.size(); ++l) {
                            String itemid = lvlUpCost.getJSONObject(l).getString("id");
                            int count = lvlUpCost.getJSONObject(l).getIntValue("count");
                            UserSyncData.getJSONObject("inventory").put(itemid, UserSyncData.getJSONObject("inventory").getIntValue(itemid) - count);
                            inventory.put(itemid, UserSyncData.getJSONObject("inventory").getIntValue(itemid));
                        }
                    }
                }

                UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(charInstId)).put("mainSkillLvl", targetLevel);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                JSONObject modified = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                JSONObject troop = new JSONObject(true);
                JSONObject charss = new JSONObject(true);
                JSONObject chars_id = new JSONObject(true);
                chars_id.put("mainSkillLvl", targetLevel);
                charss.put(String.valueOf(charInstId), chars_id);
                troop.put("chars", charss);
                modified.put("troop", troop);
                modified.put("inventory", inventory);
                playerDataDelta.put("deleted", new JSONObject(true));
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
        }
    }

    @PostMapping(
            value = {"/setDefaultSkill"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject SetDefaultSkill(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 设置技能 /charBuild/setDefaultSkill");
        int charInstId = JsonBody.getIntValue("charInstId");
        int defaultSkillIndex = JsonBody.getInteger("defaultSkillIndex");
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
                JSONObject defSkillChar = UserSyncData.getJSONObject("troop").getJSONObject("chars")
                        .getJSONObject(String.valueOf(charInstId));
                // 每个模板各自记 defaultSkillIndex（官方实测：术师 2 / 近卫 1 / 医疗 1）
                com.hypergryph.arknights.core.work.processArray
                        .activeCharData(defSkillChar).put("defaultSkillIndex", defaultSkillIndex);
                userDao.setUserData(uid, UserSyncData);
                // 官方只回 {defaultSkillIndex}
                JSONObject dsiDelta = new JSONObject(true);
                if (defSkillChar.getJSONObject("tmpl") != null) {
                    dsiDelta.put("tmpl", defSkillChar.getJSONObject("tmpl"));
                } else {
                    dsiDelta.put("defaultSkillIndex", defaultSkillIndex);
                }
                JSONObject result = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                JSONObject modified = new JSONObject(true);
                JSONObject troop = new JSONObject(true);
                JSONObject chars = new JSONObject(true);
                chars.put(String.valueOf(charInstId), dsiDelta);
                troop.put("chars", chars);
                modified.put("troop", troop);
                playerDataDelta.put("deleted", new JSONObject(true));
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
        }
    }

    @PostMapping(
            value = {"/boostPotential"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject BoostPotential(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "]  /charBuild/boostPotential");
        int charInstId = JsonBody.getIntValue("charInstId");
        int targetRank = JsonBody.getInteger("targetRank");
        String itemId = JsonBody.getString("itemId");
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
                if (itemId.indexOf("_char_") != -1) {
                    UserSyncData.getJSONObject("inventory").put(itemId, UserSyncData.getJSONObject("inventory").getIntValue(itemId) - 1);
                } else {
                    UserSyncData.getJSONObject("inventory").put(itemId, UserSyncData.getJSONObject("inventory").getIntValue(itemId) - 4);
                }

                UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(charInstId)).put("potentialRank", targetRank);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                JSONObject modified = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                JSONObject inventory = new JSONObject(true);
                JSONObject troop = new JSONObject(true);
                JSONObject chars = new JSONObject(true);
                JSONObject charid = new JSONObject(true);
                charid.put("potentialRank", targetRank);
                chars.put(String.valueOf(charInstId), charid);
                troop.put("chars", chars);
                inventory.put(itemId, UserSyncData.getJSONObject("inventory").getIntValue(itemId));
                modified.put("inventory", inventory);
                modified.put("troop", troop);
                playerDataDelta.put("deleted", new JSONObject(true));
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                result.put("result", 1);
                return result;
            }
        }
    }

    @PostMapping(
            value = {"/evolveChar"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject EvolveChar(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charBuild/evolveChar");
        int charInstId = JsonBody.getIntValue("charInstId");
        int destEvolvePhase = JsonBody.getIntValue("destEvolvePhase");
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
                JSONObject chars = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(charInstId));
                JSONObject inventory = new JSONObject(true);
                String charid = chars.getString("charId");
                int rarity = getCharRarity(charid);
                JSONArray phases = ArknightsApplication.characterJson.getJSONObject(String.valueOf(charid)).getJSONArray("phases");
                JSONArray skills = ArknightsApplication.characterJson.getJSONObject(String.valueOf(charid)).getJSONArray("skills");

                // 精二/精一都得真实存在（12F 这种只有 E0 的干员，phase 表长度就是 1）
                if (destEvolvePhase < 0 || destEvolvePhase >= phases.size()) {
                    JSONObject bad = new JSONObject(true);
                    bad.put("result", 1);
                    bad.put("error", charid + " 没有精" + destEvolvePhase + "（最多精" + (phases.size() - 1) + "）");
                    return bad;
                }

                int gold;
                int i;
                for(gold = 0; gold < phases.size(); ++gold) {
                    if (destEvolvePhase == gold) {
                        JSONArray evolveCost = phases.getJSONObject(gold).getJSONArray("evolveCost");

                        for(int l = 0; l < evolveCost.size(); ++l) {
                            String itemid = evolveCost.getJSONObject(l).getString("id");
                            i = evolveCost.getJSONObject(l).getIntValue("count");
                            UserSyncData.getJSONObject("inventory").put(itemid, UserSyncData.getJSONObject("inventory").getIntValue(itemid) - i);
                            inventory.put(itemid, UserSyncData.getJSONObject("inventory").getIntValue(itemid));
                        }
                    }
                }

                gold = UserSyncData.getJSONObject("status").getIntValue("gold");
                JSONObject tchar = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(charInstId));
                String charId = tchar.getString("charId");
                String skinAtStart = com.hypergryph.arknights.core.work.processArray.getSkin(tchar);
                if (destEvolvePhase == 1) {
                    if (rarity == 2) {
                        gold -= 10000;
                    } else if (rarity == 3) {
                        gold -= 15000;
                    } else if (rarity == 4) {
                        gold -= 20000;
                    } else if (rarity == 5) {
                        gold -= 30000;
                    }

                    // 精一换皮肤：#1 → #1+（官方实测：char_002_amiya#1+）
                    String skinBefore = com.hypergryph.arknights.core.work.processArray.getSkin(tchar);
                    if (skinBefore == null || skinBefore.equals(charId + "#1")) {
                        com.hypergryph.arknights.core.work.processArray
                                .setSkin(tchar, charId + "#1+");
                    }

                    // 按 skillId 去静态表查 phase；模板干员的基节点 skills 是空的，要连模板一起解锁
                    com.hypergryph.arknights.core.work.processArray
                            .unlockSkillsForPhase(tchar, charId, 1);
                } else if (destEvolvePhase == 2) {
                    if (rarity == 3) {
                        gold -= 60000;
                    } else if (rarity == 4) {
                        gold -= 120000;
                    } else if (rarity == 5) {
                        gold -= 180000;
                    }

                    // 模板干员的基节点 skin 是 null，不能再 .equals()（会 NPE）
                    // 精二：从 #1 或 #1+ 都换到 #2
                    String curSkin = com.hypergryph.arknights.core.work.processArray.getSkin(tchar);
                    if (curSkin == null || curSkin.equals(charId + "#1") || curSkin.equals(charId + "#1+")) {
                        com.hypergryph.arknights.core.work.processArray
                                .setSkin(tchar, charId + "#2");
                    }

                    com.hypergryph.arknights.core.work.processArray
                            .unlockSkillsForPhase(tchar, charId, 2);

                    // 精二后模组槽变成可见：官方 300 个干员实测 hide = (evolvePhase>=2 ? 0 : 1)
                    com.hypergryph.arknights.core.work.processArray.setEquipHidden(tchar, false);
                }

                boolean skinChanged = !java.util.Objects.equals(
                        skinAtStart, com.hypergryph.arknights.core.work.processArray.getSkin(tchar));
                UserSyncData.getJSONObject("status").put("gold", gold);
                tchar.put("evolvePhase", destEvolvePhase);
                tchar.put("level", 1);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                JSONObject modified = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                JSONObject troop = new JSONObject(true);
                JSONObject status = new JSONObject(true);
                status.put("gold", gold);
                JSONObject charss = new JSONObject(true);
                JSONObject evolveDelta;
                if (skinChanged) {
                    evolveDelta = tchar;
                } else {
                    evolveDelta = new JSONObject(true);
                    evolveDelta.put("level", tchar.getIntValue("level"));
                    evolveDelta.put("evolvePhase", destEvolvePhase);
                }
                charss.put(String.valueOf(charInstId), evolveDelta);
                troop.put("chars", charss);
                modified.put("troop", troop);
                modified.put("inventory", inventory);
                modified.put("status", status);
                playerDataDelta.put("deleted", new JSONObject(true));
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
        }
    }

    @PostMapping(
            value = {"/evolveCharUseItem"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject evolveCharUseItem(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charBuild/evolveCharUseItem");
        int charInstId = JsonBody.getIntValue("charInstId");
        String itemId = JsonBody.getString("itemId");
        int instId = JsonBody.getIntValue("instId");

        JSONObject result = new JSONObject(true);
        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }
        Account account = (Account)Accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(500);
            JSONObject banned = new JSONObject(true);
            banned.put("statusCode", 403);
            banned.put("error", "Bad Request");
            banned.put("message", "error");
            return banned;
        }

        JSONObject UserSyncData = JSONObject.parseObject(account.getUser());
        JSONObject tchar = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(charInstId));
        if (tchar == null) {
            result.put("result", 1);
            result.put("error", "没有这个干员: " + charInstId);
            return result;
        }

        JSONObject consumableDelta = spendConsumable(UserSyncData, itemId, instId);
        if (consumableDelta == null) {
            result.put("result", 1);
            result.put("error", "没有这件特训装置: " + itemId + " / instId=" + instId);
            return result;
        }

        String charId = tchar.getString("charId");
        int curPhase = tchar.getIntValue("evolvePhase");
        int maxPhase = 0;
        JSONArray charPhases = ArknightsApplication.characterJson.getJSONObject(charId).getJSONArray("phases");
        if (charPhases != null) {
            maxPhase = Math.max(0, charPhases.size() - 1);
        }
        if (curPhase >= maxPhase) {
            result.put("result", 1);
            result.put("error", charId + " 已经到最高精英阶段（精" + maxPhase + "），不能用精二直升凭证");
            return result;
        }
        int destPhase = Math.min(maxPhase, curPhase + 1);
        if (destPhase == 1) {
            String skinBefore = com.hypergryph.arknights.core.work.processArray.getSkin(tchar);
            if (skinBefore == null || skinBefore.equals(charId + "#1")) {
                com.hypergryph.arknights.core.work.processArray.setSkin(tchar, charId + "#1+");
            }
            com.hypergryph.arknights.core.work.processArray.unlockSkillsForPhase(tchar, charId, 1);
        } else if (destPhase == 2) {
            String curSkin = com.hypergryph.arknights.core.work.processArray.getSkin(tchar);
            if (curSkin == null || curSkin.equals(charId + "#1") || curSkin.equals(charId + "#1+")) {
                com.hypergryph.arknights.core.work.processArray.setSkin(tchar, charId + "#2");
            }
            com.hypergryph.arknights.core.work.processArray.unlockSkillsForPhase(tchar, charId, 2);
            com.hypergryph.arknights.core.work.processArray.setEquipHidden(tchar, false);
        }
        tchar.put("evolvePhase", destPhase);
        userDao.setUserData(account.getUid(), UserSyncData);

        JSONObject charss = new JSONObject(true);
        charss.put(String.valueOf(charInstId), tchar);
        JSONObject troop = new JSONObject(true);
        troop.put("chars", charss);

        JSONObject modified = new JSONObject(true);
        modified.put("consumable", consumableDelta);
        modified.put("troop", troop);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("playerDataDelta", playerDataDelta);
        result.put("result", 0);
        ArknightsApplication.LOGGER.info("[evolveCharUseItem] {} ({}) 精二，消耗 {}/{}", charId, charInstId, itemId, instId);
        return result;
    }

    @PostMapping(
            value = {"/upgradeCharLevelMaxUseItem"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject upgradeCharLevelMaxUseItem(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charBuild/upgradeCharLevelMaxUseItem");
        // 官方字段：charInsId / itemInsId；兼容带 t 的写法
        int charInstId = JsonBody.containsKey("charInsId") ? JsonBody.getIntValue("charInsId") : JsonBody.getIntValue("charInstId");
        String itemId = JsonBody.getString("itemId");
        int instId = JsonBody.containsKey("itemInsId") ? JsonBody.getIntValue("itemInsId") : JsonBody.getIntValue("instId");

        JSONObject result = new JSONObject(true);
        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }
        Account account = (Account)Accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(500);
            JSONObject banned = new JSONObject(true);
            banned.put("statusCode", 403);
            banned.put("error", "Bad Request");
            banned.put("message", "error");
            return banned;
        }

        JSONObject UserSyncData = JSONObject.parseObject(account.getUser());
        JSONObject tchar = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(charInstId));
        if (tchar == null) {
            result.put("result", 1);
            result.put("error", "没有这个干员: " + charInstId);
            return result;
        }

        JSONObject consumableDelta = spendConsumable(UserSyncData, itemId, instId);
        if (consumableDelta == null) {
            result.put("result", 1);
            result.put("error", "没有这件特训装置: " + itemId + " / instId=" + instId);
            return result;
        }

        String charId = tchar.getString("charId");
        int phase = tchar.getIntValue("evolvePhase");
        int maxLevel = maxLevelOf(charId, phase);
        tchar.put("level", maxLevel);
        tchar.put("exp", 0);
        userDao.setUserData(account.getUid(), UserSyncData);

        JSONObject charDelta = new JSONObject(true);
        charDelta.put("level", maxLevel);
        charDelta.put("exp", 0);
        JSONObject charss = new JSONObject(true);
        charss.put(String.valueOf(charInstId), charDelta);
        JSONObject troop = new JSONObject(true);
        troop.put("chars", charss);

        JSONObject modified = new JSONObject(true);
        modified.put("consumable", consumableDelta);
        modified.put("troop", troop);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("playerDataDelta", playerDataDelta);
        result.put("result", 0);
        ArknightsApplication.LOGGER.info("[upgradeCharLevelMaxUseItem] {} ({}) -> 精{} 满级 {}", charId, charInstId, phase, maxLevel);
        return result;
    }

    private static JSONObject spendConsumable(JSONObject UserSyncData, String itemId, int instId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        JSONObject consumable = UserSyncData.getJSONObject("consumable");
        if (consumable == null) {
            return null;
        }
        JSONObject byInst = consumable.getJSONObject(itemId);
        if (byInst == null) {
            return null;
        }
        JSONObject inst = byInst.getJSONObject(String.valueOf(instId));
        if (inst == null || inst.getIntValue("count") <= 0) {
            return null;
        }
        inst.put("count", 0);
        inst.put("ts", -1);

        JSONObject after = new JSONObject(true);
        after.put("ts", -1);
        after.put("count", 0);
        JSONObject oneInst = new JSONObject(true);
        oneInst.put(String.valueOf(instId), after);
        JSONObject delta = new JSONObject(true);
        delta.put(itemId, oneInst);
        return delta;
    }

    private static int maxLevelOf(String charId, int phase) {
        try {
            JSONObject def = ArknightsApplication.characterJson.getJSONObject(charId);
            JSONArray phases = def == null ? null : def.getJSONArray("phases");
            if (phases != null && phase >= 0 && phase < phases.size()) {
                int max = phases.getJSONObject(phase).getIntValue("maxLevel");
                if (max > 0) {
                    return max;
                }
            }
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[charBuild] 取 {} 精{} 等级上限失败: {}", charId, phase, e.toString());
        }
        // 兜底：按精英阶段取最常见的上限
        return phase >= 2 ? 90 : (phase == 1 ? 80 : 50);
    }

    @PostMapping(
            value = {"/changeCharSkin"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject ChangeCharSkin(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charBuild/changeCharSkin");
        int charInstId = JsonBody.getIntValue("charInstId");
        String skinId = JsonBody.getString("skinId");
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
                JSONObject skinChar = UserSyncData.getJSONObject("troop").getJSONObject("chars")
                        .getJSONObject(String.valueOf(charInstId));
                // 基节点字段是 skin，模板 patch 里叫 skinId
                com.hypergryph.arknights.core.work.processArray.setSkin(skinChar, skinId);
                userDao.setUserData(uid, UserSyncData);
                // 官方只回变更字段（2026-10-27 实测 103 B）：
                //   非模板干员 -> {"skin": "..."}；模板干员 -> 整个 tmpl（skinId 在 patch 里）
                JSONObject skinDelta = new JSONObject(true);
                if (skinChar.getJSONObject("tmpl") != null) {
                    skinDelta.put("tmpl", skinChar.getJSONObject("tmpl"));
                } else {
                    skinDelta.put("skin", skinId);
                }
                JSONObject result = new JSONObject(true);
                JSONObject modified = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                JSONObject troop = new JSONObject(true);
                JSONObject chars = new JSONObject(true);
                chars.put(String.valueOf(charInstId), skinDelta);
                troop.put("chars", chars);
                modified.put("troop", troop);
                playerDataDelta.put("deleted", new JSONObject(true));
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
        }
    }

    @PostMapping(
            value = {"/setCharVoiceLan"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject setCharVoiceLan(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charBuild/setCharVoiceLan");
        JSONArray charList = JsonBody.getJSONArray("charList");
        String voiceLan = JsonBody.getString("voiceLan");
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

                for(int i = 0; i < charList.size(); ++i) {
                    UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(charList.getString(i)).put("voiceLan", voiceLan);
                }

                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                JSONObject modified = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                JSONObject troop = new JSONObject(true);
                troop.put("chars", UserSyncData.getJSONObject("troop").getJSONObject("chars"));
                modified.put("troop", troop);
                playerDataDelta.put("deleted", new JSONObject(true));
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
        }
    }

    @PostMapping(
            value = {"/batchSetCharVoiceLan"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject batchSetCharVoiceLan(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charBuild/batchSetCharVoiceLan");
        String voiceLan = JsonBody.getString("voiceLan");
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
                JSONObject voiceLangDict = ArknightsApplication.charwordTable.getJSONObject("voiceLangDict");
                JSONObject chars = UserSyncData.getJSONObject("troop").getJSONObject("chars");
                Iterator var12 = chars.entrySet().iterator();

                JSONObject cvDictionary;
                while(var12.hasNext()) {
                    Map.Entry entry = (Map.Entry)var12.next();
                    String charId = chars.getJSONObject(entry.getKey().toString()).getString("charId");
                    if (voiceLangDict.containsKey(charId)) {
                        cvDictionary = voiceLangDict.getJSONObject(charId).getJSONObject("dict");
                        if (cvDictionary.containsKey(voiceLan)) {
                            chars.getJSONObject(entry.getKey().toString()).put("voiceLan", voiceLan);
                        }
                    }
                }

                UserSyncData.getJSONObject("status").put("globalVoiceLan", voiceLan);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                JSONObject modified = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                cvDictionary = new JSONObject(true);
                cvDictionary.put("chars", chars);
                modified.put("troop", cvDictionary);
                playerDataDelta.put("deleted", new JSONObject(true));
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                result.put("result", 0);
                return result;
            }
        }
    }

    @PostMapping(
            value = {"/unlockEquipment"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject unlockEquipment(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charBuild/unlockEquipment");
        int charInstId = JsonBody.getIntValue("charInstId");
        String templateId = JsonBody.getString("templateId");
        String equipId = JsonBody.getString("equipId");
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
                JSONObject unlockChar = UserSyncData.getJSONObject("troop").getJSONObject("chars")
                        .getJSONObject(String.valueOf(charInstId));
                // 模板干员（阿米娅）的 equip 在 tmpl[currentTmpl] 里，基节点是空对象
                JSONObject unlockTarget = com.hypergryph.arknights.core.work.processArray
                        .activeCharData(unlockChar);
                if (unlockTarget.getJSONObject("equip") == null) {
                    unlockTarget.put("equip", new JSONObject(true));
                }
                JSONObject eqSlot = unlockTarget.getJSONObject("equip").getJSONObject(equipId);
                if (eqSlot == null) {
                    JSONObject bad = new JSONObject(true);
                    bad.put("result", 1);
                    bad.put("error", "没有这个模组槽: " + equipId);
                    return bad;
                }
                if (eqSlot.getIntValue("locked") == 0) {
                    JSONObject bad = new JSONObject(true);
                    bad.put("result", 1);
                    bad.put("error", "该模组已解锁");
                    return bad;
                }

                // 扣解锁消耗：uniequip_table.equipDict[equipId].itemCost["1"]
                JSONObject invDelta = new JSONObject(true);
                JSONObject statDelta = new JSONObject(true);
                String costError = consumeEquipCost(UserSyncData, equipId, 1, invDelta, statDelta);
                if (costError != null) {
                    JSONObject bad = new JSONObject(true);
                    bad.put("result", 1);
                    bad.put("error", costError);
                    return bad;
                }

                eqSlot.put("locked", 0);
                eqSlot.put("level", 1);
                userDao.setUserData(uid, UserSyncData);

                JSONObject result = new JSONObject(true);
                result.put("result", 0);
                JSONObject modified = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                JSONObject troop = new JSONObject(true);
                JSONObject chars = new JSONObject(true);
                chars.put(String.valueOf(charInstId), UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(charInstId)));
                troop.put("chars", chars);
                modified.put("troop", troop);
                if (!invDelta.isEmpty()) {
                    modified.put("inventory", invDelta);
                }
                if (!statDelta.isEmpty()) {
                    modified.put("status", statDelta);
                }
                playerDataDelta.put("deleted", new JSONObject(true));
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
        }
    }

    @PostMapping(
            value = {"/setEquipment"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject setEquipment(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charBuild/setEquipment");
        int charInstId = JsonBody.getIntValue("charInstId");
        String templateId = JsonBody.getString("templateId");
        String equipId = JsonBody.getString("equipId");
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
                JSONObject setChar = UserSyncData.getJSONObject("troop").getJSONObject("chars")
                        .getJSONObject(String.valueOf(charInstId));
                // 客户端会带 templateId（非模板干员为 null），有就按它写，没有才回落到 currentTmpl
                JSONObject setTarget = com.hypergryph.arknights.core.work.processArray
                        .templateNode(setChar, templateId);
                setTarget.put("currentEquip", equipId);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                result.put("result", 0);
                JSONObject modified = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                JSONObject troop = new JSONObject(true);
                JSONObject chars = new JSONObject(true);
                chars.put(String.valueOf(charInstId), UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(String.valueOf(charInstId)));
                troop.put("chars", chars);
                modified.put("troop", troop);
                playerDataDelta.put("deleted", new JSONObject(true));
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
        }
    }

    @PostMapping(
            value = {"/upgradeEquipment"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject upgradeEquipment(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[" + "/" + clientIp + "] /charBuild/upgradeEquipment");
        int charInstId = JsonBody.getIntValue("charInstId");
        String templateId = JsonBody.getString("templateId");
        String equipId = JsonBody.getString("equipId");
        int targetLevel = JsonBody.getIntValue("targetLevel");

        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        if (Accounts.size() != 1) {
            JSONObject result = new JSONObject(true);
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }
        Long uid = ((Account)Accounts.get(0)).getUid();
        if (((Account)Accounts.get(0)).getBan() == 1L) {
            response.setStatus(500);
            JSONObject banned = new JSONObject(true);
            banned.put("statusCode", 403);
            banned.put("error", "Bad Request");
            banned.put("message", "error");
            return banned;
        }

        JSONObject UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
        JSONObject upChar = UserSyncData.getJSONObject("troop").getJSONObject("chars")
                .getJSONObject(String.valueOf(charInstId));
        JSONObject upTarget = com.hypergryph.arknights.core.work.processArray
                .templateNode(upChar, templateId);

        JSONObject upEquipMap = upTarget.getJSONObject("equip");
        JSONObject upSlot = upEquipMap == null ? null : upEquipMap.getJSONObject(equipId);
        if (upSlot == null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "没有这个模组槽: " + equipId);
            return bad;
        }
        if (upSlot.getIntValue("locked") != 0) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "该模组尚未解锁");
            return bad;
        }
        int curLevel = upSlot.getIntValue("level");
        if (targetLevel != curLevel + 1) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", "等级不连续：当前 " + curLevel + "，请求 " + targetLevel);
            return bad;
        }

        // 消耗 = equipDict[equipId].itemCost[targetLevel]
        JSONObject invDelta = new JSONObject(true);
        JSONObject statDelta = new JSONObject(true);
        String costError = consumeEquipCost(UserSyncData, equipId, targetLevel, invDelta, statDelta);
        if (costError != null) {
            JSONObject bad = new JSONObject(true);
            bad.put("result", 1);
            bad.put("error", costError);
            return bad;
        }

        upSlot.put("level", targetLevel);
        userDao.setUserData(uid, UserSyncData);

        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        JSONObject modified = new JSONObject(true);
        JSONObject playerDataDelta = new JSONObject(true);
        JSONObject troop = new JSONObject(true);
        JSONObject chars = new JSONObject(true);
        // 官方回整个干员对象（含 equip），照做
        chars.put(String.valueOf(charInstId), UserSyncData.getJSONObject("troop").getJSONObject("chars")
                .getJSONObject(String.valueOf(charInstId)));
        troop.put("chars", chars);
        modified.put("troop", troop);
        if (!invDelta.isEmpty()) {
            modified.put("inventory", invDelta);
        }
        if (!statDelta.isEmpty()) {
            modified.put("status", statDelta);
        }
        playerDataDelta.put("deleted", new JSONObject(true));
        playerDataDelta.put("modified", modified);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    private String consumeEquipCost(JSONObject UserSyncData, String equipId, int level,
                                    JSONObject invDelta, JSONObject statDelta) {
        JSONObject equipDict = ArknightsApplication.uniequipTable;
        JSONObject def = equipDict == null ? null : equipDict.getJSONObject(equipId);
        if (def == null) {
            return "模组表里没有 " + equipId;
        }
        JSONObject itemCost = def.getJSONObject("itemCost");
        JSONArray cost = itemCost == null ? null : itemCost.getJSONArray(String.valueOf(level));
        if (cost == null || cost.isEmpty()) {
            return "模组表里没有第 " + level + " 级的消耗";
        }
        // 先整体校验再扣，避免扣一半失败
        for (int i = 0; i < cost.size(); i++) {
            JSONObject item = cost.getJSONObject(i);
            String id = item.getString("id");
            int need = item.getIntValue("count");
            String type = item.getString("type");
            if ("GOLD".equals(type) || "4001".equals(id)) {
                if (UserSyncData.getJSONObject("status").getIntValue("gold") < need) {
                    return "龙门币不足（需要 " + need + "）";
                }
            } else {
                if (UserSyncData.getJSONObject("inventory").getIntValue(id) < need) {
                    return "材料不足: " + id + " 需要 " + need;
                }
            }
        }
        for (int i = 0; i < cost.size(); i++) {
            JSONObject item = cost.getJSONObject(i);
            String id = item.getString("id");
            int need = item.getIntValue("count");
            String type = item.getString("type");
            if ("GOLD".equals(type) || "4001".equals(id)) {
                int left = UserSyncData.getJSONObject("status").getIntValue("gold") - need;
                UserSyncData.getJSONObject("status").put("gold", left);
                statDelta.put("gold", left);
            } else {
                int left = UserSyncData.getJSONObject("inventory").getIntValue(id) - need;
                UserSyncData.getJSONObject("inventory").put(id, left);
                invDelta.put(id, left);
            }
        }
        return null;
    }

    @PostMapping(
            value = {"/addonStory/unlock"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject addonStoryUnlock(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charBuild/addonStory/unlock");
        String charId = JsonBody.getString("charId");
        String storyId = JsonBody.getString("storyId");
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
                JSONObject story = new JSONObject(true);
                story.put("fts", (new Date()).getTime() / 1000L);
                story.put("rts", (new Date()).getTime() / 1000L);
                JSONObject addon = UserSyncData.getJSONObject("troop").getJSONObject("addon");
                JSONObject char_data = new JSONObject(true);
                JSONObject story_1;
                if (addon.containsKey(charId)) {
                    if (addon.getJSONObject(charId).containsKey("story")) {
                        addon.getJSONObject(charId).getJSONObject("story").put(storyId, story);
                        UserSyncData.getJSONObject("troop").put("addon", addon);
                    } else {
                        story_1 = new JSONObject(true);
                        story_1.put(storyId, story);
                        char_data = addon.getJSONObject(charId);
                        char_data.put("story", story_1);
                        addon.put(charId, char_data);
                        UserSyncData.getJSONObject("troop").put("addon", addon);
                    }
                } else {
                    story_1 = new JSONObject(true);
                    story_1.put(storyId, story);
                    char_data.put("story", story_1);
                    addon.put(charId, char_data);
                    UserSyncData.getJSONObject("troop").put("addon", addon);
                }

                userDao.setUserData(uid, UserSyncData);
                story_1 = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                JSONObject modified = new JSONObject(true);
                JSONObject troop = new JSONObject(true);
                troop.put("addon", addon);
                modified.put("troop", troop);
                playerDataDelta.put("modified", modified);
                story_1.put("playerDataDelta", playerDataDelta);
                return story_1;
            }
        }
    }

    @PostMapping(
            value = {"/addonStage/battleStart"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject addonStageBattleStart(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charBuild/addonStage/battleStart");
        String charId = JsonBody.getString("charId");
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
                JSONObject story = new JSONObject(true);
                story.put("fts", -1);
                story.put("rts", -1);
                story.put("startTime", (Object)null);
                story.put("startTimes", 0);
                story.put("state", 0);
                JSONObject addon = UserSyncData.getJSONObject("troop").getJSONObject("addon");
                JSONObject char_data = new JSONObject(true);
                JSONObject result;
                if (!UserSyncData.getJSONObject("troop").getJSONObject("addon").containsKey(charId)) {
                    result = new JSONObject(true);
                    result.put(stageId, story);
                    char_data.put("stage", result);
                    UserSyncData.getJSONObject("troop").getJSONObject("addon").put(charId, char_data);
                    UserSyncData.getJSONObject("troop").put("addon", addon);
                }

                if (!UserSyncData.getJSONObject("troop").getJSONObject("addon").getJSONObject(charId).containsKey("stage")) {
                    result = new JSONObject(true);
                    result.put(stageId, story);
                    char_data.put("stage", result);
                    UserSyncData.getJSONObject("troop").getJSONObject("addon").put(charId, char_data);
                    UserSyncData.getJSONObject("troop").put("addon", addon);
                }

                if (!UserSyncData.getJSONObject("troop").getJSONObject("addon").getJSONObject(charId).getJSONObject("stage").containsKey(stageId)) {
                    result = new JSONObject(true);
                    result.put(stageId, story);
                    char_data.put("stage", result);
                    UserSyncData.getJSONObject("troop").getJSONObject("addon").put(charId, char_data);
                    UserSyncData.getJSONObject("troop").put("addon", addon);
                }

                if (UserSyncData.getJSONObject("troop").getJSONObject("addon").getJSONObject(charId).getJSONObject("stage").getJSONObject(stageId).getIntValue("state") != 3) {
                    UserSyncData.getJSONObject("troop").getJSONObject("addon").getJSONObject(charId).getJSONObject("stage").getJSONObject(stageId).put("state", 1);
                }

                userDao.setUserData(uid, UserSyncData);
                result = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                JSONObject modified = new JSONObject(true);
                JSONObject troop = new JSONObject(true);
                troop.put("addon", addon);
                modified.put("troop", troop);
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                result.put("battleId", charId + "&" + stageId);
                return result;
            }
        }
    }

    @PostMapping(
            value = {"/addonStage/battleFinish"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject addonStageBattleFinish(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charBuild/addonStage/battleFinish");
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
                String stageId = BattleData.getString("battleId").substring(BattleData.getString("battleId").indexOf("&") + 1);
                String charId = BattleData.getString("battleId").substring(0, BattleData.getString("battleId").indexOf("&"));
                JSONObject stageData = UserSyncData.getJSONObject("troop").getJSONObject("addon").getJSONObject(charId).getJSONObject("stage").getJSONObject(stageId);
                int DropRate = ArknightsApplication.serverConfig.getJSONObject("battle").getIntValue("dropRate");
                int completeState = BattleData.getIntValue("completeState");
                if (ArknightsApplication.serverConfig.getJSONObject("battle").getBooleanValue("debug")) {
                    completeState = 3;
                }

                JSONArray firstRewards = new JSONArray();
                JSONObject diamondShard;
                JSONObject result;
                JSONObject playerDataDelta;
                JSONObject modified;
                JSONObject troop;
                if (completeState == 1) {
                    if (stageData.getIntValue("state") != 3) {
                        stageData.put("state", completeState);
                    }

                    diamondShard = new JSONObject(true);
                    result = new JSONObject(true);
                    playerDataDelta = new JSONObject(true);
                    modified = new JSONObject(true);
                    troop = new JSONObject(true);
                    troop.put(charId, UserSyncData.getJSONObject("troop").getJSONObject("addon").getJSONObject(charId));
                    modified.put("addon", troop);
                    playerDataDelta.put("troop", modified);
                    result.put("modified", playerDataDelta);
                    diamondShard.put("playerDataDelta", result);
                    diamondShard.put("firstRewards", firstRewards);
                    diamondShard.put("result", 0);
                    return diamondShard;
                } else if (stageData.getIntValue("state") == 3) {
                    diamondShard = new JSONObject(true);
                    result = new JSONObject(true);
                    playerDataDelta = new JSONObject(true);
                    modified = new JSONObject(true);
                    troop = new JSONObject(true);
                    troop.put(charId, UserSyncData.getJSONObject("troop").getJSONObject("addon").getJSONObject(charId));
                    modified.put("addon", troop);
                    playerDataDelta.put("troop", modified);
                    result.put("modified", playerDataDelta);
                    diamondShard.put("playerDataDelta", result);
                    diamondShard.put("firstRewards", firstRewards);
                    diamondShard.put("result", 0);
                    return diamondShard;
                } else {
                    diamondShard = new JSONObject(true);
                    diamondShard.put("count", 200 * DropRate);
                    diamondShard.put("id", "4003");
                    diamondShard.put("type", "DIAMOND_SHD");
                    firstRewards.add(diamondShard);
                    UserSyncData.getJSONObject("status").put("diamondShard", UserSyncData.getJSONObject("status").getIntValue("diamondShard") + 200 * DropRate);
                    stageData.put("state", completeState);
                    stageData.put("fts", (new Date()).getTime() / 1000L);
                    stageData.put("rts", (new Date()).getTime() / 1000L);
                    userDao.setUserData(uid, UserSyncData);
                    result = new JSONObject(true);
                    playerDataDelta = new JSONObject(true);
                    modified = new JSONObject(true);
                    troop = new JSONObject(true);
                    JSONObject addon = new JSONObject(true);
                    addon.put(charId, UserSyncData.getJSONObject("troop").getJSONObject("addon").getJSONObject(charId));
                    troop.put("addon", addon);
                    modified.put("troop", troop);
                    playerDataDelta.put("modified", modified);
                    result.put("playerDataDelta", playerDataDelta);
                    result.put("firstRewards", firstRewards);
                    result.put("result", 0);
                    return result;
                }
            }
        }
    }

    @PostMapping(
            value = {"/changeCharTemplate"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject changeCharTemplate(@RequestHeader(value = "secret", required = false)String secret,@RequestBody JSONObject JsonBody, HttpServletResponse response, HttpServletRequest request) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /charBuild/changeCharTemplate");
        int charInstId = JsonBody.getIntValue("charInstId");
        String templateId = JsonBody.getString("templateId");
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
                JSONObject tmplChar = UserSyncData.getJSONObject("troop").getJSONObject("chars")
                        .getJSONObject(String.valueOf(charInstId));
                JSONObject tmplMap = tmplChar.getJSONObject("tmpl");
                if (templateId == null || templateId.isEmpty()) {
                    JSONObject bad = new JSONObject(true);
                    bad.put("result", 1);
                    bad.put("error", "templateId 不能为空");
                    return bad;
                }
                // 只能切到已解锁的模板：模板 patch 是通关对应关卡时由 AmiyaTemplate 建出来的
                if (tmplMap == null || !tmplMap.containsKey(templateId)) {
                    JSONObject bad = new JSONObject(true);
                    bad.put("result", 1);
                    bad.put("error", "该模板尚未解锁: " + templateId);
                    return bad;
                }
                if (!templateId.equals(tmplChar.getString("currentTmpl"))) {
                    tmplChar.put("currentTmpl", templateId);
                }
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                JSONObject modified = new JSONObject(true);
                JSONObject playerDataDelta = new JSONObject(true);
                JSONObject troop = new JSONObject(true);
                JSONObject chars = new JSONObject(true);
                // 官方只回 currentTmpl，tmpl / equip / skin / skills 一律不动（2026-10-27 实测）
                JSONObject tmplDelta = new JSONObject(true);
                tmplDelta.put("currentTmpl", templateId);
                chars.put(String.valueOf(charInstId), tmplDelta);
                troop.put("chars", chars);
                modified.put("troop", troop);
                playerDataDelta.put("deleted", new JSONObject(true));
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            }
        }
    }
}
