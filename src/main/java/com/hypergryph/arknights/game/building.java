package com.hypergryph.arknights.game;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import com.hypergryph.arknights.core.dao.userDao;
import com.hypergryph.arknights.core.pojo.Account;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.List;

import static com.hypergryph.arknights.core.work.buildingUpdateLastTs.replaceLastUpdateTime;
import static com.hypergryph.arknights.core.work.checkAccount.checkAccount;
import static com.hypergryph.arknights.core.work.checkAccount.createError;
import static com.hypergryph.arknights.core.work.checkCanCheckIn.canCheckIn;
import static com.hypergryph.arknights.core.work.checkCanCheckIn.getTomorrow4Am;

@RestController
@RequestMapping("/building")
public class building {

    @PostMapping(
            value = {"/sync"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject sync(@RequestBody JSONObject jsonBody, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        ArknightsApplication.LOGGER.info("[/" + clientIp + "] /building/sync");
        ArknightsApplication.LOGGER.info("BuildingSync:" + jsonBody);
        String secret = ArknightsApplication.getSecretByIP(clientIp);
        long ts = ArknightsApplication.getTimestamp();
        JSONObject result = new JSONObject();
        if(checkAccount(secret)){
            List<Account> accounts = userDao.queryAccountBySecret(secret);
            Account account = accounts.get(0);
            JSONObject userData = JSON.parseObject(account.getUser());
            JSONObject playerDataDelta = new JSONObject();
            JSONObject modified = new JSONObject();
            JSONObject deleted = new JSONObject();
            result.put("ts", ts);
            JSONObject building = new JSONObject();
            if (canCheckIn(userData.getJSONObject("event").getLong("building"), ArknightsApplication.getTimestamp())) {
                userData.getJSONObject("event").put("building", getTomorrow4Am());
                JSONObject rlv2 = userData.getJSONObject("rlv2");
                JSONObject mission = userData.getJSONObject("mission");
                JSONObject openServer = userData.getJSONObject("openServer");
                JSONObject gachaNew = userData.getJSONObject("gacha").getJSONObject("newbee");
                JSONObject gacha = new JSONObject();
                gacha.put("gacha", gachaNew);
                JSONObject social = userData.getJSONObject("social");
                JSONObject retro = userData.getJSONObject("retro");
                JSONObject crisisV2 = userData.getJSONObject("crisisV2");
                JSONObject campaignsV2 = userData.getJSONObject("campaignsV2");
                JSONObject sandboxPerm = userData.getJSONObject("sandboxPerm");
                JSONObject dungeon = userData.getJSONObject("dungeon");
                JSONObject mainline = userData.getJSONObject("mainline");
                JSONObject shop = userData.getJSONObject("shop");
                JSONObject nameCardStyle = userData.getJSONObject("nameCardStyle");
                building =userData.getJSONObject("building");
                JSONObject buildingRoom = new JSONObject();
                buildingRoom = userData.getJSONObject("building").getJSONObject("rooms");
                replaceLastUpdateTime(buildingRoom, ArknightsApplication.getTimestamp());
                building.put("rooms", buildingRoom);
                building.getJSONObject("status").getJSONObject("labor").put("lastUpdateTime", ArknightsApplication.getTimestamp());
                playerDataDelta.put("deleted", deleted);
                modified.put("rlv2", rlv2);
                modified.put("mission", mission);
                modified.put("gacha", gacha);
                modified.put("social", social);
                modified.put("openServer", openServer);
                modified.put("campaigns", campaignsV2);
                modified.put("sandboxPerm", sandboxPerm);
                modified.put("dungeon", dungeon);
                modified.put("mainline", mainline);
                modified.put("shop", shop);
                modified.put("nameCardStyle", nameCardStyle);
                modified.put("retro", retro);
                modified.put("crisisV2", crisisV2);
                modified.put("building", building);
                playerDataDelta.put("modified", modified);
                result.put("playerDataDelta", playerDataDelta);
                return result;
            } else {
                JSONObject eventBuilding = new JSONObject();
                JSONObject buildingRoom = new JSONObject();
                Long buildingEvent = userData.getJSONObject("event").getLong("building");
                eventBuilding.put("building", buildingEvent);
                modified.put("event", eventBuilding);
                JSONObject buildingStatus = userData.getJSONObject("building").getJSONObject("status");
                buildingStatus.getJSONObject("labor").put("lastUpdateTime", ArknightsApplication.getTimestamp());
                building.put("status", buildingStatus);
                buildingRoom = userData.getJSONObject("building").getJSONObject("rooms");
                replaceLastUpdateTime(buildingRoom, ArknightsApplication.getTimestamp());
            }
        }
        else{
            return createError(secret, response);
        }
        return result;
    }
}
