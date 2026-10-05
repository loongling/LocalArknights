package com.hypergryph.arknights;

import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

//全局异常兜底：未实现的游戏业务接口(404)返回"空增量成功响应"，避免客户端因默认404页面卡死；非游戏路径404返回结构化JSON；捕获未处理异常，避免堆栈泄露。
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Set<String> GAME_API_PREFIXES = new HashSet<>(Arrays.asList(
            "/account", "/quest", "/charBuild", "/char", "/shop", "/gacha",
            "/crisis", "/crisisV2", "/story", "/storyreview", "/social", "/mail",
            "/building", "/activity", "/act", "/rlv2", "/sandboxPerm", "/siracusaMap",
            "/deepSea", "/retro", "/tower", "/dungeon", "/mission", "/medal",
            "/skin", "/emoticon", "/consumable", "/checkIn", "/ticket", "/carousel",
            "/charm", "/car", "/background", "/homeTheme", "/avatar", "/recruit",
            "/inventory", "/troop", "/status", "/equipment", "/gallery", "/dexNav",
            "/nameCardStyle", "/tshop", "/invite", "/backflow", "/mainline",
            "/openServer", "/trainingGround", "/recalRune", "/performanceStory",
            "/aprilFool", "/collectionReward", "/firework", "/charRotation",
            "/limitedBuff", "/templateTrap", "/campaignsV2", "/rune", "/share",
            "/setting", "/pay"
    ));

    private static final Set<String> TELEMETRY_PATHS = new HashSet<>(Arrays.asList(
            "/batch_event", "/event_report", "/api/h", "/beat", "/event",
            "/error", "/syncPushMessage", "/pb/async"
    ));

    @ExceptionHandler(NoHandlerFoundException.class)
    public Object handleNotFound(HttpServletRequest request, HttpServletResponse response, NoHandlerFoundException ex) {
        String uri = request.getRequestURI();
        String clientIp = ArknightsApplication.getIpAddr(request);
        if (TELEMETRY_PATHS.stream().anyMatch(uri::startsWith)) {
            response.setStatus(HttpStatus.OK.value());
            return new JSONObject(true);
        }
        if (isGameApi(uri)) {
            LOGGER.warn("[/" + clientIp + "] 未实现的游戏接口(返回空增量): " + request.getMethod() + " " + uri);
            response.setStatus(HttpStatus.OK.value());
            JSONObject result = new JSONObject(true);
            result.put("result", 0);
            JSONObject delta = new JSONObject(true);
            delta.put("modified", new JSONObject(true));
            delta.put("deleted", new JSONObject(true));
            result.put("playerDataDelta", delta);
            return result;
        }
        LOGGER.warn("[/" + clientIp + "] 404 未匹配路由: " + request.getMethod() + " " + uri);
        response.setStatus(HttpStatus.NOT_FOUND.value());
        JSONObject result = new JSONObject(true);
        result.put("statusCode", 404);
        result.put("error", "Not Found");
        result.put("message", "no handler for " + uri);
        return result;
    }

    @ExceptionHandler(Exception.class)
    public JSONObject handleAny(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.error("[/" + clientIp + "] 处理 " + request.getRequestURI() + " 时异常: " + ex.getMessage(), ex);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        JSONObject result = new JSONObject(true);
        result.put("statusCode", 500);
        result.put("error", "Internal Server Error");
        result.put("message", ex.getMessage() == null ? "unknown" : ex.getMessage());
        return result;
    }

    private boolean isGameApi(String uri) {
        if (uri == null) return false;
        for (String p : GAME_API_PREFIXES) {
            if (uri.startsWith(p + "/") || uri.equals(p)) return true;
        }
        return false;
    }
}
