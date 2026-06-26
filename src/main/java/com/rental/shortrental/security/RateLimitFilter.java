package com.rental.shortrental.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int publicLimitPerMinute;
    private final int authLimitPerMinute;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.public-per-minute:90}") int publicLimitPerMinute,
            @Value("${app.security.rate-limit.auth-per-minute:30}") int authLimitPerMinute
    ) {
        this.enabled = enabled;
        this.publicLimitPerMinute = publicLimitPerMinute;
        this.authLimitPerMinute = authLimitPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        LimitRule rule = ruleFor(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = Instant.now().getEpochSecond();
        String key = rule.name() + ":" + clientIp(request);
        Bucket bucket = buckets.compute(key, (ignored, current) -> {
            if (current == null || now - current.windowStart >= 60) {
                return new Bucket(now, new AtomicInteger(1));
            }
            current.counter.incrementAndGet();
            return current;
        });
        cleanupOldBuckets(now);

        if (bucket.counter.get() > rule.limitPerMinute()) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Too many requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private LimitRule ruleFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/public/")
                || path.startsWith("/guest/data/")
                || path.startsWith("/api/calendar-export/")
                || path.startsWith("/public/test/")) {
            return new LimitRule("public", publicLimitPerMinute);
        }
        if (path.equals("/login") || path.equals("/register")) {
            return new LimitRule("auth", authLimitPerMinute);
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma >= 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    private void cleanupOldBuckets(long now) {
        if (buckets.size() < 512) {
            return;
        }
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Bucket> entry = it.next();
            if (now - entry.getValue().windowStart >= 180) {
                it.remove();
            }
        }
    }

    private record LimitRule(String name, int limitPerMinute) {
    }

    private record Bucket(long windowStart, AtomicInteger counter) {
    }
}
