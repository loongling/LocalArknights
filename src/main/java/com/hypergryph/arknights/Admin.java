package com.hypergryph.arknights;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.work.processArray;
import com.hypergryph.arknights.core.pojo.Account;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/admin"})
public class Admin {
    public Admin() {
    }

    public static void GM_GiveItem(JSONObject UserSyncData, String reward_id, String reward_type, int reward_count, JSONArray items) {
        JSONObject chars = UserSyncData.getJSONObject("troop").getJSONObject("chars");
        JSONObject troop = new JSONObject(true);
        JSONObject item;
        JSONObject consumable;
        // 干员发放的唯一实现已搬到 processArray.grantChar，这里只做转发
        if (reward_type.equals("CHAR")) {
            items.addAll(processArray.grantChar(UserSyncData, reward_id, reward_count));
        }

        if (reward_type.equals("HGG_SHD")) {
            UserSyncData.getJSONObject("status").put("practiceTicket", UserSyncData.getJSONObject("status").getIntValue("hggShard") + reward_count);
        }

        if (reward_type.equals("LGG_SHD")) {
            UserSyncData.getJSONObject("status").put("practiceTicket", UserSyncData.getJSONObject("status").getIntValue("lggShard") + reward_count);
        }

        if (reward_type.equals("MATERIAL")) {
            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
        }

        if (reward_type.equals("CARD_EXP")) {
            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
        }

        if (reward_type.equals("SOCIAL_PT")) {
            UserSyncData.getJSONObject("status").put("socialPoint", UserSyncData.getJSONObject("status").getIntValue("socialPoint") + reward_count);
        }

        if (reward_type.equals("AP_GAMEPLAY")) {
            UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("ap") + reward_count);
        }

