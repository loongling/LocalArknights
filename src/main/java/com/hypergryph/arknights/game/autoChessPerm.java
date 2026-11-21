package com.hypergryph.arknights.game;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/autoChessPerm")
public class autoChessPerm {

    @PostMapping(
            value = {"/syncInfo"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject syncInfo(@RequestHeader(value = "secret", required = false)String secret, @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 同步卫戍协议数据/autoChessPerm/syncInfo");
        ArknightsApplication.LOGGER.info("ACP syncInfo:" + jsonBody);
        String actId = jsonBody.getString("actId");
        JSONObject result = new JSONObject();
        JSONObject playerDataDelta = new JSONObject();
        JSONObject modified = new JSONObject();
        playerDataDelta.put("deleted", new JSONObject());
        JSONObject autochessPerm = new JSONObject();
        JSONObject seasons = new JSONObject();
        JSONObject IDs = new JSONObject();
        IDs.put("soldOut", false);
        seasons.put(actId, IDs);
        autochessPerm.put("seasons", seasons);
        modified.put("autochessPerm", autochessPerm);
        playerDataDelta.put("modified", modified);
        result.put("playerDataDelta", playerDataDelta);
        result.put("diyChanged", new JSONArray());
        result.put("changed", new JSONArray());
        return result;
        //TODO 此数据应该按账号同步，但数据少尚不知道如何同步
    }

    @PostMapping(
            value = {"/startGuideBattle"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject startGuideBattle(@RequestHeader(value = "secret", required = false)String secret, @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 开始卫戍协议教程关/autoChessPerm/startGuideBattle");
        ArknightsApplication.LOGGER.info("ACP GuidaStart:" + jsonBody);

        String stageId = jsonBody.getString("stageId");

        JSONObject result = new JSONObject();
        result.put("result", 0);
        result.put("battleId", java.util.UUID.randomUUID().toString());

        JSONObject playerDataDelta = new JSONObject();
        playerDataDelta.put("modified", new JSONObject());
        playerDataDelta.put("deleted", new JSONObject());
        result.put("playerDataDelta", playerDataDelta);

        ArknightsApplication.LOGGER.info("ACP GuideStart -> battleId=" + result.getString("battleId") + " stage=" + stageId);
        return result;
    }

    @PostMapping(
            value = {"/finishGuideBattle"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject finishGuideBattle(@RequestHeader(value = "secret", required = false) String secret, @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 结束卫戍协议教程关/autoChessPerm/finishGuideBattle");
        String activityId = jsonBody.getString("activityId");
        List<Account> Accounts = userDao.queryAccountBySecret(secret);
        JSONObject result = new JSONObject(true);
        if (Accounts.size() != 1) {
            result.put("result", 2);
            result.put("error", "无法查询到此账户");
            return result;
        }
        JSONArray pushMessage = new JSONArray();
        JSONObject userSyncData = JSONObject.parseObject(((Account)Accounts.get(0)).getUser());
        Long uid = ((Account)Accounts.get(0)).getUid();
        JSONObject autochessPerm = userSyncData.getJSONObject("autochessPerm");
        int cnt = autochessPerm.getJSONObject("seasons").getJSONObject(activityId).getJSONObject("mode").getJSONObject("mode_training_2").getInteger("completeCnt");
        autochessPerm.getJSONObject("seasons").getJSONObject(activityId).getJSONObject("mode").getJSONObject("mode_training_2").put("completeCnt", cnt + 1);
        if (cnt == 0) {
            autochessPerm.getJSONObject("seasons").getJSONObject(activityId).getJSONObject("mode").getJSONObject("mode_multi_funny").put("unlock", true);
            autochessPerm.getJSONObject("seasons").getJSONObject(activityId).getJSONObject("mode").getJSONObject("mode_single_funny").put("unlock", true);
            autochessPerm.getJSONObject("seasons").getJSONObject(activityId).getJSONObject("mode").getJSONObject("mode_single_1").put("unlock", true);
            autochessPerm.getJSONObject("trainingModeFin").put("mode_training_2", 1);
            JSONObject autoChessSeasonUnlockMode1 =new JSONObject();
            JSONObject autoChessSeasonUnlockMode2 =new JSONObject();
            JSONObject autoChessSeasonUnlockMode3 =new JSONObject();
            JSONObject payload1 = new JSONObject();
            JSONObject payload2 = new JSONObject();
            JSONObject payload3 = new JSONObject();
            payload1.put("actId",activityId);
            payload2.put("actId",activityId);
            payload3.put("actId",activityId);
            payload1.put("modeId","mode_multi_funny");
            payload2.put("modeId","mode_single_funny");
            payload3.put("modeId","mode_single_1");
            autoChessSeasonUnlockMode1.put("path", "autoChessSeasonUnlockMode");
            autoChessSeasonUnlockMode2.put("path", "autoChessSeasonUnlockMode");
            autoChessSeasonUnlockMode3.put("path", "autoChessSeasonUnlockMode");
            autoChessSeasonUnlockMode1.put("payload", payload1);
            autoChessSeasonUnlockMode2.put("payload", payload2);
            autoChessSeasonUnlockMode3.put("payload", payload3);
            pushMessage.add(autoChessSeasonUnlockMode1);
            pushMessage.add(autoChessSeasonUnlockMode2);
            pushMessage.add(autoChessSeasonUnlockMode3);
        } else{
            int TCnt = autochessPerm.getJSONObject("trainingModeFin").getInteger("mode_training_2");
            autochessPerm.getJSONObject("trainingModeFin").put("mode_training_2", TCnt+1);
        }
        userSyncData.put("autochessPerm", autochessPerm);
        userDao.setUserData(uid, userSyncData);
        JSONObject modified = new JSONObject(true);
        JSONObject playerDataDelta = new JSONObject(true);
        modified.put("autochessPerm", autochessPerm);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        result.put("result", 0);
        result.put("playerDataDelta", playerDataDelta);
        result.put("pushMessage", pushMessage);
        return result;
    }

    @PostMapping(
            value = {"/startMatch"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject startMatch(@RequestHeader(value = "secret", required = false) String secret, @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 开始卫戍协议匹配 /autoChessPerm/startMatch");
        JSONObject result = new JSONObject(true);
        return result;
    }

    @PostMapping(
            value = {"/createTeam"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject createTeam(@RequestHeader(value = "secret", required = false) String secret, @RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 创建卫戍协议房间/autoChessPerm/finishGuideBattle");
        JSONObject result = new JSONObject(true);
        JSONObject team = new JSONObject();
        String actId = jsonBody.getString("activityId");
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            return createErrorResponse(400, "服务器维护中");
        }

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
        JSONObject userSyncData = JSONObject.parseObject(((Account)accounts.get(0)).getUser());
        Long uid = ((Account)accounts.get(0)).getUid();
        String server = ArknightsApplication.serverConfig.getJSONObject("server").getString("ip") + ":" + ArknightsApplication.serverConfig.getJSONObject("server").getJSONObject("autochess").getString("room");
        String teamId = randomTeamId();
        for (int i = 0; i < 5; i++) {
            if (com.hypergryph.arknights.core.dao.autochessRoomDao.queryRoom(teamId) == null) {
                break;
            }
            teamId = randomTeamId();
        }
        // 落库：首次创建时写入 15 分钟有效期；已存在则复用（不刷新时间）
        try {
            java.util.Map<String, Object> existRoom =
                    com.hypergryph.arknights.core.dao.autochessRoomDao.queryRoom(teamId);
            if (existRoom == null) {
                com.hypergryph.arknights.core.dao.autochessRoomDao.createRoom(
                        teamId, uid, secret, actId, 900);
                ArknightsApplication.LOGGER.info("ACP createTeam 落库: team=" + teamId + " ttl=900s");
            } else {
                ArknightsApplication.LOGGER.info("ACP createTeam 复用已有房间: team=" + teamId
                        + " expireTs=" + existRoom.get("expireTs"));
            }
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("ACP createTeam 落库失败", e);
        }
        team.put("serverAddress", server);
        team.put("serverToken", secret);
        team.put("teamId", teamId); //TODO 待随机生成
        JSONObject modified = new JSONObject(true);
        JSONObject playerDataDelta = new JSONObject(true);
        JSONObject nameCardStyle = userSyncData.getJSONObject("nameCardStyle");
        modified.put("nameCardStyle", nameCardStyle);
        JSONObject team1 = new JSONObject();
        team1.put("teamId", teamId);
        team1.put("startTs", (new Date()).getTime() / 1000L);
        team1.put("teamType", 2);
        JSONObject activityId = new JSONObject();
        activityId.put(actId, team1);
        JSONObject seasons = new JSONObject();
        seasons.put("seasons", activityId);
        modified.put("autochessPerm", seasons);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));
        result.put("result", 0);
        result.put("playerDataDelta", playerDataDelta);
        result.put("team", team);
        userSyncData.getJSONObject("autochessPerm").getJSONObject("seasons").getJSONObject(actId).put("team", team1);
        userDao.setUserData(uid, userSyncData);
        return result;
    }

    private JSONObject createErrorResponse(int code, String message) {
        JSONObject result = new JSONObject();
        result.put("result", code);
        result.put("msg", message);
        result.put("message", "server error");
        return result;
    }

    // 官服 teamId 字符集实测为 24 个符号（0-9 + a-n），对应 64 位整数的 base24 编码
    private static final char[] TEAM_ID_ALPHABET =
            "0123456789abcdefghijklmn".toCharArray();
    private static final java.security.SecureRandom TEAM_ID_RANDOM = new java.security.SecureRandom();

    /** 14 位 base24 随机串（0-9a-n），与官服 teamId 格式一致 */
    public static String randomTeamId() {
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            sb.append(TEAM_ID_ALPHABET[TEAM_ID_RANDOM.nextInt(TEAM_ID_ALPHABET.length)]);
        }
        return sb.toString();
    }

    @PostMapping(
            value = {"/joinTeam"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject joinTeam(@RequestHeader(value = "secret", required = false) String secret,
                               @RequestBody JSONObject jsonBody,
                               HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] 加入卫戍协议房间/autoChessPerm/joinTeam");
        ArknightsApplication.LOGGER.info("ACP joinTeam:" + jsonBody);

        JSONObject result = new JSONObject(true);
        String actId = jsonBody.getString("activityId");
        String teamId = jsonBody.getString("teamId");
        if (teamId == null || teamId.isEmpty()) {
            teamId = jsonBody.getString("teamID");
        }

        List<Account> accounts = userDao.queryAccountBySecret(secret);
        if (accounts.size() != 1) {
            response.setStatus(401);
            return createErrorResponse(2, "账户验证失败");
        }
        Account account = accounts.get(0);
        Long uid = account.getUid();

        java.util.Map<String, Object> room =
                com.hypergryph.arknights.core.dao.autochessRoomDao.queryRoom(teamId);
        if (room == null) {
            // 房间不存在或已过期
            ArknightsApplication.LOGGER.info("ACP joinTeam 房间不存在: " + teamId);
            JSONObject err = new JSONObject(true);
            err.put("result", 1);
            err.put("msg", "房间不存在或已过期");
            JSONObject delta = new JSONObject(true);
            delta.put("modified", new JSONObject(true));
            delta.put("deleted", new JSONObject(true));
            err.put("playerDataDelta", delta);
            return err;
        }

        // 登记成员（每人独立 token）
        try {
            com.hypergryph.arknights.core.dao.autochessRoomDao.joinRoom(teamId, uid, secret, false);
        } catch (Exception e) {
            ArknightsApplication.LOGGER.warn("ACP joinTeam 落库失败", e);
        }

        JSONObject server = ArknightsApplication.serverConfig.getJSONObject("server");
        String address = server.getString("ip") + ":" + server.getJSONObject("autochess").getString("room");

        JSONObject team = new JSONObject();
        team.put("teamId", teamId);
        team.put("serverAddress", address);
        team.put("serverToken", secret);

        JSONObject teamInfo = new JSONObject();
        teamInfo.put("teamId", teamId);
        Object startTs = room.get("createTs");
        teamInfo.put("startTs", startTs == null ? (new java.util.Date()).getTime() / 1000L
                : ((Number) startTs).longValue());
        teamInfo.put("type", 2);
        teamInfo.put("teamType", 2);

        JSONObject act = new JSONObject();
        act.put(actId == null ? "act3autochess" : actId, teamInfo);
        JSONObject seasons = new JSONObject();
        seasons.put("seasons", act);
        JSONObject modified = new JSONObject(true);
        modified.put("autochessPerm", seasons);

        JSONObject playerDataDelta = new JSONObject(true);
        playerDataDelta.put("modified", modified);
        playerDataDelta.put("deleted", new JSONObject(true));

        result.put("result", 0);
        result.put("team", team);
        result.put("playerDataDelta", playerDataDelta);
        ArknightsApplication.LOGGER.info("ACP joinTeam ok: team=" + teamId + " uid=" + uid + " addr=" + address);
        return result;
    }
}
