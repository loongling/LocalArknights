package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.card.NameCardBuilder;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import com.hypergryph.arknights.core.pojo.SearchUidList;
import com.hypergryph.arknights.core.pojo.UserInfo;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/social")
public class social {

    @PostMapping("/sendFriendRequest")
    public JSONObject sendFriendRequest(@RequestHeader(value = "secret", required = false) String secret,
                                        @RequestBody JSONObject jsonBody,
                                        HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /social/sendFriendRequest");
        ArknightsApplication.LOGGER.info("sendFriendRequest:" + jsonBody);

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }
        long myUid = account.getUid();

        String friendId = jsonBody.getString("friendId");
        Object battleOriginRaw = jsonBody.get("battleOrigin");

        if (friendId == null || friendId.trim().isEmpty()) {
            response.setStatus(400);
            return createErrorResponse(222, "validation", "\"friendId\" is required");
        }
        if (battleOriginRaw instanceof String && ((String) battleOriginRaw).trim().isEmpty()) {
            response.setStatus(400);
            return createErrorResponse(222, "validation", "\"battleOrigin\" is not allowed to be empty");
        }

        long targetUid;
        try {
            targetUid = Long.parseLong(friendId.trim());
        } catch (NumberFormatException e) {
            return sendFriendResult(3);
        }
        if (targetUid <= 0 || targetUid == myUid) {
            return sendFriendResult(3);
        }
        if (userDao.queryAccountByUid(targetUid).isEmpty()) {
            ArknightsApplication.LOGGER.info("[social] 好友申请目标 {} 不存在", targetUid);
            return sendFriendResult(3);
        }

        JSONObject myFriend = loadFriend(myUid);
        int existing = relationOf(myFriend, targetUid);
        if (existing == REL_FRIEND) {
            return sendFriendResult(1);
        }
        if (existing == REL_SENT) {
            return sendFriendResult(2);
        }

        appendRequest(myFriend, targetUid, 0);
        userDao.setFriendData(myUid, myFriend);

        JSONObject targetFriend = loadFriend(targetUid);
        appendRequest(targetFriend, myUid, 1);
        userDao.setFriendData(targetUid, targetFriend);

        ArknightsApplication.LOGGER.info("[social] {} -> {} 好友申请已写入双方 request", myUid, targetUid);

