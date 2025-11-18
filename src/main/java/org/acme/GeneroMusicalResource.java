package org.acme;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;
import io.smallrye.faulttolerance.api.RateLimit;
import java.time.temporal.ChronoUnit;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Path("/generos-musicais")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GeneroMusicalResource {

    @GET
    @Operation(
            summary = "Retorna todos os gêneros musicais (getAll)",
            description = "Retorna uma lista de gêneros musicais por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Lista retornada com sucesso",
            content = @Content(
                    schema = @Schema(implementation = GeneroMusical.class, type = SchemaType.ARRAY)
            )
    )
    @RateLimit(value = 10, window = 10, windowUnit = ChronoUnit.SECONDS)
    @Timeout(value = 800, unit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.6, delay = 5000)
    @Fallback(fallbackMethod = "fallbackGetAll")
    public Response getAll(){
        return Response.ok(GeneroMusical.listAll()).build();
    }

    public Response fallbackGetAll() {
        List<GeneroMusical> listaVazia = Collections.emptyList();
        return Response.ok(listaVazia).build();
    }

    @GET
    @Path("{id}")
    @Operation(
            summary = "Retorna um gênero musical pela busca por ID (getById)",
            description = "Retorna um gênero musical específico pela busca de ID colocado na URL no formato JSON por padrão"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = GeneroMusical.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    @Timeout(value = 500, unit = ChronoUnit.MILLIS)
    @Fallback(fallbackMethod = "fallbackGetById")
    public Response getById(
            @Parameter(description = "Id do gênero musical a ser pesquisado", required = true)
            @PathParam("id") long id){
        GeneroMusical entity = GeneroMusical.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(entity).build();
    }

    public Response fallbackGetById(long id) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("Serviço de busca indisponível para o ID: " + id + ". Tente novamente mais tarde.")
                .type(MediaType.TEXT_PLAIN)
                .build();
    }

    @GET
    @Operation(
            summary = "Retorna os gêneros musicais conforme o sistema de pesquisa (search)",
            description = "Retorna uma lista de gêneros musicais filtrada conforme a pesquisa por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = SearchGeneroMusicalResponse.class)
            )
    )
    @Path("/search")
    public Response search(
            @Parameter(description = "Query de buscar por nome ou descrição")
            @QueryParam("q") String q,
            @Parameter(description = "Campo de ordenação da lista de retorno")
            @QueryParam("sort") @DefaultValue("id") String sort,
            @Parameter(description = "Esquema de filtragem de gêneros musicais por ordem crescente ou decrescente")
            @QueryParam("direction") @DefaultValue("asc") String direction,
            @Parameter(description = "Define qual página será retornada na response")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Define quantos objetos serão retornados por query")
            @QueryParam("size") @DefaultValue("4") int size
    ){
        Set<String> allowed = Set.of("id", "nome", "descricao");
        if(!allowed.contains(sort)){
            sort = "id";
        }

        Sort sortObj = Sort.by(
                sort,
                "desc".equalsIgnoreCase(direction) ? Sort.Direction.Descending : Sort.Direction.Ascending
        );

        int effectivePage = Math.max(page, 0);

        PanacheQuery<GeneroMusical> query;

        if (q == null || q.isBlank()) {
            query = GeneroMusical.findAll(sortObj);
        } else {
            query = GeneroMusical.find(
                    "lower(nome) like ?1 or lower(descricao) like ?1", sortObj, "%" + q.toLowerCase() + "%");
        }

        List<GeneroMusical> generos = query.page(effectivePage, size).list();

        var response = new SearchGeneroMusicalResponse();
        response.GenerosMusicais = generos;
        response.TotalGenerosMusicais = (int) query.count();
        response.TotalPages = query.pageCount();
        response.HasMore = effectivePage < query.pageCount() - 1;
        response.NextPage = response.HasMore ? "http://localhost:8080/generos-musicais/search?q="+(q != null ? q : "")+"&page="+(effectivePage + 1) + (size > 0 ? "&size="+size : "") : "";

        return Response.ok(response).build();
    }

    @POST
    @Operation(
            summary = "Adiciona um registro à lista de gêneros musicais (insert)",
            description = "Adiciona um item à lista de gêneros musicais por meio de POST e request body JSON. O ID é gerado e retornado na resposta."
    )
    @RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = GeneroMusical.class)
            )
    )
    @APIResponse(
            responseCode = "201",
            description = "Created - Retorna o objeto criado com o ID gerado.",
            content = @Content(
                    schema = @Schema(implementation = GeneroMusical.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Bad Request"
    )
    @Transactional
    public Response insert(@Valid GeneroMusical genero, @HeaderParam("Idempotency-Key") String idempotencyKey){
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("O cabeçalho Idempotency-Key é obrigatório para esta operação.")
                    .build();
        }

        GeneroMusical.persist(genero);

        URI location = UriBuilder.fromResource(GeneroMusicalResource.class).path("{id}").build(genero.id);
        return Response
                .created(location)
                .entity(genero)
                .build();
    }

    @DELETE
    @Operation(
            summary = "Remove um registro da lista de gêneros musicais (delete)",
            description = "Remove um item da lista de gêneros musicais por meio de Id na URL"
    )
    @APIResponse(
            responseCode = "204",
            description = "Sem conteúdo"
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    @APIResponse(
            responseCode = "409",
            description = "Conflito - Gênero musical possui músicas vinculadas"
    )
    @Transactional
    @Path("{id}")
    public Response delete(@PathParam("id") long id){
        GeneroMusical entity = GeneroMusical.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long musicasVinculadas = Musica.count("?1 MEMBER OF generos", entity);
        if(musicasVinculadas > 0){
            return Response.status(Response.Status.CONFLICT)
                    .entity("Não é possível deletar o gênero musical. Existem " + musicasVinculadas + " música(s) vinculada(s).")
                    .build();
        }

        GeneroMusical.deleteById(id);
        return Response.noContent().build();
    }

    @PUT
    @Operation(
            summary = "Altera um registro da lista de gêneros musicais (update)",
            description = "Edita um item da lista de gêneros musicais por meio de Id na URL e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = GeneroMusical.class)
            )
    )
    @APIResponse(
            responseCode = "200",
            description = "Item editado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = GeneroMusical.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    @Transactional
    @Path("{id}")
    public Response update(@PathParam("id") long id,@Valid GeneroMusical newGeneroMusical){
        GeneroMusical entity = GeneroMusical.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        entity.nome = newGeneroMusical.nome;
        entity.descricao = newGeneroMusical.descricao;

        return Response.status(Response.Status.OK).entity(entity).build();
    }
}