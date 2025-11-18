package org.acme.idempotency;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Provider
@ApplicationScoped
@Priority(Priorities.HEADER_DECORATOR)
public class IdempotencyFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENT_CONTEXT_PROPERTY = "idempotentContext";
    private static final long DEFAULT_EXPIRE_AFTER_SECONDS = 3600;

    @Inject
    @CacheName("idempotency-cache")
    Cache cache;

    @Inject
    ObjectMapper objectMapper;

    private static class IdempotentContext {
        private final String cacheKey;
        private final long expireAfter;

        public IdempotentContext(String cacheKey, long expireAfter) {
            this.cacheKey = cacheKey;
            this.expireAfter = expireAfter;
        }

        public String getCacheKey() { return cacheKey; }
        public long getExpireAfter() { return expireAfter; }
    }

    public static class IdempotencyRecord {
        private int status;
        private String bodyJson;
        private int contentLength;

        public IdempotencyRecord() {}

        public IdempotencyRecord(int status, String bodyJson, int contentLength) {
            this.status = status;
            this.bodyJson = bodyJson;
            this.contentLength = contentLength;
        }

        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }
        public String getBodyJson() { return bodyJson; }
        public void setBodyJson(String bodyJson) { this.bodyJson = bodyJson; }
        public int getContentLength() { return contentLength; }
        public void setContentLength(int contentLength) { this.contentLength = contentLength; }
    }

    private String createCacheKey(ContainerRequestContext requestContext, String idempotencyKey) {
        String path = requestContext.getUriInfo().getPath();
        return requestContext.getMethod() + ":" + path + ":" + idempotencyKey;
    }


    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!"POST".equalsIgnoreCase(requestContext.getMethod())) {
            return;
        }

        String idempotencyKey = requestContext.getHeaderString(IDEMPOTENCY_KEY_HEADER);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        String cacheKey = createCacheKey(requestContext, idempotencyKey);
        System.out.println("IDEMPOTÊNCIA DEBUG: REQUISIÇÃO. Chave gerada: " + cacheKey);

        try {
            IdempotencyRecord record = (IdempotencyRecord) cache.get(cacheKey, k -> null)
                    .await().indefinitely();

            if (record != null) {
                // LOG 2: CHAVE ENCONTRADA - O ideal para a 2ª chamada
                System.out.println("IDEMPOTÊNCIA DEBUG: Cache HIT. Retornando resposta do cache.");

                Response.ResponseBuilder responseBuilder = Response
                        .status(record.getStatus())
                        .entity(record.getBodyJson())
                        .header("Content-Type", "application/json");

                // Tenta reconstruir o cabeçalho Location para 201 Created
                if (record.getStatus() == 201) {
                    // Busca o ID do corpo JSON (funciona se for um JSON simples)
                    String idValue = record.getBodyJson().substring(record.getBodyJson().indexOf("\"id\":") + 5);
                    idValue = idValue.substring(0, idValue.indexOf(',')).trim();

                    String location = requestContext.getUriInfo().getAbsolutePath().toString() + "/" + idValue;
                    responseBuilder.header("Location", location);
                }

                requestContext.abortWith(responseBuilder.build());
                return;
            } else {
                // LOG 3: CHAVE NÃO ENCONTRADA - O ideal para a 1ª chamada
                System.out.println("IDEMPOTÊNCIA DEBUG: Cache MISS. Chave não encontrada. Permite persistência.");
            }

            requestContext.setProperty(IDEMPOTENT_CONTEXT_PROPERTY,
                    new IdempotentContext(cacheKey, DEFAULT_EXPIRE_AFTER_SECONDS));

        } catch (Exception e) {
            System.err.println("IDEMPOTÊNCIA ERROR: Erro no filtro de requisição: " + e.getMessage());
        }
    }


    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object contextProperty = requestContext.getProperty(IDEMPOTENT_CONTEXT_PROPERTY);

        if (contextProperty instanceof IdempotentContext) {
            IdempotentContext context = (IdempotentContext) contextProperty;

            if (responseContext.getStatus() >= 200 && responseContext.getStatus() < 300 && responseContext.hasEntity()) {

                try {
                    // 1. Serializa o corpo da entidade para JSON String
                    String bodyJson = objectMapper.writeValueAsString(responseContext.getEntity());

                    // 2. Cria o registro com a String
                    IdempotencyRecord record = new IdempotencyRecord(
                            responseContext.getStatus(),
                            bodyJson,
                            bodyJson.length()
                    );

                    cache.get(context.getCacheKey(), k -> record)
                            .await().indefinitely();

                    System.out.println("IDEMPOTÊNCIA DEBUG: RESPOSTA. Resposta salva no cache para chave: " + context.getCacheKey());

                } catch (Exception e) {
                    System.err.println("IDEMPOTÊNCIA ERROR: Erro ao salvar a resposta no cache: " + e.getMessage());
                }
            }
        }
    }
}