        JSONObject out = new JSONObject(true);
        out.put("result", 0);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", new JSONObject(true));
        playerDataDelta.put("deleted", new JSONObject(true));
        out.put("playerDataDelta", playerDataDelta);
        return out;
    }

    private static JSONObject sendFriendResult(int result) {
        JSONObject out = new JSONObject(true);
        out.put("result", result);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", new JSONObject(true));
        playerDataDelta.put("deleted", new JSONObject(true));
        out.put("playerDataDelta", playerDataDelta);
        return out;
    }

    private static void appendRequest(JSONObject friend, long uid, int direction) {
        JSONArray requests = friend.getJSONArray("request");
        if (requests == null) {
            requests = new JSONArray();
            friend.put("request", requests);
        }
        JSONObject item = new JSONObject(true);
        item.put("uid", String.valueOf(uid));
        item.put("direction", direction);
        item.put("ts", ArknightsApplication.getTimestamp());
        requests.add(item);
    }

    private static int requestDirectionOf(JSONObject friend, long uid) {
        JSONArray requests = friend.getJSONArray("request");
        if (requests == null) {
            return -1;
        }
        for (int i = 0; i < requests.size(); i++) {
            Object entry = requests.get(i);
            if (friendUidOf(entry) != uid) {
                continue;
            }
            if (entry instanceof JSONObject) {
                return ((JSONObject) entry).getIntValue("direction");
            }
            return 0;           // 老数据没有 direction，按"我发的"处理
        }
        return -1;
    }

    @PostMapping("/searchPlayer")
    public JSONObject searchPlayer(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody JSONObject jsonBody,
                                   HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /social/searchPlayer");
        ArknightsApplication.LOGGER.info("searchPlayer:" + jsonBody);

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }

        JSONArray idList = jsonBody.getJSONArray("idList");
        JSONArray players = new JSONArray();
        JSONArray friendStatusList = new JSONArray();
        JSONArray resultIdList = new JSONArray();

        JSONObject friend = loadFriend(account.getUid());

        if (idList != null) {
            for (int i = 0; i < idList.size(); i++) {
                long uid;
                try {
                    uid = Long.parseLong(String.valueOf(idList.get(i)).trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (uid <= 0) {
                    continue;
                }
                JSONObject player = buildPlayerProfile(uid);
                if (player == null) {
                    continue;           // 查不到就不进结果，官方也是只回存在的
                }
                players.add(player);
                resultIdList.add(String.valueOf(uid));
                friendStatusList.add(wireFriendStatus(friend, uid));
            }
        }

        ArknightsApplication.LOGGER.info("[social] searchPlayer 请求 {} 个，命中 {} 个",
                idList == null ? 0 : idList.size(), players.size());

        JSONObject out = new JSONObject(true);
        out.put("players", players);
        out.put("friendStatusList", friendStatusList);
        out.put("resultIdList", resultIdList);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", new JSONObject(true));
        playerDataDelta.put("deleted", new JSONObject(true));
        out.put("playerDataDelta", playerDataDelta);
        return out;
    }

    private static final int REL_NONE = 0;
    private static final int REL_FRIEND = 1;
    private static final int REL_SENT = 2;
    private static final int REL_INCOMING = 3;

    private static int relationOf(JSONObject friend, long uid) {
        if (isFriend(friend, uid)) {
            return REL_FRIEND;
        }
        int direction = requestDirectionOf(friend, uid);
        if (direction == 0) {
            return REL_SENT;
        }
        if (direction == 1) {
            return REL_INCOMING;
        }
        return REL_NONE;
    }

    private static int wireFriendStatus(JSONObject friend, long uid) {
        return relationOf(friend, uid) == REL_FRIEND ? 2 : 0;
    }

    private JSONObject buildPlayerProfile(long uid) {
        List<UserInfo> infos = userDao.queryUserInfo(uid);
        if (infos.isEmpty()) {
            return null;
        }
        UserInfo info = infos.get(0);
        JSONObject status;
        try {
            status = JSONObject.parseObject(info.getStatus());
        } catch (Exception e) {
            return null;
        }
        if (status == null) {
            return null;
        }

        JSONArray assist = new JSONArray();
        try {
            if (info.getSocialAssistCharList() != null) {
                JSONArray parsed = JSON.parseArray(info.getSocialAssistCharList());
                if (parsed != null && !parsed.isEmpty()) {
                    assist = parsed;
                }
            }
        } catch (Exception ignored) {
        }
        if (assist.isEmpty()) {
            assist.add(null);
        }

        JSONObject skin = new JSONObject(true);
        skin.put("selected", "nc_rhodes_default");
        skin.put("state", new JSONObject(true));
        skin.put("tmpl", new JSONObject(true));

        JSONObject medalBoard = new JSONObject(true);
        medalBoard.put("type", "EMPTY");
        medalBoard.put("custom", null);
        medalBoard.put("template", null);

        JSONObject birthday = new JSONObject(true);
        birthday.put("month", 0);
        birthday.put("day", 0);

        JSONObject equipStatus = new JSONObject(true);
        equipStatus.put("unlockCnt", 0);
        equipStatus.put("limitedEquipCnt", 0);
        equipStatus.put("level3EquipCnt", 0);
        equipStatus.put("equipCharCnt", 0);
        equipStatus.put("limitedCharCnt", 0);

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
        player.put("secretary", status.getString("secretary"));
        player.put("secretarySkinId", status.getString("secretarySkinId"));
        player.put("secretarySkinSp", status.getBooleanValue("secretarySkinSp"));
        player.put("friendNumLimit", status.getIntValue("friendNumLimit"));
        player.put("serverName", status.getString("serverName"));
        player.put("level", status.getIntValue("level"));
        player.put("avatarId", status.getString("avatarId"));
        player.put("avatar", avatar);
        player.put("assistCharList", assist);
        player.put("lastOnlineTime", status.getLongValue("lastOnlineTs"));
        player.put("medalBoard", medalBoard);
        player.put("birthday", birthday);
        player.put("equipStatus", equipStatus);
        player.put("activity", new JSONObject(true));
        player.put("magazineBoard", new JSONArray());
        player.put("friendPrivate", 0);
        player.put("skin", skin);
        return player;
    }

    @PostMapping("/getSortListInfo")
    public JSONObject getSortListInfo(@RequestHeader(value = "secret", required = false) String secret,
                                      @RequestBody JSONObject jsonBody,
                                      HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /social/getSortListInfo");
        ArknightsApplication.LOGGER.info("getSortListInfo:" + jsonBody);

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }
        long myUid = account.getUid();

        int type = jsonBody.getIntValue("type");
        JSONObject param = jsonBody.getJSONObject("param");

        JSONArray result = new JSONArray();
        JSONArray starFriendList = new JSONArray();

        switch (type) {
            case 0: {                       // SEARCH_FRIEND：只认 param.nickName
                String nickName = param == null ? null : param.getString("nickName");
                if (nickName == null || nickName.trim().isEmpty()) {
                    response.setStatus(400);
                    return createErrorResponse(222, "validation", "param.nickName is required");
                }
                List<SearchUidList> hits = userDao.searchPlayer("%" + nickName.trim() + "%", "%");
                ArknightsApplication.LOGGER.info("[social] 搜索 {} 命中 {} 个账号", nickName, hits.size());
                for (SearchUidList hit : hits) {
                    if (hit.getUid() == myUid) {
                        continue;           // 官方搜索结果不含自己
                    }
                    JSONObject vm = buildFriendViewModel(hit.getUid(), false);
                    if (vm != null) {
                        result.add(vm);
                    }
                }
                break;
            }

            case 1: {
                JSONObject friend = loadFriend(myUid);
                JSONArray list = friend.getJSONArray("list");
                if (list != null) {
                    for (int i = 0; i < list.size(); i++) {
                        long uid = friendUidOf(list.get(i));
                        if (uid <= 0) {
                            continue;
                        }
                        JSONObject vm = buildFriendViewModel(uid, true);
                        if (vm != null) {
                            result.add(vm);
                        }
                    }
                }
                JSONArray stars = friend.getJSONArray("star");
                if (stars != null) {
                    for (int i = 0; i < stars.size(); i++) {
                        long uid = friendUidOf(stars.get(i));
                        if (uid > 0) {
                            starFriendList.add(String.valueOf(uid));
                        }
                    }
                }
                break;
            }

            case 2: {
                JSONObject friend = loadFriend(myUid);
                JSONArray requests = friend.getJSONArray("request");
                if (requests != null) {
                    for (int i = 0; i < requests.size(); i++) {
                        Object entry = requests.get(i);
                        long uid = friendUidOf(entry);
                        if (uid <= 0) {
                            continue;
                        }
                        int direction = entry instanceof JSONObject
                                ? ((JSONObject) entry).getIntValue("direction") : 0;
                        if (direction != 1) {
                            continue;           // 我发出去的申请不在这个列表里
                        }
                        // 官方实测：申请列表只回 uid，详情另外走 getFriendRequestList
                        JSONObject item = new JSONObject(true);
                        item.put("uid", String.valueOf(uid));
                        result.add(item);
                    }
                }
                break;
            }

            default:
                ArknightsApplication.LOGGER.warn("[social] 未知 getSortListInfo type={}，返回空列表", type);
                break;
        }

        JSONObject out = new JSONObject(true);
        out.put("result", result);
        if (type == 1) {
            out.put("starFriendList", starFriendList);
        }
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", new JSONObject(true));
        playerDataDelta.put("deleted", new JSONObject(true));
        out.put("playerDataDelta", playerDataDelta);
        return out;
    }

    private JSONObject buildFriendViewModel(long uid, boolean recentVisited) {
        List<UserInfo> infos = userDao.queryUserInfo(uid);
        if (infos.isEmpty()) {
            return null;
        }
        JSONObject status;
        try {
            status = JSONObject.parseObject(infos.get(0).getStatus());
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[social] uid={} 的 status 解析失败: {}", uid, e.toString());
            return null;
        }
        if (status == null) {
            status = new JSONObject(true);
        }
        JSONObject vm = new JSONObject(true);
        vm.put("uid", String.valueOf(uid));
        vm.put("level", status.getIntValue("level"));
        vm.put("infoShare", 0);
        vm.put("infoShareVisited", 0);
        vm.put("recentVisited", recentVisited);
        vm.put("lastOnlineTime", status.getLongValue("lastOnlineTs"));
        vm.put("activity", new JSONObject(true));
        return vm;
    }

    private JSONObject loadFriend(long uid) {
        try {
            List<UserInfo> infos = userDao.queryUserInfo(uid);
            if (!infos.isEmpty() && infos.get(0).getFriend() != null) {
                JSONObject friend = JSONObject.parseObject(infos.get(0).getFriend());
                if (friend != null) {
                    return friend;
                }
            }
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("[social] uid={} 的 friend 列解析失败: {}", uid, e.toString());
        }
        JSONObject empty = new JSONObject(true);
        empty.put("list", new JSONArray());
        empty.put("request", new JSONArray());
        return empty;
    }

    private static long friendUidOf(Object entry) {
        if (entry == null) {
            return -1;
        }
        if (entry instanceof Number) {
            return ((Number) entry).longValue();
        }
        if (entry instanceof JSONObject) {
            return ((JSONObject) entry).getLongValue("uid");
        }
        try {
            return Long.parseLong(String.valueOf(entry).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @PostMapping("/setAssistCharList")
    public JSONObject setAssistCharList(@RequestHeader(value = "secret", required = false) String secret,
                                        @RequestBody JSONObject jsonBody,
                                        HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /social/setAssistCharList");
        ArknightsApplication.LOGGER.info("setAssistCharList:" + jsonBody);

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }
        JSONObject userData = holder.getJSONObject("user");

        JSONArray incoming = jsonBody.getJSONArray("assistCharList");
        if (incoming == null) {
            response.setStatus(400);
            return createErrorResponse(222, "validation", "assistCharList is required");
        }

        JSONObject troop = userData.getJSONObject("troop");
        JSONObject chars = troop == null ? null : troop.getJSONObject("chars");

        JSONArray stored = new JSONArray();
        for (int i = 0; i < incoming.size(); i++) {
            Object raw = incoming.get(i);
            if (raw == null) {
                stored.add(null);
                continue;
            }
            JSONObject entry;
            if (raw instanceof JSONObject) {
                entry = (JSONObject) raw;
            } else if (raw instanceof java.util.Map) {
                entry = (JSONObject) JSON.toJSON(raw);
            } else {
                response.setStatus(400);
                return createErrorResponse(222, "validation",
                        "assistCharList[" + i + "] must be object or null");
            }
            int charInstId = entry.getIntValue("charInstId");
            if (charInstId <= 0) {
                response.setStatus(400);
                return createErrorResponse(222, "validation",
                        "assistCharList[" + i + "].charInstId is invalid");
            }
            if (chars == null || chars.getJSONObject(String.valueOf(charInstId)) == null) {
                response.setStatus(400);
                ArknightsApplication.LOGGER.warn("[social] 未拥有干员 instId={}，拒绝设置助战", charInstId);
                return createErrorResponse(5847, "invalid parameter",
                        "charRotation assistCharList char not owned: " + charInstId);
            }
            JSONObject owned = chars.getJSONObject(String.valueOf(charInstId));
            JSONObject clean = new JSONObject(true);
            clean.put("charInstId", charInstId);
            // 模板干员的 defaultSkillIndex 在 patch 里（基节点是 -1）
            clean.put("skillIndex", com.hypergryph.arknights.core.work.processArray.getDefaultSkillIndex(owned));
            String currentEquip = owned.getString("currentEquip");
            clean.put("currentEquip", (currentEquip == null || currentEquip.isEmpty()) ? null : currentEquip);
            stored.add(clean);
        }

        JSONObject social = userData.getJSONObject("social");
        if (social == null) {
            social = new JSONObject(true);
            userData.put("social", social);
        }
        social.put("assistCharList", stored);
        userDao.setUserData(account.getUid(), userData);

        ArknightsApplication.LOGGER.info("[social] assistCharList -> {}", stored);

        JSONObject socialDelta = new JSONObject(true);
        socialDelta.put("assistCharList", stored);
        JSONObject modified = new JSONObject(true);
        modified.put("social", socialDelta);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject result = new JSONObject(true);
        result.put("playerDataDelta", playerDataDelta);
        return result;
    }

    @PostMapping("/processFriendRequest")
    public JSONObject processFriendRequest(@RequestHeader(value = "secret", required = false) String secret,
                                           @RequestBody JSONObject jsonBody,
                                           HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /social/processFriendRequest");
        ArknightsApplication.LOGGER.info("processFriendRequest:" + jsonBody);

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }
        long myUid = account.getUid();
        JSONObject userData = holder.getJSONObject("user");

        int action = jsonBody.getIntValue("action");
        long targetUid = parseUid(jsonBody.getString("friendId"));
        if (targetUid <= 0 || targetUid == myUid) {
            return processFriendResult(1, loadFriend(myUid), userData, myUid, false);
        }

        JSONObject myFriend = loadFriend(myUid);
        if (requestDirectionOf(myFriend, targetUid) < 0) {
            ArknightsApplication.LOGGER.info("[social] {} 没有来自 {} 的待处理申请", myUid, targetUid);
            return processFriendResult(1, myFriend, userData, myUid, false);
        }

        JSONObject targetFriend = loadFriend(targetUid);
        removeRequest(myFriend, targetUid);
        removeRequest(targetFriend, myUid);

        if (action == 1) {
            addFriend(myFriend, targetUid);
            addFriend(targetFriend, myUid);
            ArknightsApplication.LOGGER.info("[social] {} 同意 {} 的好友申请", myUid, targetUid);
        } else {
            ArknightsApplication.LOGGER.info("[social] {} 拒绝 {} 的好友申请", myUid, targetUid);
        }

        userDao.setFriendData(myUid, myFriend);
        userDao.setFriendData(targetUid, targetFriend);
        return processFriendResult(0, myFriend, userData, myUid, true);
    }

    private JSONObject processFriendResult(int result, JSONObject myFriend, JSONObject userData,
                                           long uid, boolean savePushFlags) {
        int friendNum = countFriends(myFriend);
        int hasFriendRequest = hasIncomingRequest(myFriend) ? 1 : 0;

        JSONObject pushFlags = userData.getJSONObject("pushFlags");
        if (pushFlags == null) {
            pushFlags = new JSONObject(true);
            userData.put("pushFlags", pushFlags);
        }
        pushFlags.put("hasFriendRequest", hasFriendRequest);
        if (savePushFlags) {
            userDao.setUserData(uid, userData);
        }

        JSONObject flagsDelta = new JSONObject(true);
        flagsDelta.put("hasFriendRequest", hasFriendRequest);
        JSONObject modified = new JSONObject(true);
        modified.put("pushFlags", flagsDelta);
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject out = new JSONObject(true);
        out.put("result", result);
        out.put("friendNum", friendNum);
        out.put("playerDataDelta", playerDataDelta);
        return out;
    }

    @PostMapping("/deleteFriend")
    public JSONObject deleteFriend(@RequestHeader(value = "secret", required = false) String secret,
                                   @RequestBody JSONObject jsonBody,
                                   HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /social/deleteFriend");
        ArknightsApplication.LOGGER.info("deleteFriend:" + jsonBody);

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }
        long myUid = account.getUid();
        long targetUid = parseUid(jsonBody.getString("friendId"));

        JSONObject myFriend = loadFriend(myUid);
        if (targetUid <= 0 || !removeFriend(myFriend, targetUid)) {
            return simpleResult(1);
        }
        userDao.setFriendData(myUid, myFriend);

        if (targetUid != myUid) {
            JSONObject targetFriend = loadFriend(targetUid);
            removeFriend(targetFriend, myUid);
            userDao.setFriendData(targetUid, targetFriend);
        }

        ArknightsApplication.LOGGER.info("[social] {} 删除了好友 {}", myUid, targetUid);
        return simpleResult(0);
    }

    @PostMapping("/getFriendAndRequestSendList")
    public JSONObject getFriendAndRequestSendList(@RequestHeader(value = "secret", required = false) String secret,
                                                  @RequestBody(required = false) JSONObject jsonBody,
                                                  HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /social/getFriendAndRequestSendList");

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }
        JSONObject friend = loadFriend(account.getUid());

        JSONArray friendsList = new JSONArray();
        JSONArray list = friend.getJSONArray("list");
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                long uid = friendUidOf(list.get(i));
                if (uid <= 0) {
                    continue;
                }
                JSONObject simple = new JSONObject(true);
                simple.put("uid", String.valueOf(uid));
                // 官方实测：没有备注时是 null，不是 ""
                String alias = aliasOf(friend, uid);
                simple.put("friendAlias", alias.isEmpty() ? null : alias);
                friendsList.add(simple);
            }
        }

        JSONArray requestSendIdList = new JSONArray();
        JSONArray requests = friend.getJSONArray("request");
        if (requests != null) {
            for (int i = 0; i < requests.size(); i++) {
                Object entry = requests.get(i);
                int direction = entry instanceof JSONObject
                        ? ((JSONObject) entry).getIntValue("direction") : 0;
                long uid = friendUidOf(entry);
                if (uid > 0 && direction == 0) {
                    requestSendIdList.add(String.valueOf(uid));
                }
            }
        }

        JSONObject out = new JSONObject(true);
        out.put("friendsList", friendsList);
        out.put("requestSendIdList", requestSendIdList);
        out.put("playerDataDelta", emptyDelta());
        return out;
    }

    @PostMapping("/getFriendList")
    public JSONObject getFriendList(@RequestHeader(value = "secret", required = false) String secret,
                                    @RequestBody JSONObject jsonBody,
                                    HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /social/getFriendList");

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }
        JSONArray idList = jsonBody.getJSONArray("idList");
        if (idList == null) {
            response.setStatus(400);
            return createErrorResponse(222, "validation", "\"idList\" is required");
        }
        JSONObject friend = loadFriend(account.getUid());

        JSONArray friends = new JSONArray();
        JSONArray aliases = new JSONArray();
        JSONArray resultIdList = new JSONArray();
        for (int i = 0; i < idList.size(); i++) {
            long uid = parseUid(String.valueOf(idList.get(i)));
            if (uid <= 0) {
                continue;
            }
            JSONObject data = buildFriendDetail(uid, true);
            if (data == null) {
                continue;
            }
            friends.add(data);
            aliases.add(aliasOf(friend, uid));
            resultIdList.add(String.valueOf(uid));
        }

        JSONObject out = new JSONObject(true);
        out.put("friends", friends);
        out.put("friendAlias", aliases);
        out.put("resultIdList", resultIdList);
        out.put("playerDataDelta", emptyDelta());
        return out;
    }

    @PostMapping("/getFriendRequestList")
    public JSONObject getFriendRequestList(@RequestHeader(value = "secret", required = false) String secret,
                                           @RequestBody JSONObject jsonBody,
                                           HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /social/getFriendRequestList");

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }
        JSONArray idList = jsonBody.getJSONArray("idList");
        if (idList == null) {
            response.setStatus(400);
            return createErrorResponse(222, "validation", "\"idList\" is required");
        }

        JSONArray requestList = new JSONArray();
        JSONArray resultIdList = new JSONArray();
        for (int i = 0; i < idList.size(); i++) {
            long uid = parseUid(String.valueOf(idList.get(i)));
            if (uid <= 0) {
                continue;
            }
            JSONObject data = buildFriendDetail(uid, false);
            if (data == null) {
                continue;
            }
            requestList.add(data);
            resultIdList.add(String.valueOf(uid));
        }

        JSONObject out = new JSONObject(true);
        out.put("requestList", requestList);
        out.put("resultIdList", resultIdList);
        out.put("playerDataDelta", emptyDelta());
        return out;
    }

    @PostMapping("/setStarFriendList")
    public JSONObject setStarFriendList(@RequestHeader(value = "secret", required = false) String secret,
                                        @RequestBody JSONObject jsonBody,
                                        HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /social/setStarFriendList");
        ArknightsApplication.LOGGER.info("setStarFriendList:" + jsonBody);

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }
        long myUid = account.getUid();
        JSONObject userData = holder.getJSONObject("user");

        JSONArray idList = jsonBody.getJSONArray("idList");
        JSONArray newIdList = new JSONArray();
        if (idList != null) {
            for (int i = 0; i < idList.size(); i++) {
                long uid = parseUid(String.valueOf(idList.get(i)));
                if (uid > 0) {
                    newIdList.add(String.valueOf(uid));
                }
            }
        }

        JSONObject friend = loadFriend(myUid);
        friend.put("star", newIdList);
        userDao.setFriendData(myUid, friend);

        JSONObject modified = new JSONObject(true);
        if (newIdList.isEmpty()) {
            JSONObject social = userData.getJSONObject("social");
            if (social == null) {
                social = new JSONObject(true);
                userData.put("social", social);
            }
            social.put("starFriendFlag", 1);
            userDao.setUserData(myUid, userData);

            JSONObject socialDelta = new JSONObject(true);
            socialDelta.put("starFriendFlag", 1);
            modified.put("social", socialDelta);
        }
        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        JSONObject out = new JSONObject(true);
        out.put("result", 0);
        out.put("newIdList", newIdList);
        out.put("playerDataDelta", playerDataDelta);
        return out;
    }

    @PostMapping("/setFriendAlias")
    public JSONObject setFriendAlias(@RequestHeader(value = "secret", required = false) String secret,
                                     @RequestBody JSONObject jsonBody,
                                     HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /social/setFriendAlias");
        ArknightsApplication.LOGGER.info("setFriendAlias:" + jsonBody);

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return (JSONObject) holder.remove("__error");
        }
        long myUid = account.getUid();
        long targetUid = parseUid(jsonBody.getString("friendId"));
        String alias = jsonBody.getString("alias");

        JSONObject friend = loadFriend(myUid);
        if (targetUid <= 0 || !isFriend(friend, targetUid)) {
            return simpleResult(6);
        }
        JSONObject aliases = friend.getJSONObject("alias");
        if (aliases == null) {
            aliases = new JSONObject(true);
            friend.put("alias", aliases);
        }
        if (alias == null || alias.isEmpty()) {
            aliases.remove(String.valueOf(targetUid));
        } else {
            aliases.put(String.valueOf(targetUid), alias);
        }
        userDao.setFriendData(myUid, friend);
        return simpleResult(0);
    }


    private static JSONObject simpleResult(int result) {
        JSONObject out = new JSONObject(true);
        out.put("result", result);
        out.put("playerDataDelta", emptyDelta());
        return out;
    }

    private static long parseUid(String value) {
        if (value == null) {
            return -1;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isFriend(JSONObject friend, long uid) {
        JSONArray list = friend.getJSONArray("list");
        if (list == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            if (friendUidOf(list.get(i)) == uid) {
                return true;
            }
        }
        return false;
    }

    private static void addFriend(JSONObject friend, long uid) {
        JSONArray list = friend.getJSONArray("list");
        if (list == null) {
            list = new JSONArray();
            friend.put("list", list);
        }
        if (!isFriend(friend, uid)) {
            list.add(String.valueOf(uid));
        }
    }

    private static boolean removeFriend(JSONObject friend, long uid) {
        JSONArray list = friend.getJSONArray("list");
        if (list == null) {
            return false;
        }
        boolean removed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (friendUidOf(list.get(i)) == uid) {
                list.remove(i);
                removed = true;
            }
        }
        return removed;
    }

    private static void removeRequest(JSONObject friend, long uid) {
        JSONArray requests = friend.getJSONArray("request");
        if (requests == null) {
            return;
        }
        for (int i = requests.size() - 1; i >= 0; i--) {
            if (friendUidOf(requests.get(i)) == uid) {
                requests.remove(i);
            }
        }
    }

    private static int countFriends(JSONObject friend) {
        JSONArray list = friend.getJSONArray("list");
        return list == null ? 0 : list.size();
    }

    private static boolean hasIncomingRequest(JSONObject friend) {
        JSONArray requests = friend.getJSONArray("request");
        if (requests == null) {
            return false;
        }
        for (int i = 0; i < requests.size(); i++) {
            Object entry = requests.get(i);
            int direction = entry instanceof JSONObject
                    ? ((JSONObject) entry).getIntValue("direction") : 0;
            if (direction == 1) {
                return true;
            }
        }
        return false;
    }

    private static String aliasOf(JSONObject friend, long uid) {
        JSONObject aliases = friend.getJSONObject("alias");
        if (aliases == null) {
            return "";
        }
        String alias = aliases.getString(String.valueOf(uid));
        return alias == null ? "" : alias;
    }

    private JSONObject buildFriendDetail(long uid, boolean withRecentVisited) {
        return NameCardBuilder.buildFriendDetail(uid, withRecentVisited);
    }
    private Account auth(String secret, HttpServletResponse response, JSONObject holder) {
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            holder.put("__error", createErrorResponse(400, "服务器维护中"));
            return null;
        }
        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            holder.put("__error", createErrorResponse(2, "账户验证失败"));
            return null;
        }
        Account account = accounts.get(0);
        if (account.getBan() == 1L) {
            response.setStatus(403);
            holder.put("__error", createErrorResponse(403, "账号已被封禁"));
            return null;
        }
        holder.put("user", JSONObject.parseObject(account.getUser()));
        return account;
    }

    private JSONObject createErrorResponse(int code, String msg) {
        JSONObject result = new JSONObject(true);
        result.put("result", code);
        result.put("msg", msg);
        result.put("message", "server error");
        return result;
    }

    private JSONObject createErrorResponse(int code, String msg, String info) {
        JSONObject result = new JSONObject(true);
        result.put("code", code);
        result.put("msg", msg);
        result.put("info", info);
        result.put("message", "server error");
        return result;
    }

    @PostMapping("/receiveSocialPoint")
    public JSONObject receiveSocialPoint(@RequestHeader(value = "secret", required = false) String secret,
                                         HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /social/receiveSocialPoint");

        JSONObject holder = new JSONObject();
        Account account = auth(secret, response, holder);
        if (account == null) {
            return holder;
        }

        JSONObject userData = JSONObject.parseObject(account.getUser());
        JSONObject socialNode = userData.getJSONObject("social");
        if (socialNode == null) {
            socialNode = new JSONObject(true);
            userData.put("social", socialNode);
        }
        JSONObject yesterday = socialNode.getJSONObject("yesterdayReward");
        if (yesterday == null) {
            yesterday = new JSONObject(true);
            yesterday.put("canReceive", 0);
            yesterday.put("assistAmount", 0);
            yesterday.put("comfortAmount", 0);
            yesterday.put("first", 0);
            socialNode.put("yesterdayReward", yesterday);
        }

        JSONArray rewardList = new JSONArray();
        JSONObject out = new JSONObject(true);
        if (yesterday.getIntValue("canReceive") != 1) {
            out.put("reward", rewardList);
            out.put("playerDataDelta", emptyDelta());
            return out;
        }

        int gain = yesterday.getIntValue("first") == 1
                ? 16
                : Math.min(16, yesterday.getIntValue("assistAmount") + yesterday.getIntValue("comfortAmount"));
        yesterday.put("canReceive", 0);
        yesterday.put("first", 0);
        yesterday.put("assistAmount", 0);
        yesterday.put("comfortAmount", 0);

        JSONObject status = userData.getJSONObject("status");
        int socialPoint = status.getIntValue("socialPoint") + gain;
        status.put("socialPoint", socialPoint);

        JSONObject item = new JSONObject(true);
        item.put("id", "SOCIAL_PT");
        item.put("type", "SOCIAL_PT");
        item.put("count", gain);
        rewardList.add(item);

        userDao.setUserData(account.getUid(), userData);

        JSONObject statusDelta = new JSONObject(true);
        statusDelta.put("socialPoint", socialPoint);
        JSONObject yesterdayDelta = new JSONObject(true);
        yesterdayDelta.put("canReceive", 0);
        yesterdayDelta.put("first", 0);

        JSONObject modified = new JSONObject(true);
        modified.put("status", statusDelta);
        JSONObject socialDelta = new JSONObject(true);
        socialDelta.put("yesterdayReward", yesterdayDelta);
        modified.put("social", socialDelta);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        out.put("reward", rewardList);
        out.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("[social] uid={} 领取信用 +{} → {}", account.getUid(), gain, socialPoint);
        return out;
    }

    private static JSONObject emptyDelta() {
        JSONObject delta = new JSONObject(true);
        delta.put("modified", new JSONObject(true));
        delta.put("deleted", new JSONObject(true));
        return delta;
    }
}
