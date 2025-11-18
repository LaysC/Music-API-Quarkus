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

import com.fasterxml.jackson.databind.ObjectMapper; // 👈 Necessário para Serialização/Deserialização

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
    ObjectMapper objectMapper; // 👈 Injeção do Jackson

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

    /**
     * O corpo é armazenado como String para evitar problemas de serialização do Hibernate/JAX-RS.
     */
    public static class IdempotencyRecord {
        private int status;
        private String bodyJson; // 👈 Armazenamos o JSON como String
        private int contentLength; // Para retornar o Content-Length correto (opcional)

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
        // A chave só usa o método e o path relativo
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

        try {
            // Tenta obter o registro do cache.
            IdempotencyRecord record = (IdempotencyRecord) cache.get(cacheKey, k -> null)
                    .await().indefinitely();

            if (record != null) {
                // Se o registro for encontrado (chave válida e não expirada), aborta a requisição

                Response.ResponseBuilder responseBuilder = Response
                        .status(record.getStatus())
                        // Retorna a String JSON salva como entidade
                        .entity(record.getBodyJson())
                        .header("Content-Type", "application/json");

                // Retorna o cabeçalho Location para 201 Created
                if (record.getStatus() == 201) {
                    String location = requestContext.getUriInfo().getAbsolutePath().toString() + "/" + record.getBodyJson().substring(record.getBodyJson().indexOf("\"id\":") + 5, record.getBodyJson().indexOf(','));
                    responseBuilder.header("Location", location);
                }

                requestContext.abortWith(responseBuilder.build());
                return;
            }

            requestContext.setProperty(IDEMPOTENT_CONTEXT_PROPERTY,
                    new IdempotentContext(cacheKey, DEFAULT_EXPIRE_AFTER_SECONDS));

        } catch (Exception e) {
            // Em caso de erro de cache/serialização na leitura, o erro é impresso, mas a requisição é permitida
            System.err.println("Erro durante verificação de Idempotência. Permite persistência: " + e.getMessage());
        }
    }


    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object contextProperty = requestContext.getProperty(IDEMPOTENT_CONTEXT_PROPERTY);

        if (contextProperty instanceof IdempotentContext) {
            IdempotentContext context = (IdempotentContext) contextProperty;

            if (responseContext.getStatus() >= 200 && responseContext.getStatus() < 300 && responseContext.hasEntity()) {

                try {
                    // 👈 1. Serializa o corpo da entidade para JSON String
                    String bodyJson = objectMapper.writeValueAsString(responseContext.getEntity());

                    // 👈 2. Cria o registro com a String
                    IdempotencyRecord record = new IdempotencyRecord(
                            responseContext.getStatus(),
                            bodyJson,
                            bodyJson.length()
                    );

                    // 3. Grava o resultado para futuras chamadas
                    cache.get(context.getCacheKey(), k -> record)
                            .await().indefinitely();

                } catch (Exception e) {
                    System.err.println("Erro ao salvar a resposta idempotente no cache: " + e.getMessage());
                }
            }
        }
    }
}