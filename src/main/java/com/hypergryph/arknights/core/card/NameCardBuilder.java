package com.hypergryph.arknights.core.card;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NameCardBuilder {

    private NameCardBuilder() {
    }

    public static JSONObject buildFriendDetail(long uid, boolean withRecentVisited) {
        String json = userDao.queryUserJsonByUid(uid);
        if (json == null) {
            return null;
        }
        JSONObject target;
        try {
            target = JSONObject.parseObject(json);
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[namecard] uid={} 的 userData 解析失败: {}", uid, e.toString());
            return null;
        }
        if (target == null) {
            return null;
        }
        JSONObject status = target.getJSONObject("status");
        if (status == null) {
            return null;
        }
        JSONObject troop = target.getJSONObject("troop");
        JSONObject chars = troop == null ? null : troop.getJSONObject("chars");
        JSONObject social = target.getJSONObject("social");
        JSONObject building = target.getJSONObject("building");
        JSONObject nameCardStyle = target.getJSONObject("nameCardStyle");

        // 名片皮肤：官方响应里 state/tmpl 是空的，只取 selected
        JSONObject skin = new JSONObject(true);
        String selectedSkin = "nc_rhodes_default";
        if (nameCardStyle != null) {
            JSONObject cardSkin = nameCardStyle.getJSONObject("skin");
            if (cardSkin != null && cardSkin.getString("selected") != null) {
                selectedSkin = cardSkin.getString("selected");
            }
        }
        skin.put("selected", selectedSkin);
        skin.put("state", new JSONObject(true));
        skin.put("tmpl", new JSONObject(true));

        JSONObject medalBoard = social == null ? null : social.getJSONObject("medalBoard");
        if (medalBoard == null) {
            medalBoard = new JSONObject(true);
            medalBoard.put("type", "EMPTY");
            medalBoard.put("custom", null);
            medalBoard.put("template", null);
        }

        JSONObject avatar = null;
        try {
            avatar = JSON.parseObject(String.valueOf(status.get("avatar")));
        } catch (Exception ignored) {
        }
        if (avatar == null) {
            avatar = new JSONObject(true);
        }

        JSONObject player = new JSONObject(true);
        player.put("nickName", status.getString("nickName"));
        player.put("nickNumber", status.getString("nickNumber"));
        player.put("uid", String.valueOf(uid));
        player.put("registerTs", status.getLongValue("registerTs"));
        player.put("mainStageProgress", status.getString("mainStageProgress"));
        player.put("charCnt", chars == null ? 0 : chars.size());
        player.put("furnCnt", countFurniture(building));
        player.put("skinCnt", countSkins(target));
        player.put("secretary", status.getString("secretary"));
        player.put("secretarySkinId", status.getString("secretarySkinId"));
        player.put("secretarySkinSp", status.getBooleanValue("secretarySkinSp"));
        player.put("resume", status.getString("resume") == null ? "" : status.getString("resume"));
        player.put("teamV2", buildTeamV2(chars));
        player.put("friendNumLimit", status.getIntValue("friendNumLimit"));
        player.put("serverName", status.getString("serverName"));
        player.put("level", status.getIntValue("level"));
        player.put("avatarId", status.getString("avatarId"));
        player.put("avatar", avatar);
        player.put("assistCharList", buildAssistSharedList(social, chars));
        player.put("lastOnlineTime", status.getLongValue("lastOnlineTs"));
        // board = 名片上的势力展示，官方是 6 个**大写 groupId**（例如
        // ["RHODES","GLASGOW","URSUS","RHINE","PENGUIN","KJERAG"]）。
        // 它和 teamV2 不是一套（GLASGOW/RHINE 不在 teamV2 里），来自 character_table[].groupId。
        // 之前给空数组，客户端渲染这块时直接卡死，所以这里必须给够 6 个。
        player.put("board", buildBoard(chars));
        player.put("infoShare", 0);
        player.put("medalBoard", medalBoard);

        // 官方是真实生日（如 {month:7,day:17}）；本地没有这个字段，
        // 但**不能给 month=0**（非法日期会让客户端卡住），给一个合法的占位值。
        JSONObject birthday = new JSONObject(true);
        Integer[] md = birthdayOf(status);
        birthday.put("month", md[0]);
        birthday.put("day", md[1]);
        player.put("birthday", birthday);

        JSONObject equipStatus = new JSONObject(true);
        equipStatus.put("unlockCnt", 0);
        equipStatus.put("limitedEquipCnt", 0);
        equipStatus.put("level3EquipCnt", 0);
        equipStatus.put("equipCharCnt", 0);
        equipStatus.put("limitedCharCnt", 0);
        player.put("equipStatus", equipStatus);

        // 名片样式（PlayerNameCardStyle = {componentOrder, skin, misc}）。
        // NameCardV2View 渲染别人的卡时要从这里取 componentOrder / skin / misc，
        // 缺了它 skinId 会是空串，客户端就会去加载
        //   arts/ui/namecardskin/[uc]/skin_style
        // 这个不存在的 bundle，然后卡在加载态（实测卡死就是这个原因）。
        JSONObject styleOut = new JSONObject(true);
        styleOut.put("componentOrder", componentOrderOf(nameCardStyle));
        styleOut.put("skin", skin);
        styleOut.put("misc", miscOf(nameCardStyle));
        player.put("nameCardStyle", styleOut);

        player.put("activity", new JSONObject(true));
        player.put("magazineBoard", new JSONArray());
        player.put("friendPrivate", 0);
        // 官方两个变体的字段并不完全一样：
        //   friendList  → 有 recentVisited / clueSendToMe / clueReceiveFromMe，没有 birthday
        //   requestList → 有 birthday，没有上面三个
        // 这里一律给全（超集）：少任何一个，客户端都可能在加载名片时拿到 null 卡住不出界面。
        player.put("recentVisited", 0);
        player.put("clueSendToMe", 0);
        player.put("clueReceiveFromMe", new JSONArray());
        player.put("skin", skin);
        return player;
    }

    private static JSONArray buildBoard(JSONObject chars) {
        JSONArray board = new JSONArray();
        java.util.LinkedHashSet<String> picked = new java.util.LinkedHashSet<>();
        if (chars != null) {
            Map<String, Integer> groupCount = new HashMap<>();
            for (String key : chars.keySet()) {
                JSONObject ch = chars.getJSONObject(key);
                if (ch == null) {
                    continue;
                }
                String charId = ch.getString("charId");
                JSONObject meta = charId == null ? null : ArknightsApplication.characterJson.getJSONObject(charId);
                if (meta == null) {
                    continue;
                }
                String groupId = meta.getString("groupId");
                if (groupId == null || groupId.isEmpty()) {
                    continue;
                }
                groupCount.merge(groupId, 1, Integer::sum);
            }
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(groupCount.entrySet());
            entries.sort((a, b) -> {
                int cmp = b.getValue() - a.getValue();
                return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
            });
            for (Map.Entry<String, Integer> e : entries) {
                if (picked.size() >= 6) {
                    break;
                }
                picked.add(e.getKey().toUpperCase());
            }
        }
        for (String fallback : DEFAULT_BOARD) {
            if (picked.size() >= 6) {
                break;
            }
            picked.add(fallback);
        }
        board.addAll(picked);
        return board;
    }

    private static final String[] DEFAULT_BOARD = {
            "RHODES", "GLASGOW", "URSUS", "RHINE", "PENGUIN", "KJERAG"
    };

    private static Integer[] birthdayOf(JSONObject status) {
        JSONObject bd = status.getJSONObject("birthday");
        if (bd != null) {
            int month = bd.getIntValue("month");
            int day = bd.getIntValue("day");
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                return new Integer[] { month, day };
            }
        }
        return new Integer[] { 1, 1 };
    }

    private static JSONArray componentOrderOf(JSONObject nameCardStyle) {
        JSONArray order = nameCardStyle == null ? null : nameCardStyle.getJSONArray("componentOrder");
        if (order != null && !order.isEmpty()) {
            return order;
        }
        JSONArray fallback = new JSONArray();
        fallback.add("module_sign");
        fallback.add("module_assist");
        fallback.add("module_medal");
        return fallback;
    }

    private static JSONObject miscOf(JSONObject nameCardStyle) {
        JSONObject misc = nameCardStyle == null ? null : nameCardStyle.getJSONObject("misc");
        if (misc != null) {
            return misc;
        }
        JSONObject fallback = new JSONObject(true);
        fallback.put("showDetail", true);
        fallback.put("showBirthday", false);
        return fallback;
    }

    private static int countFurniture(JSONObject building) {        if (building == null) {
            return 0;
        }
        JSONObject furniture = building.getJSONObject("furniture");
        if (furniture == null) {
            return 0;
        }
        JSONObject furnitures = furniture.getJSONObject("furnitures");
        if (furnitures != null) {
            return furnitures.size();
        }
        return furniture.size();
    }

    private static int countSkins(JSONObject userData) {
        JSONObject skin = userData.getJSONObject("skin");
        if (skin == null) {
            return 0;
        }
        JSONObject characterSkins = skin.getJSONObject("characterSkins");
        return characterSkins == null ? 0 : characterSkins.size();
    }

    private static JSONObject buildTeamV2(JSONObject chars) {
        JSONObject teamV2 = new JSONObject(true);
        if (chars == null) {
            return teamV2;
        }
        for (String key : chars.keySet()) {
            JSONObject ch = chars.getJSONObject(key);
            if (ch == null) {
                continue;
            }
            String charId = ch.getString("charId");
            JSONObject meta = charId == null ? null : ArknightsApplication.characterJson.getJSONObject(charId);
            if (meta == null) {
                continue;
            }
            String teamId = meta.getString("teamId");
            if (teamId == null || teamId.isEmpty()) {
                continue;
            }
            teamV2.put(teamId, teamV2.getIntValue(teamId) + 1);
        }
        return teamV2;
    }

    private static JSONArray buildAssistSharedList(JSONObject social, JSONObject chars) {
        JSONArray out = new JSONArray();
        JSONArray stored = social == null ? null : social.getJSONArray("assistCharList");
        if (stored == null || stored.isEmpty()) {
            out.add(null);                  // 官方空槽位是 [null]
            return out;
        }
        for (int i = 0; i < stored.size(); i++) {
            Object raw = stored.get(i);
            JSONObject entry = raw instanceof JSONObject ? (JSONObject) raw : null;
            if (entry == null) {
                out.add(null);
                continue;
            }
            int instId = entry.getIntValue("charInstId");
            JSONObject owned = chars == null ? null : chars.getJSONObject(String.valueOf(instId));
            if (owned == null) {
                out.add(null);              // 干员已不存在，按空槽处理
                continue;
            }
            JSONObject shared = new JSONObject(true);
            shared.put("charId", owned.getString("charId"));
            // 模板干员（阿米娅）的皮肤/技能/技能序号都在 tmpl[currentTmpl] 里
            String activeSkin = com.hypergryph.arknights.core.work.processArray.getSkin(owned);
            shared.put("skinId", activeSkin == null
                    ? owned.getString("charId") + "#1" : activeSkin);
            JSONObject activeNode = com.hypergryph.arknights.core.work.processArray.activeCharData(owned);
            JSONArray activeSkills = activeNode == null ? null : activeNode.getJSONArray("skills");
            shared.put("skills", activeSkills == null ? new JSONArray() : activeSkills);
            shared.put("mainSkillLvl", owned.getIntValue("mainSkillLvl"));
            shared.put("skillIndex", entry.containsKey("skillIndex")
                    ? entry.getIntValue("skillIndex") : com.hypergryph.arknights.core.work.processArray.getDefaultSkillIndex(owned));
            shared.put("evolvePhase", owned.getIntValue("evolvePhase"));
            shared.put("favorPoint", owned.getIntValue("favorPoint"));
            shared.put("potentialRank", owned.getIntValue("potentialRank"));
            shared.put("level", owned.getIntValue("level"));
            shared.put("crisisRecord", new JSONObject(true));
            shared.put("crisisV2Record", new JSONObject(true));
            String currentEquip = entry.getString("currentEquip");
            shared.put("currentEquip", (currentEquip == null || currentEquip.isEmpty()) ? null : currentEquip);
            shared.put("equip", owned.getJSONObject("equip") == null
                    ? new JSONObject(true) : owned.getJSONObject("equip"));
            out.add(shared);
        }
        return out;
    }
}
