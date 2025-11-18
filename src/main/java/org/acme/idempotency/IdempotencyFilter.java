package org.acme.idempotency;

// 1. IMPORTAMOS O CAFFEINE DIRETAMENTE
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration; // Usamos Duration para o Caffeine
import java.time.Instant;

@Provider
@ApplicationScoped
@Priority(Priorities.HEADER_DECORATOR)
public class IdempotencyFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    private static final String IDEMPOTENT_CONTEXT_PROPERTY = "idempotent-context";

    // 2. NÃO USAMOS @Inject. Criamos nosso próprio cache.
    private final Cache<String, IdempotencyRecord> cache;

    @Context
    ResourceInfo resourceInfo;

    // 3. Construtor para inicializar o cache do Caffeine
    public IdempotencyFilter() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(1000) // Valor do seu application.properties
                .expireAfterWrite(Duration.ofHours(1)) // Valor do seu application.properties
                .build();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        Method method = resourceInfo.getResourceMethod();
        Class<?> clazz = resourceInfo.getResourceClass();

        Idempotent methodAnnotation = method.getAnnotation(Idempotent.class);
        Idempotent classAnnotation = clazz.getAnnotation(Idempotent.class);

        if (methodAnnotation == null && classAnnotation == null) {
            return;
        }

        Idempotent idempotentConfig = methodAnnotation != null ? methodAnnotation : classAnnotation;
        String idempotencyKey = requestContext.getHeaderString(IDEMPOTENCY_KEY_HEADER);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            requestContext.abortWith(Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("O cabeçalho X-Idempotency-Key é obrigatório para esta operação")
                    .build());
            return;
        }

        String cacheKey = createCacheKey(requestContext, idempotencyKey);

        // 4. USAMOS A API DO CAFFEINE: getIfPresent()
        IdempotencyRecord record = cache.getIfPresent(cacheKey);

        if (record != null) {
            // Achamos! Aborta e retorna o cache
            requestContext.abortWith(Response
                    .status(record.getStatus())
                    .entity(record.getBody())
                    .build());
            return;
        }

        // Não achamos, armazena o contexto para o response filter
        requestContext.setProperty(IDEMPOTENT_CONTEXT_PROPERTY,
                new IdempotentContext(cacheKey, idempotentConfig.expireAfter()));
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        IdempotentContext context = (IdempotentContext) requestContext.getProperty(IDEMPOTENT_CONTEXT_PROPERTY);
        if (context == null) {
            return;
        }

        IdempotencyRecord record = new IdempotencyRecord(
                responseContext.getStatus(),
                responseContext.getEntity(),
                Instant.now().plusSeconds(context.getExpireAfter())
        );

        // 5. USAMOS A API DO CAFFEINE: put()
        // Isso resolve o "Cannot resolve method 'put'"
        cache.put(context.getCacheKey(), record);
    }

    private String createCacheKey(ContainerRequestContext requestContext, String idempotencyKey) {
        return requestContext.getMethod() + ":" +
                requestContext.getUriInfo().getPath() + ":" +
                idempotencyKey;
    }

    // --- Classes Internas ---

    // Contexto para passar dados entre os filtros
    private static class IdempotentContext {
        private final String cacheKey;
        private final int expireAfter;

        public IdempotentContext(String cacheKey, int expireAfter) {
            this.cacheKey = cacheKey;
            this.expireAfter = expireAfter;
        }
        public String getCacheKey() { return cacheKey; }
        public int getExpireAfter() { return expireAfter; }
    }

    // Registro do cache.
    // Declarada como 'public static' para evitar problemas de serialização/acesso
    public static class IdempotencyRecord {
        public int status;
        public Object body;
        public Instant expiry;

        public IdempotencyRecord() {}

        public IdempotencyRecord(int status, Object body, Instant expiry) {
            this.status = status;
            this.body = body;
            this.expiry = expiry;
        }
        public int getStatus() { return status; }
        public Object getBody() { return body; }
        public Instant getExpiry() { return expiry; }
    }
}