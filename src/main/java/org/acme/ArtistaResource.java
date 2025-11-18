package org.acme;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder; // Importante para o Location correto

import org.acme.idempotency.Idempotent; // <--- Import da anotação

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;
import java.time.temporal.ChronoUnit;
import io.smallrye.faulttolerance.api.RateLimit;

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

@Path("/api/v1/artistas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ArtistaResource {

    @GET
    @Operation(
            summary = "Retorna todos os artistas (getAll)",
            description = "Retorna uma lista de artistas por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Lista retornada com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Artista.class, type = SchemaType.ARRAY)
            )
    )
    @RateLimit(value = 10, window = 10, windowUnit = ChronoUnit.SECONDS)
    @Timeout(value = 800, unit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.6, delay = 5000)
    @Fallback(fallbackMethod = "fallbackGetAll")
    public Response getAll(){
        return Response.ok(Artista.listAll()).build();
    }

    public Response fallbackGetAll() {
        List<Artista> listaVazia = Collections.emptyList();
        return Response.ok(listaVazia).build();
    }

    @GET
    @Path("{id}")
    @Operation(
            summary = "Retorna um artista pela busca por ID (getById)",
            description = "Retorna um artista específico pela busca de ID colocado na URL no formato JSON por padrão"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Artista.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    @Timeout(value = 500, unit = ChronoUnit.MILLIS)
    @Fallback(fallbackMethod = "fallbackGetById")
    public Response getById(
            @Parameter(description = "Id do artista a ser pesquisado", required = true)
            @PathParam("id") long id){
        Artista entity = Artista.findById(id);
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
            summary = "Retorna os artistas conforme o sistema de pesquisa (search)",
            description = "Retorna uma lista de artistas filtrada conforme a pesquisa por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = SearchArtistaResponse.class)
            )
    )
    @Path("/search")
    public Response search(
            @Parameter(description = "Query de buscar por nome ou país de origem")
            @QueryParam("q") String q,
            @Parameter(description = "Campo de ordenação da lista de retorno")
            @QueryParam("sort") @DefaultValue("id") String sort,
            @Parameter(description = "Esquema de filtragem de artistas por ordem crescente ou decrescente")
            @QueryParam("direction") @DefaultValue("asc") String direction,
            @Parameter(description = "Define qual página será retornada na response")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Define quantos objetos serão retornados por query")
            @QueryParam("size") @DefaultValue("4") int size
    ){
        Set<String> allowed = Set.of("id", "nomeArtistico", "dataDeEstreia", "paisDeOrigem");
        if(!allowed.contains(sort)){
            sort = "id";
        }

        Sort sortObj = Sort.by(
                sort,
                "desc".equalsIgnoreCase(direction) ? Sort.Direction.Descending : Sort.Direction.Ascending
        );

        int effectivePage = Math.max(page, 0);

        PanacheQuery<Artista> query;

        if (q == null || q.isBlank()) {
            query = Artista.findAll(sortObj);
        } else {
            query = Artista.find(
                    "lower(nomeArtistico) like ?1 or lower(paisDeOrigem) like ?1", sortObj, "%" + q.toLowerCase() + "%");
        }

        List<Artista> artistas = query.page(effectivePage, size).list();

        var response = new SearchArtistaResponse();
        response.Artistas = artistas;
        response.TotalArtistas = (int) query.count();
        response.TotalPages = query.pageCount();
        response.HasMore = effectivePage < query.pageCount() - 1;

        response.NextPage = response.HasMore ? "http://localhost:8080/api/v1/artistas/search?q="+(q != null ? q : "")+"&page="+(effectivePage + 1) + (size > 0 ? "&size="+size : "") : "";

        return Response.ok(response).build();
    }

    @POST
    @Operation(
            summary = "Adiciona um registro à lista de artistas (insert)",
            description = "Adiciona um item à lista de artistas por meio de POST e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = Artista.class)
            )
    )
    @APIResponse(
            responseCode = "201",
            description = "Created - Retorna o objeto criado com o ID gerado.",
            content = @Content(
                    schema = @Schema(implementation = Artista.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Bad Request"
    )
    @Transactional
    @Idempotent
    public Response insert(@Valid Artista artista){


        Artista.persist(artista);

        URI location = UriBuilder.fromResource(ArtistaResource.class).path("{id}").build(artista.id);

        return Response
                .created(location)
                .entity(artista)
                .build();
    }

    @DELETE
    @Operation(
            summary = "Remove um registro da lista de artistas (delete)",
            description = "Remove um item da lista de artistas por meio de Id na URL"
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
            description = "Conflito - Artista possui músicas vinculadas",
            content = @Content(
                    mediaType = "text/plain",
                    schema = @Schema(implementation = String.class))
    )
    @Transactional
    @Path("{id}")
    public Response delete(@PathParam("id") long id){
        Artista entity = Artista.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long musicasVinculadas = Musica.count("artista.id = ?1", id);
        if(musicasVinculadas > 0){
            return Response.status(Response.Status.CONFLICT)
                    .entity("Não é possível deletar o artista. Existem " + musicasVinculadas + " música(s) vinculada(s).")
                    .build();
        }

        Artista.deleteById(id);
        return Response.noContent().build();
    }

    @PUT
    @Operation(
            summary = "Altera um registro da lista de artistas (update)",
            description = "Edita um item da lista de artistas por meio de Id na URL e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = Artista.class)
            )
    )
    @APIResponse(
            responseCode = "200",
            description = "Item editado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Artista.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    @Transactional
    @Path("{id}")
    public Response update(@PathParam("id") long id, @Valid Artista newArtista){
        Artista entity = Artista.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        entity.nomeArtistico = newArtista.nomeArtistico;
        entity.nomeCompleto = newArtista.nomeCompleto;
        entity.dataDeEstreia = newArtista.dataDeEstreia;
        entity.paisDeOrigem = newArtista.paisDeOrigem;

        if(newArtista.perfil != null){
            if(entity.perfil == null){
                entity.perfil = new PerfilArtista();
            }
            entity.perfil.descricaoCarreira = newArtista.perfil.descricaoCarreira;
            entity.perfil.estiloMusicalPrincipal = newArtista.perfil.estiloMusicalPrincipal;
            entity.perfil.premiosEReconhecimentos = newArtista.perfil.premiosEReconhecimentos;
        } else {
            entity.perfil = null;
        }

        return Response.status(Response.Status.OK).entity(entity).build();
    }
}