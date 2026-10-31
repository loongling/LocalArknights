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

    /**
     * 开发用：把某个槽位强制设成指定房间（跳过材料/建造流程）。
     * POST /admin/building/forceRoom?uid=10000001&slotId=slot_28&roomId=DORMITORY&level=1
     */
    /**
     * 强制清空留言板待领状态（消红点）：sp.thisWeek=0、lastShowTs=now、所有访客标记已领。
     * POST /admin/building/clearMessageBoard?uid=10000001
     */
    @RequestMapping({"/building/clearMessageBoard"})
    public JSONObject clearMessageBoard(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid) {
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
        JSONObject rooms = userData.getJSONObject("building").getJSONObject("rooms");
        JSONObject bucket = rooms == null ? null : rooms.getJSONObject("MEETING");
        if (bucket == null || bucket.isEmpty()) {
            result.put("code", 404);
            result.put("msg", "没有会客室数据");
            return result;
        }
        long now = com.hypergryph.arknights.ArknightsApplication.getTimestamp();
        for (String slotId : bucket.keySet()) {
            JSONObject node = bucket.getJSONObject(slotId);
            if (node == null) continue;
            node.put("received", 0);
            JSONObject leave = node.getJSONObject("messageLeave");
            if (leave == null) {
                leave = new JSONObject(true);
                node.put("messageLeave", leave);
            }
            leave.put("lastVisitTs", now);
            leave.put("lastShowTs", now);
            leave.put("inUse", true);
            JSONObject sp = leave.getJSONObject("sp");
            if (sp == null) {
                sp = new JSONObject(true);
                sp.put("lastWeek", 0);
                sp.put("lastWeekSum", 0);
                leave.put("sp", sp);
            }
            sp.put("thisWeek", 0);
            sp.put("lastWeek", 0);
        }
        userDao.setUserData(account.getUid(), userData);
        try {
            com.hypergryph.arknights.core.dao.visitDao.markClaimed(account.getUid());
        } catch (Exception ignored) {
        }
        result.put("code", 200);
        result.put("msg", "留言板待领状态已清空 lastVisitTs=lastShowTs=" + now);
        return result;
    }

    /**
     * 手动修单个账号的剿灭关卡记录：只保留「该号真正解锁到的那一关」，
     * 其余 camp_* 条目删掉（之前一次性补 37 条会让客户端把剿灭全部显示成已解锁）。
     * POST /admin/account/fixCampaignStages?uid=10000001&keep=camp_01
     */
    @RequestMapping({"/account/fixCampaignStages"})
    public JSONObject fixCampaignStages(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid,
                                        @RequestParam(required = false, defaultValue = "camp_01") String keep) {
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

        JSONObject dungeon = userData.getJSONObject("dungeon");
        if (dungeon == null) {
            dungeon = new JSONObject(true);
            userData.put("dungeon", dungeon);
        }
        JSONObject stages = dungeon.getJSONObject("stages");
        if (stages == null) {
            stages = new JSONObject(true);
            dungeon.put("stages", stages);
        }
        // ① 删掉没打过（completeTimes=0 且 startTimes=0）且不在保留名单里的 camp_* 条目
        java.util.Set<String> keepSet = new java.util.HashSet<>();
        for (String s : keep.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                keepSet.add(t);
            }
        }
        int removed = 0;
        for (String id : new java.util.ArrayList<>(stages.keySet())) {
            if (!id.startsWith("camp_") || keepSet.contains(id)) {
                continue;
            }
            JSONObject one = stages.getJSONObject(id);
            if (one == null) {
                continue;
            }
            if (one.getIntValue("completeTimes") == 0 && one.getIntValue("startTimes") == 0) {
                stages.remove(id);
                removed++;
            }
        }
        // ② 保留的关卡没有条目就补上（与官服 camp_01 字段一致）
        int added = 0;
        for (String id : keepSet) {
            if (stages.containsKey(id)) {
                continue;
            }
            JSONObject one = new JSONObject(true);
            one.put("stageId", id);
            one.put("completeTimes", 0);
            one.put("startTimes", 0);
            one.put("practiceTimes", 0);
            one.put("state", 0);
            one.put("hasBattleReplay", 0);
            stages.put(id, one);
            added++;
        }

        // ② mainStageProgress 按「已通关的最高主线关卡」推下一关（不能是 tr_08 这种教学关）
        int progressFixed = 0;
        JSONObject status = userData.getJSONObject("status");
        if (status != null) {
            java.util.List<String> cleared = new java.util.ArrayList<>();
            for (String id : stages.keySet()) {
                if (id.startsWith("main_") && id.indexOf('#') < 0
                        && stages.getJSONObject(id).getIntValue("state") == 3) {
                    cleared.add(id);
                }
            }
            java.util.Collections.sort(cleared);
            if (!cleared.isEmpty()) {
                String highest = cleared.get(cleared.size() - 1);
                String next = highest;
                try {
                    String path = System.getProperty("user.dir") + "/data/excel/stage_table.json";
                    JSONObject table = JSONObject.parseObject(new String(
                            java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                            java.nio.charset.StandardCharsets.UTF_8)).getJSONObject("stages");
                    java.util.List<String> allMain = new java.util.ArrayList<>();
                    for (String id : table.keySet()) {
                        if (id.startsWith("main_") && id.indexOf('#') < 0) {
                            allMain.add(id);
                        }
                    }
                    java.util.Collections.sort(allMain);
                    int idx = allMain.indexOf(highest);
                    if (idx >= 0 && idx + 1 < allMain.size()) {
                        next = allMain.get(idx + 1);
                    }
                } catch (Exception e) {
                    ArknightsApplication.LOGGER.warn("[GM] 读取 stage_table 失败: " + e.toString());
                }
                String msp = status.getString("mainStageProgress");
                if (msp == null || !msp.equals(next)) {
                    status.put("mainStageProgress", next);
                    progressFixed = 1;
                }
            }
        }

        // ③ campaignsV2 补齐成官服模板
        int campaignFixed = com.hypergryph.arknights.core.work.processArray.repairCampaignsV2(userData);

        userDao.setUserData(account.getUid(), userData);
        result.put("code", 200);
        result.put("msg", "uid " + uid + " 已修复：补剿灭关卡 " + added + " 条，mainStageProgress "
                + progressFixed + " 处，剿灭结构 " + campaignFixed + " 处");
        return result;
    }

    /**
     * 重置某个剿灭关的击杀记录与破关奖励领取状态（测试用）。
     * POST /admin/campaign/resetReward?uid=10000001&stage=camp_01
     */
    @RequestMapping({"/campaign/resetReward"})
    public JSONObject resetCampaignReward(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid,
                                          @RequestParam String stage) {
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
        JSONObject camp = userData.getJSONObject("campaignsV2");
        JSONObject inst = camp == null ? null
                : camp.getJSONObject("instances") == null ? null
                : camp.getJSONObject("instances").getJSONObject(stage);
        if (inst == null) {
            result.put("code", 404);
            result.put("msg", "该账号没有 " + stage + " 的剿灭记录");
            return result;
        }
        inst.put("maxKills", 0);
        com.alibaba.fastjson.JSONArray rs = new com.alibaba.fastjson.JSONArray();
        for (int i = 0; i < 8; i++) {
            rs.add(0);
        }
        inst.put("rewardStatus", rs);
        userDao.setUserData(account.getUid(), userData);
        result.put("code", 200);
        result.put("msg", uid + " 的 " + stage + " 已重置（maxKills=0，rewardStatus 全 0）");
        return result;
    }

    /**
     * 手动跑一次剿灭解锁判定（按 stage_table 的 unlockCondition），用于已有存档。
     * POST /admin/campaign/syncUnlock?uid=10000001
     */
    @RequestMapping({"/campaign/syncUnlock"})
    public JSONObject syncCampaignUnlock(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid,
                                         @RequestParam(required = false, defaultValue = "false") Boolean prune) {
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
        int removed = 0;
        if (Boolean.TRUE.equals(prune)) {
            // 先把「没打过又不满足解锁条件」的 camp_* 清掉，再按条件重新解锁
            JSONObject stages = userData.getJSONObject("dungeon").getJSONObject("stages");
            for (String id : new java.util.ArrayList<>(stages.keySet())) {
                if (!id.startsWith("camp_")) {
                    continue;
                }
                JSONObject one = stages.getJSONObject(id);
                if (one.getIntValue("completeTimes") == 0 && one.getIntValue("startTimes") == 0) {
                    stages.remove(id);
                    removed++;
                }
            }
        }
        int added = com.hypergryph.arknights.game.quest.unlockCampaignStages(userData, null, null);
        // 永久剿灭（camp_01/02/03）由主表 stage.json 的 sub/star/next 链决定，这里一并重算
        int mainAdded = com.hypergryph.arknights.game.quest.applyStageUnlocks(userData);
        userDao.setUserData(account.getUid(), userData);
        JSONObject stages = userData.getJSONObject("dungeon").getJSONObject("stages");
        java.util.List<String> camp = new java.util.ArrayList<>();
        for (String id : stages.keySet()) {
            if (id.startsWith("camp_")) {
                camp.add(id);
            }
        }
        java.util.Collections.sort(camp);
        result.put("code", 200);
        result.put("msg", "uid " + uid + " 清理 " + removed + " 张、campaignStages 解锁 " + added
                + " 张、主表链解锁 " + mainAdded + " 张，当前共 " + camp.size() + " 张");
        result.put("campaignStages", camp);
        return result;
    }

    /**
     * 标记主线关卡通关（用于验证剿灭解锁规则）。
     * 1-5 解锁常驻剿灭，2-8 解锁轮换/训练场。
     * POST /admin/campaign/unlock?uid=10000000&stage=main_01-05
     */
    @RequestMapping({"/campaign/unlock"})
    public JSONObject unlockCampaign(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid,
                                     @RequestParam(required = false, defaultValue = "main_01-05") String stage) {
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
        JSONObject stageMap = userData.getJSONObject("stage");
        if (stageMap == null) {
            stageMap = new JSONObject(true);
            userData.put("stage", stageMap);
        }
        JSONObject one = new JSONObject(true);
        one.put("stageId", stage);
        one.put("completedTimes", 1);
        one.put("practiceTimes", 0);
        one.put("state", 3);
        one.put("progress", 100);
        one.put("hasBattleReplay", 0);
        stageMap.put(stage, one);
        userDao.setUserData(account.getUid(), userData);
        result.put("code", 200);
        result.put("msg", "已标记 " + stage + " 通关（1-5 解锁常驻剿灭，2-8 解锁轮换/训练场）");
        return result;
    }

    /**
     * 把会客室 lastUpdateTime 往前推 N 小时（测试自动收集线索用）。
     * POST /admin/building/fastForward?uid=10000001&hours=20
     */
    @RequestMapping({"/building/fastForward"})
    public JSONObject fastForward(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid,
                                  @RequestParam(required = false, defaultValue = "20") Integer hours) {
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
        JSONObject rooms = userData.getJSONObject("building").getJSONObject("rooms");
        long back = (long) (hours == null ? 20 : hours) * 3600L;
        int n = 0;
        for (String type : new String[]{"MEETING", "MANUFACTURE", "TRADING"}) {
            JSONObject bucket = rooms == null ? null : rooms.getJSONObject(type);
            if (bucket == null) continue;
            for (String slotId : bucket.keySet()) {
                JSONObject node = bucket.getJSONObject(slotId);
                if (node == null) continue;
                long last = node.getLongValue("lastUpdateTime");
                if (last > 0) {
                    node.put("lastUpdateTime", last - back);
                    n++;
                }
            }
        }
        userDao.setUserData(account.getUid(), userData);
        result.put("code", 200);
        result.put("msg", "已把 " + n + " 个房间的 lastUpdateTime 往前推 " + hours + " 小时");
        return result;
    }

    /**
     * 把留言板 lastUpdateSpTs 往前推 N 天（测试「自然周翻转」用）。
     * POST /admin/building/shiftSpWeek?uid=10000001&days=8
     */
    @RequestMapping({"/building/shiftSpWeek"})
    public JSONObject shiftSpWeek(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid,
                                  @RequestParam(required = false, defaultValue = "8") Integer days) {
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
        JSONObject rooms = userData.getJSONObject("building").getJSONObject("rooms");
        JSONObject bucket = rooms == null ? null : rooms.getJSONObject("MEETING");
        if (bucket == null || bucket.isEmpty()) {
            result.put("code", 404);
            result.put("msg", "没有会客室数据");
            return result;
        }
        long now = com.hypergryph.arknights.ArknightsApplication.getTimestamp();
        int n = 0;
        for (String slotId : bucket.keySet()) {
            JSONObject node = bucket.getJSONObject(slotId);
            if (node == null) continue;
            JSONObject leave = node.getJSONObject("messageLeave");
            if (leave == null) continue;
            leave.put("lastUpdateSpTs", now - (long) (days == null ? 8 : days) * 86400L);
            n++;
        }
        userDao.setUserData(account.getUid(), userData);
        result.put("code", 200);
        result.put("msg", "已把 " + n + " 个会客室的 lastUpdateSpTs 往前推 " + days + " 天");
        return result;
    }

    /**
     * 造访客记录（测试多访客用）。
     * POST /admin/building/fakeVisit?host=10000001&visitor=10000009&nick=测试&count=1
     */
    @RequestMapping({"/building/fakeVisit"})
    public JSONObject fakeVisit(@RequestHeader("GMKey") String GMKey,
                                @RequestParam Long host,
                                @RequestParam Long visitor,
                                @RequestParam(required = false, defaultValue = "访客") String nick,
                                @RequestParam(required = false, defaultValue = "1") Integer count) {
        JSONObject result = new JSONObject(true);
        if (!ArknightsApplication.serverConfig.getJSONObject("server").getString("GMKey").equals(GMKey)) {
            result.put("code", 401);
            result.put("msg", "Unauthorized");
            return result;
        }
        long now = com.hypergryph.arknights.ArknightsApplication.getTimestamp();
        int n = Math.max(1, count == null ? 1 : count);
        for (int i = 0; i < n; i++) {
            com.hypergryph.arknights.core.dao.visitDao.addVisit(host, visitor + i, nick + (n > 1 ? (i + 1) : ""),
                    "0000", 1, "avatar_def_10", now - i);
        }
        // 同步模拟真实访问对宿主留言板的影响：lastVisitTs=now、sp.thisWeek += 30*n
        List<Account> hosts = userDao.queryAccountByUid(host);
        if (hosts.size() == 1) {
            Account hostAcc = hosts.get(0);
            JSONObject hostData = JSONObject.parseObject(hostAcc.getUser());
            JSONObject rooms = hostData.getJSONObject("building").getJSONObject("rooms");
            JSONObject bucket = rooms == null ? null : rooms.getJSONObject("MEETING");
            if (bucket != null) {
                for (String slotId : bucket.keySet()) {
                    JSONObject node = bucket.getJSONObject(slotId);
                    if (node == null) continue;
                    JSONObject leave = node.getJSONObject("messageLeave");
                    if (leave == null) {
                        leave = new JSONObject(true);
                        leave.put("inUse", true);
                        leave.put("lastShowTs", 0);
                        leave.put("lastUpdateSpTs", now);
                        node.put("messageLeave", leave);
                    }
                    leave.put("lastVisitTs", now);
                    JSONObject sp = leave.getJSONObject("sp");
                    if (sp == null) {
                        sp = new JSONObject(true);
                        sp.put("lastWeek", 0);
                        sp.put("lastWeekSum", 0);
                        leave.put("sp", sp);
                    }
                    sp.put("thisWeek", sp.getIntValue("thisWeek") + 30 * n);
                }
            }
            userDao.setUserData(hostAcc.getUid(), hostData);
        }
        result.put("code", 200);
        result.put("msg", "已写入 " + n + " 条访客记录 host=" + host + "（并点亮留言板）");
        return result;
    }

    /**
     * 会客室：把库存里「阵营重复/板上已有」的线索重掷成缺少的阵营，方便测试一键放置。
     * POST /admin/building/rerollClues?uid=10000001
     */
    @RequestMapping({"/building/rerollClues"})
    public JSONObject rerollClues(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid) {
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
        JSONObject rooms = userData.getJSONObject("building").getJSONObject("rooms");
        JSONObject bucket = rooms == null ? null : rooms.getJSONObject("MEETING");
        if (bucket == null || bucket.isEmpty()) {
            result.put("code", 404);
            result.put("msg", "没有会客室数据");
            return result;
        }
        String[] all = {"RHINE", "PENGUIN", "BLACKSTEEL", "URSUS", "GLASGOW", "KJERAG", "RHODES"};
        int changed = 0;
        for (String slotId : bucket.keySet()) {
            JSONObject node = bucket.getJSONObject(slotId);
            if (node == null) continue;
            JSONObject board = node.getJSONObject("board");
            JSONArray stock = node.getJSONArray("ownStock");
            if (stock == null) continue;
            // 标记板上和库存里已用掉的阵营
            java.util.Set<String> used = new java.util.HashSet<>();
            if (board != null) {
                used.addAll(board.keySet());
            }
            for (int i = 0; i < stock.size(); i++) {
                JSONObject c = stock.getJSONObject(i);
                if (c == null || c.getIntValue("inUse") == 1) continue;
                String t = c.getString("type");
                if (t != null && !used.contains(t)) {
                    used.add(t);
                    continue;
                }
                // 阵营重复 → 换一个还没用的
                for (String f : all) {
                    if (!used.contains(f)) {
                        c.put("type", f);
                        c.put("id", String.valueOf(account.getUid()) + "#" + (i + 1) + "#"
                                + com.hypergryph.arknights.ArknightsApplication.getTimestamp());
                        used.add(f);
                        changed++;
                        break;
                    }
                }
            }
        }
        userDao.setUserData(account.getUid(), userData);
        result.put("code", 200);
        result.put("msg", "重掷了 " + changed + " 条重复阵营的线索");
        return result;
    }

    /**
     * 会客室：重置每日线索产出（把 clueProduceTs 清零、dailyReward 清空），下次 sync 会重新产出待领线索。
     * POST /admin/building/resetDailyClue?uid=10000001
     */
    @RequestMapping({"/building/resetDailyClue"})
    public JSONObject resetDailyClue(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid) {
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
        JSONObject rooms = userData.getJSONObject("building").getJSONObject("rooms");
        JSONObject bucket = rooms == null ? null : rooms.getJSONObject("MEETING");
        if (bucket == null || bucket.isEmpty()) {
            result.put("code", 404);
            result.put("msg", "没有会客室数据");
            return result;
        }
        int n = 0;
        for (String slotId : bucket.keySet()) {
            JSONObject node = bucket.getJSONObject(slotId);
            if (node == null) continue;
            node.put("clueProduceTs", 0L);
            node.put("dailyReward", null);
            n++;
        }
        userDao.setUserData(account.getUid(), userData);
        result.put("code", 200);
        result.put("msg", "已重置 " + n + " 个会客室的每日线索");
        return result;
    }

    /**
     * 会客室线索交流：直接把倒计时清零（测领取用）。
     * POST /admin/building/finishClueExchange?uid=10000001
     */
    @RequestMapping({"/building/finishClueExchange"})
    public JSONObject finishClueExchange(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid) {
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
        JSONObject rooms = userData.getJSONObject("building").getJSONObject("rooms");
        JSONObject bucket = rooms == null ? null : rooms.getJSONObject("MEETING");
        if (bucket == null || bucket.isEmpty()) {
            result.put("code", 404);
            result.put("msg", "没有会客室数据");
            return result;
        }
        long now = com.hypergryph.arknights.ArknightsApplication.getTimestamp();
        int n = 0;
        for (String slotId : bucket.keySet()) {
            JSONObject node = bucket.getJSONObject(slotId);
            if (node == null) continue;
            node.put("processPoint", 0);
            node.put("completeWorkTime", now);
            node.put("lastUpdateTime", now);
            node.put("clueExchangeReady", true);
            JSONObject infoShare = node.getJSONObject("infoShare");
            if (infoShare == null) {
                infoShare = new JSONObject(true);
                infoShare.put("reward", 0);
                node.put("infoShare", infoShare);
            }
            infoShare.put("ts", now - 1);
            n++;
        }
        userDao.setUserData(account.getUid(), userData);
        result.put("code", 200);
        result.put("msg", "已把 " + n + " 个会客室的线索交流倒计时清零");
        return result;
    }

    /**
     * 拆房间（测试/救急用）：把槽位恢复成「未建造」。
     * POST /admin/building/removeRoom?uid=10000001&slotId=slot_13
     * 效果：删 roomSlots[slotId] 与 rooms[roomType][slotId]，并把里面驻扎的干员解绑。
     */
    @RequestMapping({"/building/removeRoom"})
    public JSONObject removeRoom(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid,
                                 @RequestParam String slotId) {
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
        JSONObject slots = building.getJSONObject("roomSlots");
        JSONObject slot = slots == null ? null : slots.getJSONObject(slotId);
        if (slot == null) {
            result.put("code", 404);
            result.put("msg", "槽位不存在或已拆: " + slotId);
            return result;
        }
        String roomId = slot.getString("roomId");

        // 解绑驻扎干员
        JSONArray ids = slot.getJSONArray("charInstIds");
        JSONObject bchars = building.getJSONObject("chars");
        if (ids != null && bchars != null) {
            for (int i = 0; i < ids.size(); i++) {
                Integer v = ids.getInteger(i);
                if (v == null || v <= 0) {
                    continue;
                }
                JSONObject c = bchars.getJSONObject(String.valueOf(v));
                if (c != null) {
                    c.put("roomSlotId", "");
                    c.put("index", -1);
                    c.put("changeScale", 0);
                }
            }
        }
        // 删房间节点
        JSONObject rooms = building.getJSONObject("rooms");
        JSONObject bucket = rooms == null ? null : rooms.getJSONObject(roomId);
        if (bucket != null) {
            bucket.remove(slotId);
            if (bucket.isEmpty()) {
                rooms.remove(roomId);
            }
        }
        slots.remove(slotId);

        userDao.setUserData(account.getUid(), userData);
        result.put("code", 200);
        result.put("msg", "已拆掉 " + slotId + "（" + roomId + "）");
        result.put("roomId", roomId);
        return result;
    }

    /**
     * 训练室：直接把「剩余训练点数」设成指定值（测结算用）。
     * POST /admin/building/trainPoint?uid=10000001&point=5
     */
    @RequestMapping({"/building/trainPoint"})
    public JSONObject setTrainPoint(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid,
                                    @RequestParam double point) {
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
        JSONObject rooms = userData.getJSONObject("building").getJSONObject("rooms");
        JSONObject bucket = rooms == null ? null : rooms.getJSONObject("TRAINING");
        if (bucket == null || bucket.isEmpty()) {
            result.put("code", 404);
            result.put("msg", "没有训练室数据");
            return result;
        }
        long now = com.hypergryph.arknights.ArknightsApplication.getTimestamp();
        int n = 0;
        for (String slotId : bucket.keySet()) {
            JSONObject node = bucket.getJSONObject(slotId);
            JSONObject trainee = node == null ? null : node.getJSONObject("trainee");
            if (trainee == null || trainee.getIntValue("state") != 1) {
                continue;
            }
            trainee.put("processPoint", point);
            node.put("lastUpdateTime", now);
            n++;
        }
        userDao.setUserData(account.getUid(), userData);
        result.put("code", 200);
        result.put("msg", "已把 " + n + " 个训练中的点数设为 " + point);
        return result;
    }

    /**
     * 送家具：POST /admin/send/furniture?uid=10000001&furniId=furni_inkArt_table_01&count=1
     * 写 building.furniture[id] = {count, inUse} 和 solution.furnitureTs[id]
     */
    @RequestMapping({"/send/furniture"})
    public JSONObject sendFurniture(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid,
                                    @RequestParam String furniId,
                                    @RequestParam(defaultValue = "1") int count) {
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
        if (building == null) {
            result.put("code", 404);
            result.put("msg", "没有基建数据");
            return result;
        }
        JSONObject furni = building.getJSONObject("furniture");
        if (furni == null) {
            furni = new JSONObject(true);
            building.put("furniture", furni);
        }
        JSONObject entry = furni.getJSONObject(furniId);
        if (entry == null) {
            entry = new JSONObject(true);
            entry.put("count", 0);
            entry.put("inUse", 0);
        }
        entry.put("count", entry.getIntValue("count") + count);
        furni.put(furniId, entry);

        JSONObject solution = building.getJSONObject("solution");
        if (solution == null) {
            solution = new JSONObject(true);
            building.put("solution", solution);
        }
        JSONObject ts = solution.getJSONObject("furnitureTs");
        if (ts == null) {
            ts = new JSONObject(true);
            solution.put("furnitureTs", ts);
        }
        ts.put(furniId, com.hypergryph.arknights.ArknightsApplication.getTimestamp());

        userDao.setUserData(account.getUid(), userData);
        result.put("code", 200);
        result.put("msg", "已送 " + furniId + " x" + count + "（现有 " + entry.getIntValue("count") + " 件）");
        return result;
    }

    @RequestMapping({"/building/forceRoom"})
    public JSONObject forceRoom(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid,
                                @RequestParam String slotId, @RequestParam String roomId,
                                @RequestParam(defaultValue = "1") int level) {
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
        JSONObject slots = building.getJSONObject("roomSlots");
        JSONObject slot = slots.getJSONObject(slotId);
        if (slot == null) {
            slot = new JSONObject(true);
            slots.put(slotId, slot);
        }
        slot.put("level", Math.max(1, level));
        slot.put("state", 2);
        slot.put("roomId", roomId);
        slot.put("charInstIds", new com.alibaba.fastjson.JSONArray());
        slot.put("completeConstructTime", -1);

        JSONObject rooms = building.getJSONObject("rooms");
        if (rooms == null) {
            rooms = new JSONObject(true);
            building.put("rooms", rooms);
        }
        JSONObject bucket = rooms.getJSONObject(roomId);
        if (bucket == null) {
            bucket = new JSONObject(true);
            rooms.put(roomId, bucket);
        }
        bucket.put(slotId, com.hypergryph.arknights.game.building.createRoomNode(roomId, Math.max(1, level)));

        userDao.setUserData(account.getUid(), userData);
        result.put("code", 200);
        result.put("msg", "槽位 " + slotId + " 已设为 " + roomId + " Lv" + level);
        result.put("room", bucket.getJSONObject(slotId));
        return result;
    }

    /**
     * 心情（基建 ap）操作：
     * - 不带参数：全部干员补满（ap=8640000）
     * - charInstId=N：只改这一个，值取 ap 参数（默认 0），用于测宿舍回复/消耗
     * POST /admin/building/resetAp?uid=10000001
     * POST /admin/building/resetAp?uid=10000001&charInstId=9&ap=8000000
     */
    @RequestMapping({"/building/resetAp"})
    public JSONObject resetBuildingAp(@RequestHeader("GMKey") String GMKey, @RequestParam Long uid,
                                      @RequestParam(required = false) Long charInstId,
                                      @RequestParam(defaultValue = "0") int ap) {
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
        if (charInstId != null) {
            JSONObject c = chars.getJSONObject(String.valueOf(charInstId));
            if (c == null) {
                result.put("code", 404);
                result.put("msg", "该干员不在基建 chars 里: " + charInstId);
                return result;
            }
            c.put("ap", ap);
            c.put("lastApAddTime", now);
            userDao.setUserData(account.getUid(), userData);
            result.put("code", 200);
            result.put("msg", "干员 " + charInstId + " 的心情设为 " + ap);
            return result;
        }
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
