package dev.julioperez.nls.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class RequestCorrelationIdFilter extends OncePerRequestFilter {
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(MDC_KEY, requestId);
        response.setHeader("X-Request-Id", requestId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, requestId)) {
            filterChain.doFilter(request, response);
        }
    }
}
