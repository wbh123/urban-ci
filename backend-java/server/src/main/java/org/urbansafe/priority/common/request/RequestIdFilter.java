package org.urbansafe.priority.common.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为每个请求生成或复用安全的请求编号，并写回响应头。 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-UrbanSafe-Request-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = normalizeRequestId(request.getHeader(REQUEST_ID_HEADER));
        RequestContext.setRequestId(requestId);
        RequestContext.setClientIp(request.getRemoteAddr());
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }

    private String normalizeRequestId(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > 128) {
            return UUID.randomUUID().toString();
        }

        String trimmed = candidate.trim();
        if (!trimmed.matches("[A-Za-z0-9._:-]+")) {
            return UUID.randomUUID().toString();
        }
        return trimmed;
    }
}
