package com.hypergryph.arknights;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * HTTP 请求日志拦截器。
 * body 必须在 controller 读取后才有内容，所以放到 afterCompletion 打印，
 * 并依赖 ContentCachingRequestWrapper(由 WebConfig 中注册的 Filter 包装)。
 * 旧实现用 System.out.println 且在 preHandle 读 body，导致 body 永远为空且不进日志文件。
 */
@Component
public class HttpRequestLoggerInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestLoggerInterceptor.class);

    private static final Set<String> IGNORED_PATHS = new HashSet<>(Arrays.asList(
            "/syncPushMessage", "/pb/async", "/event", "/batch_event", "/error", "/beat"
    ));

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String requestURI = request.getRequestURI();

        if (IGNORED_PATHS.stream().anyMatch(requestURI::startsWith)) {
            return;
        }

        // [TEMP-DIAG] 打印所有请求头，确认客户端认证方式
        java.util.Enumeration<String> hnames = request.getHeaderNames();
        StringBuilder hdrs = new StringBuilder();
        while (hnames.hasMoreElements()) {
            String hn = hnames.nextElement();
            hdrs.append(hn).append("=").append(request.getHeader(hn)).append(" | ");
        }
        LOGGER.info("[DIAG-HEADERS] " + request.getMethod() + " " + requestURI + " => " + hdrs);

        StringBuilder logMessage = new StringBuilder("----- HTTP Request -----")
                .append("\nURL: ").append(requestURI)
                .append("\nMethod: ").append(request.getMethod());
        String params = request.getQueryString();
        if (params != null && !params.isEmpty()) {
            logMessage.append("\nParams: ").append(params);
        }
        String body = getRequestBody(request);
        if (!body.isEmpty()) {
            logMessage.append("\nBody:\n").append(body);
        }
        logMessage.append("\n------------------------");

        LOGGER.info(logMessage.toString());
    }

    private String getRequestBody(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod()) && !"PUT".equalsIgnoreCase(request.getMethod())) {
            return "";
        }
        if (request instanceof ContentCachingRequestWrapper) {
            byte[] content = ((ContentCachingRequestWrapper) request).getContentAsByteArray();
            if (content.length == 0) {
                return "";
            }
            String charset = request.getCharacterEncoding();
            try {
                return new String(content, charset != null ? charset : "UTF-8");
            } catch (Exception e) {
                return "[Error reading body]";
            }
        }
        return "";
    }
}
