package com.hypergryph.arknights;

import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class HttpRequestLoggerInterceptor implements HandlerInterceptor {

    private static final Set<String> IGNORED_PATHS = new HashSet<>();

    static {
        IGNORED_PATHS.add("/syncPushMessage");
        IGNORED_PATHS.add("/pb/async");
        IGNORED_PATHS.add("/event");
        IGNORED_PATHS.add("/batch_event");
        IGNORED_PATHS.add("/error");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestURI = request.getRequestURI();

        if (IGNORED_PATHS.stream().anyMatch(requestURI::startsWith)) {
            return true;
        }

        ContentCachingRequestWrapper wrappedRequest = request instanceof ContentCachingRequestWrapper
                ? (ContentCachingRequestWrapper) request
                : new ContentCachingRequestWrapper(request);

        String method = wrappedRequest.getMethod();
        String params = getParams(wrappedRequest);
        String headers = getHeaders(wrappedRequest);
        String body = getRequestBody(wrappedRequest);

        StringBuilder logMessage = new StringBuilder("----- HTTP Request -----\n")
                .append("URL: ").append(requestURI).append("\n")
                .append("Method: ").append(method).append("\n");
        if (!params.isEmpty()) {
            logMessage.append("Params: ").append(params).append("\n");
        }
        logMessage.append("Headers: \n").append(headers).append("\n");
        if (!body.isEmpty()) {
            logMessage.append("Body: \n").append(body).append("\n");
        }
        logMessage.append("------------------------");

        System.out.println(logMessage);
        return true;
    }

    private String getParams(HttpServletRequest request) {
        return request.getQueryString() != null ? request.getQueryString() : "";
    }

    private String getHeaders(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        return headerNames != null ?
                java.util.Collections.list(headerNames).stream()
                        .map(header -> header + ": " + request.getHeader(header))
                        .collect(Collectors.joining("\n"))
                : "";
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        if (!"POST".equalsIgnoreCase(request.getMethod()) && !"PUT".equalsIgnoreCase(request.getMethod())) {
            return "";
        }
        try {
            byte[] content = request.getContentAsByteArray();
            return content.length > 0 ? new String(content, request.getCharacterEncoding()) : "";
        } catch (IOException e) {
            return "[Error reading body]";
        }
    }
}