        if (reward_type.equals("AP_ITEM")) {
            if (reward_id.contains("60")) {
                UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("ap") + 60);
            } else if (reward_id.contains("200")) {
                UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("ap") + 200);
            } else {
                UserSyncData.getJSONObject("status").put("ap", UserSyncData.getJSONObject("status").getIntValue("ap") + 100);
            }
        }

        if (reward_type.equals("TKT_TRY")) {
            UserSyncData.getJSONObject("status").put("practiceTicket", UserSyncData.getJSONObject("status").getIntValue("practiceTicket") + reward_count);
        }

        if (reward_type.equals("DIAMOND")) {
            UserSyncData.getJSONObject("status").put("androidDiamond", UserSyncData.getJSONObject("status").getIntValue("androidDiamond") + reward_count);
            UserSyncData.getJSONObject("status").put("iosDiamond", UserSyncData.getJSONObject("status").getIntValue("iosDiamond") + reward_count);
        }

        if (reward_type.equals("DIAMOND_SHD")) {
            UserSyncData.getJSONObject("status").put("diamondShard", UserSyncData.getJSONObject("status").getIntValue("diamondShard") + reward_count);
        }

        if (reward_type.equals("GOLD")) {
            UserSyncData.getJSONObject("status").put("gold", UserSyncData.getJSONObject("status").getIntValue("gold") + reward_count);
        }

        if (reward_type.equals("TKT_RECRUIT")) {
            UserSyncData.getJSONObject("status").put("recruitLicense", UserSyncData.getJSONObject("status").getIntValue("recruitLicense") + reward_count);
        }

        if (reward_type.equals("TKT_INST_FIN")) {
            UserSyncData.getJSONObject("status").put("instantFinishTicket", UserSyncData.getJSONObject("status").getIntValue("instantFinishTicket") + reward_count);
        }

        if (reward_type.equals("TKT_GACHA_PRSV")) {
            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
        }

        if (reward_type.equals("RENAMING_CARD")) {
            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
        }

        if (reward_type.equals("RETRO_COIN")) {
            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
        }

        if (reward_type.equals("AP_SUPPLY")) {
            UserSyncData.getJSONObject("inventory").put(reward_id, UserSyncData.getJSONObject("inventory").getIntValue(reward_id) + reward_count);
        }

        if (reward_type.equals("TKT_GACHA_10")) {
            UserSyncData.getJSONObject("status").put("tenGachaTicket", UserSyncData.getJSONObject("status").getIntValue("tenGachaTicket") + reward_count);
        }

        if (reward_type.equals("TKT_GACHA")) {
            UserSyncData.getJSONObject("status").put("gachaTicket", UserSyncData.getJSONObject("status").getIntValue("gachaTicket") + reward_count);
        }

        if (reward_type.indexOf("VOUCHER") != -1) {
            if (!UserSyncData.getJSONObject("consumable").containsKey(reward_id)) {
                item = new JSONObject(true);
                consumable = new JSONObject(true);
                consumable.put("ts", -1);
                consumable.put("count", 0);
                item.put("0", consumable);
                UserSyncData.getJSONObject("consumable").put(reward_id, item);
            }

            UserSyncData.getJSONObject("consumable").getJSONObject(reward_id).getJSONObject("0").put("count", UserSyncData.getJSONObject("consumable").getJSONObject(reward_id).getJSONObject("0").getIntValue("count") + reward_count);
        }

        if (reward_type.equals("CHAR_SKIN")) {
            UserSyncData.getJSONObject("skin").getJSONObject("characterSkins").put(reward_id, 1);
            UserSyncData.getJSONObject("skin").getJSONObject("skinTs").put(reward_id, (new Date()).getTime() / 1000L);
        }

        // 月卡兑换券（GP_Once_206 的 mcardVoucher）：立刻生效，不占背包
        if (reward_type.equals("MCARD_VOUCHER")) {
            long nowTs = (new Date()).getTime() / 1000L;
            for (int n = 0; n < Math.max(1, reward_count); n++) {
                com.hypergryph.arknights.core.monthlycard.MonthlyCard.activate(UserSyncData, nowTs);
            }
            com.hypergryph.arknights.core.monthlycard.MonthlyCard.grantDailyBonus(UserSyncData,
                    com.hypergryph.arknights.core.monthlycard.MonthlyCard.currentCardId());
        }

        if (!reward_type.equals("CHAR")) {
            item = new JSONObject(true);
            item.put("id", reward_id);
            item.put("type", reward_type);
            item.put("count", reward_count);
            items.add(item);
        }

    }

    @RequestMapping({"/send/character"})
    public JSONObject character(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid, @RequestParam String charId) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject result = new JSONObject(true);
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        } else {
            List<Account> user = userDao.queryAccountByUid(uid);
            JSONObject UserSyncData;
            if (user.size() != 1) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("code", 404);
                UserSyncData.put("msg", "无法找到该玩家的存档");
                return UserSyncData;
            } else {
                UserSyncData = JSONObject.parseObject(((Account)user.get(0)).getUser());
                JSONArray items = new JSONArray();
                GM_GiveItem(UserSyncData, charId, "CHAR", 1, items);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                result.put("code", 200);
                result.put("items", items);
                return result;
            }
        }
    }

    @RequestMapping({"/send/item"})
    public JSONObject item(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid, @RequestParam String itemType, @RequestParam String itemId, @RequestParam int count) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject result = new JSONObject(true);
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        } else {
            List<Account> user = userDao.queryAccountByUid(uid);
            JSONObject UserSyncData;
            if (user.size() != 1) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("code", 404);
                UserSyncData.put("msg", "无法找到该玩家的存档");
                return UserSyncData;
            } else {
                UserSyncData = JSONObject.parseObject(((Account)user.get(0)).getUser());
                JSONArray items = new JSONArray();
                GM_GiveItem(UserSyncData, itemId, itemType, count, items);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                result.put("code", 200);
                result.put("items", items);
                return result;
            }
        }
    }

    @RequestMapping({"/charBuild/changeLevel"})
    public JSONObject level(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid, @RequestParam String charId, @RequestParam int value) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject result = new JSONObject(true);
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountByUid(uid);
            JSONObject UserSyncData;
            if (Accounts.size() != 1) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("code", 404);
                UserSyncData.put("msg", "无法找到该玩家的存档");
                return UserSyncData;
            } else {
                UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                Iterator var7 = UserSyncData.getJSONObject("troop").getJSONObject("chars").entrySet().iterator();

                Map.Entry entry;
                JSONObject charData;
                do {
                    if (!var7.hasNext()) {
                        JSONObject result = new JSONObject(true);
                        result.put("code", 404);
                        result.put("msg", "该玩家尚未拥有 " + charId);
                        return result;
                    }

                    entry = (Map.Entry)var7.next();
                    charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(entry.getKey().toString());
                } while(!charData.getString("charId").equals(charId));

                charData.put("level", value);
                UserSyncData.getJSONObject("troop").getJSONObject("chars").put(entry.getKey().toString(), charData);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                result.put("code", 200);
                result.put("msg", "已改变 " + charId + " 的等级为" + value);
                return result;
            }
        }
    }

    @RequestMapping({"/charBuild/changeFavorPoint"})
    public JSONObject favorPoint(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid, @RequestParam String charId, @RequestParam int value) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject result = new JSONObject(true);
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountByUid(uid);
            JSONObject UserSyncData;
            if (Accounts.size() != 1) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("code", 404);
                UserSyncData.put("msg", "无法找到该玩家的存档");
                return UserSyncData;
            } else {
                UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                Iterator var7 = UserSyncData.getJSONObject("troop").getJSONObject("chars").entrySet().iterator();

                Map.Entry entry;
                JSONObject charData;
                do {
                    if (!var7.hasNext()) {
                        JSONObject result = new JSONObject(true);
                        result.put("code", 404);
                        result.put("msg", "该玩家尚未拥有 " + charId);
                        return result;
                    }

                    entry = (Map.Entry)var7.next();
                    charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(entry.getKey().toString());
                } while(!charData.getString("charId").equals(charId));

                charData.put("favorPoint", value);
                UserSyncData.getJSONObject("troop").getJSONObject("chars").put(entry.getKey().toString(), charData);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                result.put("code", 200);
                result.put("msg", "已改变 " + charId + " 的信赖为" + value);
                return result;
            }
        }
    }

    @RequestMapping({"/charBuild/changePotentialRank"})
    public JSONObject PotentialRank(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid, @RequestParam String charId, @RequestParam int value) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject result = new JSONObject(true);
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountByUid(uid);
            JSONObject UserSyncData;
            if (Accounts.size() != 1) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("code", 404);
                UserSyncData.put("msg", "无法找到该玩家的存档");
                return UserSyncData;
            } else {
                UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                Iterator var7 = UserSyncData.getJSONObject("troop").getJSONObject("chars").entrySet().iterator();

                Map.Entry entry;
                JSONObject charData;
                do {
                    if (!var7.hasNext()) {
                        JSONObject result = new JSONObject(true);
                        result.put("code", 404);
                        result.put("msg", "该玩家尚未拥有 " + charId);
                        return result;
                    }

                    entry = (Map.Entry)var7.next();
                    charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(entry.getKey().toString());
                } while(!charData.getString("charId").equals(charId));

                charData.put("potentialRank", value);
                UserSyncData.getJSONObject("troop").getJSONObject("chars").put(entry.getKey().toString(), charData);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                result.put("code", 200);
                result.put("msg", "已改变 " + charId + " 的潜能为 " + value);
                return result;
            }
        }
    }

    @RequestMapping({"/charBuild/changeMainSkillLvl"})
    public JSONObject mainSkillLvl(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid, @RequestParam String charId, @RequestParam int value) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject result = new JSONObject(true);
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountByUid(uid);
            JSONObject UserSyncData;
            if (Accounts.size() != 1) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("code", 404);
                UserSyncData.put("msg", "无法找到该玩家的存档");
                return UserSyncData;
            } else {
                UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                Iterator var7 = UserSyncData.getJSONObject("troop").getJSONObject("chars").entrySet().iterator();

                Map.Entry entry;
                JSONObject charData;
                do {
                    if (!var7.hasNext()) {
                        JSONObject result = new JSONObject(true);
                        result.put("code", 404);
                        result.put("msg", "该玩家尚未拥有 " + charId);
                        return result;
                    }

                    entry = (Map.Entry)var7.next();
                    charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(entry.getKey().toString());
                } while(!charData.getString("charId").equals(charId));

                charData.put("mainSkillLvl", value);
                UserSyncData.getJSONObject("troop").getJSONObject("chars").put(entry.getKey().toString(), charData);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                result.put("code", 200);
                result.put("msg", "已改变 " + charId + " 的技能等级为 " + value);
                return result;
            }
        }
    }

    @RequestMapping({"/charBuild/changeExp"})
    public JSONObject Exp(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid, @RequestParam String charId, @RequestParam int value) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject result = new JSONObject(true);
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountByUid(uid);
            JSONObject UserSyncData;
            if (Accounts.size() != 1) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("code", 404);
                UserSyncData.put("msg", "无法找到该玩家的存档");
                return UserSyncData;
            } else {
                UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                Iterator var7 = UserSyncData.getJSONObject("troop").getJSONObject("chars").entrySet().iterator();

                Map.Entry entry;
                JSONObject charData;
                do {
                    if (!var7.hasNext()) {
                        JSONObject result = new JSONObject(true);
                        result.put("code", 404);
                        result.put("msg", "该玩家尚未拥有 " + charId);
                        return result;
                    }

                    entry = (Map.Entry)var7.next();
                    charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(entry.getKey().toString());
                } while(!charData.getString("charId").equals(charId));

                charData.put("exp", value);
                UserSyncData.getJSONObject("troop").getJSONObject("chars").put(entry.getKey().toString(), charData);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                result.put("code", 200);
                result.put("msg", "已改变 " + charId + " 的经验为 " + value);
                return result;
            }
        }
    }

    @RequestMapping({"/building/resetAp"})
    public JSONObject resetBuildingAp(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid) {
        JSONObject result = new JSONObject(true);
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        }
        List<Account> Accounts = userDao.queryAccountByUid(uid);
        if (Accounts.size() != 1) {
            result.put("code", 404);
            result.put("msg", "无法找到该玩家的存档");
            return result;
        }
        Account account = Accounts.get(0);
        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject building = userData.getJSONObject("building");
        if (building == null || building.getJSONObject("chars") == null) {
            result.put("code", 404);
            result.put("msg", "没有基建数据");
            return result;
        }
        long now = com.hypergryph.arknights.ArknightsApplication.getTimestamp();
        JSONObject chars = building.getJSONObject("chars");
        for (String key : chars.keySet()) {
            JSONObject c = chars.getJSONObject(key);
            if (c == null) {
                continue;
            }
            c.put("ap", 8640000);
            c.put("lastApAddTime", now);
            c.put("workTime", 0);
            c.put("changeScale", 0);
        }
        userDao.setUserData(account.getUid(), userData);
        result.put("code", 200);
        result.put("msg", "已把 " + chars.size() + " 个基建干员的心情补满（ap=8640000）");
        return result;
    }

    @RequestMapping({"/charBuild/changeEvolvePhase"})
    public JSONObject evolvePhase(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid, @RequestParam String charId, @RequestParam int value) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject result = new JSONObject(true);
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountByUid(uid);
            JSONObject UserSyncData;
            if (Accounts.size() != 1) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("code", 404);
                UserSyncData.put("msg", "无法找到该玩家的存档");
                return UserSyncData;
            } else {
                UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                Iterator var7 = UserSyncData.getJSONObject("troop").getJSONObject("chars").entrySet().iterator();

                Map.Entry entry;
                JSONObject charData;
                do {
                    if (!var7.hasNext()) {
                        JSONObject result = new JSONObject(true);
                        result.put("code", 404);
                        result.put("msg", "该玩家尚未拥有 " + charId);
                        return result;
                    }

                    entry = (Map.Entry)var7.next();
                    charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(entry.getKey().toString());
                } while(!charData.getString("charId").equals(charId));

                charData.put("evolvePhase", value);
                UserSyncData.getJSONObject("troop").getJSONObject("chars").put(entry.getKey().toString(), charData);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                result.put("code", 200);
                result.put("msg", "已改变 " + charId + " 的精英化等级为 " + value);
                return result;
            }
        }
    }

    @RequestMapping({"/charBuild/changeDefaultSkillIndex"})
    public JSONObject defaultSkillIndex(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid, @RequestParam String charId, @RequestParam int value) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject result = new JSONObject(true);
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountByUid(uid);
            JSONObject UserSyncData;
            if (Accounts.size() != 1) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("code", 404);
                UserSyncData.put("msg", "无法找到该玩家的存档");
                return UserSyncData;
            } else {
                UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                Iterator var7 = UserSyncData.getJSONObject("troop").getJSONObject("chars").entrySet().iterator();

                Map.Entry entry;
                JSONObject charData;
                do {
                    if (!var7.hasNext()) {
                        JSONObject result = new JSONObject(true);
                        result.put("code", 404);
                        result.put("msg", "该玩家尚未拥有 " + charId);
                        return result;
                    }

                    entry = (Map.Entry)var7.next();
                    charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(entry.getKey().toString());
                } while(!charData.getString("charId").equals(charId));

                charData.put("defaultSkillIndex", value);
                UserSyncData.getJSONObject("troop").getJSONObject("chars").put(entry.getKey().toString(), charData);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                result.put("code", 200);
                result.put("msg", "已改变 " + charId + " 的默认技能为 " + value);
                return result;
            }
        }
    }

    @RequestMapping({"/charBuild/changeSkin"})
    public JSONObject skin(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid, @RequestParam String charId, @RequestParam String value) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject result = new JSONObject(true);
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountByUid(uid);
            JSONObject UserSyncData;
            if (Accounts.size() != 1) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("code", 404);
                UserSyncData.put("msg", "无法找到该玩家的存档");
                return UserSyncData;
            } else {
                UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                Iterator var7 = UserSyncData.getJSONObject("troop").getJSONObject("chars").entrySet().iterator();

                Map.Entry entry;
                JSONObject charData;
                do {
                    if (!var7.hasNext()) {
                        JSONObject result = new JSONObject(true);
                        result.put("code", 404);
                        result.put("msg", "该玩家尚未拥有 " + charId);
                        return result;
                    }

                    entry = (Map.Entry)var7.next();
                    charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(entry.getKey().toString());
                } while(!charData.getString("charId").equals(charId));

                charData.put("skin", value);
                UserSyncData.getJSONObject("troop").getJSONObject("chars").put(entry.getKey().toString(), charData);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                result.put("code", 200);
                result.put("msg", "已改变 " + charId + " 的默认皮肤为 " + value);
                return result;
            }
        }
    }

    @RequestMapping({"/charBuild/unlockAllSkills"})
    public JSONObject unlockAllSkills(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid, @RequestParam String charId) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject result = new JSONObject(true);
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountByUid(uid);
            JSONObject UserSyncData;
            if (Accounts.size() != 1) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("code", 404);
                UserSyncData.put("msg", "无法找到该玩家的存档");
                return UserSyncData;
            } else {
                UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                Iterator var6 = UserSyncData.getJSONObject("troop").getJSONObject("chars").entrySet().iterator();

                Map.Entry entry;
                JSONObject charData;
                do {
                    if (!var6.hasNext()) {
                        JSONObject result = new JSONObject(true);
                        result.put("code", 404);
                        result.put("msg", "该玩家尚未拥有 " + charId);
                        return result;
                    }

                    entry = (Map.Entry)var6.next();
                    charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(entry.getKey().toString());
                } while(!charData.getString("charId").equals(charId));

                // 模板干员的技能在 tmpl 各 patch 里，基节点 skills 是空的
                for (com.alibaba.fastjson.JSONArray skillArr : com.hypergryph.arknights.core.work.processArray.allSkillArrays(charData)) {
                    for (int i = 0; i < skillArr.size(); ++i) {
                        skillArr.getJSONObject(i).put("unlock", 1);
                    }
                }

                UserSyncData.getJSONObject("troop").getJSONObject("chars").put(entry.getKey().toString(), charData);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                result.put("code", 200);
                result.put("msg", "已解锁 " + charId + " 的所有技能");
                return result;
            }
        }
    }

    @RequestMapping({"/charBuild/changeSpecializeLevel"})
    public JSONObject UpSpecializeLevel(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid, @RequestParam String charId, @RequestParam int value) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject result = new JSONObject(true);
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        } else {
            List<Account> Accounts = userDao.queryAccountByUid(uid);
            JSONObject UserSyncData;
            if (Accounts.size() != 1) {
                UserSyncData = new JSONObject(true);
                UserSyncData.put("code", 404);
                UserSyncData.put("msg", "无法找到该玩家的存档");
                return UserSyncData;
            } else {
                UserSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
                Iterator var7 = UserSyncData.getJSONObject("troop").getJSONObject("chars").entrySet().iterator();

                Map.Entry entry;
                JSONObject charData;
                do {
                    if (!var7.hasNext()) {
                        JSONObject result = new JSONObject(true);
                        result.put("code", 404);
                        result.put("msg", "该玩家尚未拥有 " + charId);
                        return result;
                    }

                    entry = (Map.Entry)var7.next();
                    charData = UserSyncData.getJSONObject("troop").getJSONObject("chars").getJSONObject(entry.getKey().toString());
                } while(!charData.getString("charId").equals(charId));

                for (com.alibaba.fastjson.JSONArray skillArr : com.hypergryph.arknights.core.work.processArray.allSkillArrays(charData)) {
                    for (int i = 0; i < skillArr.size(); ++i) {
                        skillArr.getJSONObject(i).put("specializeLevel", value);
                    }
                }

                UserSyncData.getJSONObject("troop").getJSONObject("chars").put(entry.getKey().toString(), charData);
                userDao.setUserData(uid, UserSyncData);
                JSONObject result = new JSONObject(true);
                result.put("code", 200);
                result.put("msg", "已把 " + charId + " 的所有技能专精提升至 " + value);
                return result;
            }
        }
    }
    /**
     * 给某个玩家发一封邮件（模板来自 MySQL mail 表，可重复发）。
     * <pre>POST /admin/mail/send?uid=10000001&mailId=1   Header: GMKey</pre>
     */
    @RequestMapping({"/mail/send"})
    public JSONObject sendMail(@RequestHeader("GMKey") String GMKey,
                               @RequestParam Long uid,
                               @RequestParam(required = false) Integer mailId,
                               @RequestParam(required = false) String templateName,
                               @RequestParam(required = false, defaultValue = "365") Long expireDays,
                               @RequestParam(required = false) String from) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject denied = new JSONObject(true);
            denied.put("code", 401);
            denied.put("msg", "Unauthorized");
            return denied;
        }
        if (mailId == null && (templateName == null || templateName.isBlank())) {
            JSONObject bad = new JSONObject(true);
            bad.put("code", 400);
            bad.put("msg", "mailId 或 templateName 必须传一个");
            return bad;
        }
        JSONObject r = com.hypergryph.arknights.core.mail.MailService.send(uid, mailId, templateName, expireDays, from);
        if (r.getIntValue("result") != 0) {
            JSONObject fail = new JSONObject(true);
            fail.put("code", 404);
            fail.put("msg", r.getString("error"));
            return fail;
        }
        return r;
    }

    /**
     * 把 data/mail/mail_templates.json 导入 mail 表（按 name 幂等：有则更新、无则插入）。
     * type=2 的调研模板跳过——那些进 mail_survey 表。
     */
    @RequestMapping({"/mail/importTemplates"})
    public JSONObject importTemplates(@RequestHeader("GMKey") String GMKey) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject denied = new JSONObject(true);
            denied.put("code", 401);
            denied.put("msg", "Unauthorized");
            return denied;
        }
        String path = System.getProperty("user.dir") + "/data/mail/mail_templates.json";
        JSONObject file;
        try {
            file = com.hypergryph.arknights.core.file.IOTools.ReadJsonFile(path);
        } catch (Exception e) {
            JSONObject bad = new JSONObject(true);
            bad.put("code", 500);
            bad.put("msg", "读模板文件失败: " + e.getMessage());
            return bad;
        }
        JSONArray templates = file == null ? null : file.getJSONArray("templates");
        if (templates == null) {
            JSONObject bad = new JSONObject(true);
            bad.put("code", 404);
            bad.put("msg", "模板文件里没有 templates 数组");
            return bad;
        }
        JSONArray imported = new JSONArray();
        int skipped = 0;
        for (int i = 0; i < templates.size(); i++) {
            JSONObject t = templates.getJSONObject(i);
            if (t == null || t.getString("name") == null) {
                continue;
            }
            if (t.getIntValue("type") == 2 || t.getString("surveyMailId") != null) {
                skipped++;
                continue;
            }
            com.hypergryph.arknights.core.dao.mailDao.upsertTemplate(
                    t.getString("name"),
                    t.getString("from"),
                    t.getString("subject"),
                    t.getString("content"),
                    t.getJSONArray("items"));
            imported.add(t.getString("name"));
        }
        JSONObject result = new JSONObject(true);
        result.put("code", 200);
        result.put("imported", imported);
        result.put("skippedSurveys", skipped);
        result.put("msg", "已导入 " + imported.size() + " 个模板，跳过 " + skipped + " 个调研模板");
        return result;
    }

    /** 重发调研邮件：抹掉墓碑，下次读邮箱时按模板重新补一封 */
    @RequestMapping({"/mail/resetSurvey"})
    public JSONObject resetSurvey(@RequestHeader("GMKey") String GMKey,
                                  @RequestParam Long uid,
                                  @RequestParam String surveyMailId) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject denied = new JSONObject(true);
            denied.put("code", 401);
            denied.put("msg", "Unauthorized");
            return denied;
        }
        int n = com.hypergryph.arknights.core.dao.mailInstanceDao.resetSurvey(uid, surveyMailId);
        JSONObject r = new JSONObject(true);
        r.put("code", 200);
        r.put("deleted", n);
        r.put("msg", "已抹掉 " + surveyMailId + " 的墓碑，玩家下次读邮箱会重新收到");
        return r;
    }

    /** 看某个玩家现在有哪些邮件 */
    @RequestMapping({"/mail/list"})
    public JSONObject listMails(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject denied = new JSONObject(true);
            denied.put("code", 401);
            denied.put("msg", "Unauthorized");
            return denied;
        }
        JSONArray mails = com.hypergryph.arknights.core.mail.MailService.mailsOf(uid);
        if (mails == null) {
            JSONObject notFound = new JSONObject(true);
            notFound.put("code", 404);
            notFound.put("msg", "无法找到该玩家的存档");
            return notFound;
        }
        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("mails", mails);
        return result;
    }

    /** 删掉某个玩家的一封邮件 */
    @RequestMapping({"/mail/delete"})
    public JSONObject deleteMail(@RequestHeader("GMKey") String GMKey,
                                 @RequestParam Long uid,
                                 @RequestParam Integer mailId) {
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            JSONObject denied = new JSONObject(true);
            denied.put("code", 401);
            denied.put("msg", "Unauthorized");
            return denied;
        }
        int n = com.hypergryph.arknights.core.mail.MailService.delete(uid, mailId);
        if (n < 0) {
            JSONObject notFound = new JSONObject(true);
            notFound.put("code", 404);
            notFound.put("msg", "无法找到该玩家的存档");
            return notFound;
        }
        if (n == 0) {
            JSONObject bad = new JSONObject(true);
            bad.put("code", 404);
            bad.put("msg", "这个玩家没有 mailId=" + mailId);
            return bad;
        }
        JSONObject result = new JSONObject(true);
        result.put("result", 0);
        result.put("remaining", com.hypergryph.arknights.core.mail.MailService.mailsOf(uid).size());
        return result;
    }
}
