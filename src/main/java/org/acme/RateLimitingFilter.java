package org.acme;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Provider
@ApplicationScoped
@Priority(Priorities.HEADER_DECORATOR)
public class RateLimitingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final int MAX_REQUESTS = 10;
    private static final int WINDOW_SECONDS = 60;

    private final Cache<String, AtomicInteger> requestCounts;

    public RateLimitingFilter() {
        this.requestCounts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(WINDOW_SECONDS))
                .build();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        if (!path.startsWith("/api/v1")) {
            return;
        }

        String clientIp = requestContext.getHeaderString("X-Forwarded-For");
        if (clientIp == null) {
            clientIp = "127.0.0.1";
        }

        AtomicInteger count = requestCounts.get(clientIp, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();

        requestContext.setProperty("rate-limit-remaining", Math.max(0, MAX_REQUESTS - currentCount));

        if (currentCount > MAX_REQUESTS) {
            requestContext.abortWith(Response.status(429)
                    .entity("Limite de requisições excedido. Tente novamente em breve.")
                    .header("X-RateLimit-Limit", MAX_REQUESTS)
                    .header("X-RateLimit-Remaining", 0)
                    .header("Retry-After", WINDOW_SECONDS)
                    .build());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Object remaining = requestContext.getProperty("rate-limit-remaining");

        if (remaining != null) {
            responseContext.getHeaders().add("X-RateLimit-Limit", MAX_REQUESTS);
            responseContext.getHeaders().add("X-RateLimit-Remaining", remaining);
        }
    }
}