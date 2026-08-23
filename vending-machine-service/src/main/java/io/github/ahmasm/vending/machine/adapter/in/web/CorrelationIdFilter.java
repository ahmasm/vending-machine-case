package io.github.ahmasm.vending.machine.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private static final String CORRELATION_ID_ATTRIBUTE =
            CorrelationIdFilter.class.getName() + ".correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        var correlationId = UUID.randomUUID().toString();
        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        filterChain.doFilter(request, response);
    }

    public static String correlationId(HttpServletRequest request) {
        var value = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (value instanceof String correlationId && !correlationId.isBlank()) {
            return correlationId;
        }
        throw new IllegalStateException("Correlation ID is unavailable");
    }
}
