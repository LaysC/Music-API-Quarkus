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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Path("/musicas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MusicaResource {

    @GET
    @Operation(
            summary = "Retorna todas as músicas (getAll)",
            description = "Retorna uma lista de músicas por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Lista retornada com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Musica.class, type = SchemaType.ARRAY)
            )
    )
    @RateLimit(value = 10, window = 10, windowUnit = ChronoUnit.SECONDS)
    @Timeout(value = 800, unit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.6, delay = 5000)
    @Fallback(fallbackMethod = "fallbackGetAll")
    public Response getAll(){
        return Response.ok(Musica.listAll()).build();
    }

    public Response fallbackGetAll() {
        List<Musica> listaVazia = Collections.emptyList();
        return Response.ok(listaVazia).build();
    }

    @GET
    @Path("{id}")
    @Operation(
            summary = "Retorna uma música pela busca por ID (getById)",
            description = "Retorna uma música específica pela busca de ID colocado na URL no formato JSON por padrão"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Musica.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    @Timeout(value = 500, unit = ChronoUnit.MILLIS)
    @Fallback(fallbackMethod = "fallbackGetById")
    public Response getById(
            @Parameter(description = "Id da música a ser pesquisada", required = true)
            @PathParam("id") long id){
        Musica entity = Musica.findById(id);
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
            summary = "Retorna as músicas conforme o sistema de pesquisa (search)",
            description = "Retorna uma lista de músicas filtrada conforme a pesquisa por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = SearchMusicaResponse.class)
            )
    )
    @Path("/search")
    public Response search(
            @Parameter(description = "Query de buscar por título, ano de lançamento ou duração")
            @QueryParam("q") String q,
            @Parameter(description = "Campo de ordenação da lista de retorno")
            @QueryParam("sort") @DefaultValue("id") String sort,
            @Parameter(description = "Esquema de filtragem de músicas por ordem crescente ou decrescente")
            @QueryParam("direction") @DefaultValue("asc") String direction,
            @Parameter(description = "Define qual página será retornada na response")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Define quantos objetos serão retornados por query")
            @QueryParam("size") @DefaultValue("4") int size
    ){
        Set<String> allowed = Set.of("id", "titulo", "letra", "anoLancamento", "nota", "duracaoSegundos");
        if(!allowed.contains(sort)){
            sort = "id";
        }

        Sort sortObj = Sort.by(
                sort,
                "desc".equalsIgnoreCase(direction) ? Sort.Direction.Descending : Sort.Direction.Ascending
        );

        int effectivePage = Math.max(page, 0);

        PanacheQuery<Musica> query;

        if (q == null || q.isBlank()) {
            query = Musica.findAll(sortObj);
        } else {
            try {
                int numero = Integer.parseInt(q);
                query = Musica.find(
                        "anoLancamento = ?1 or duracaoSegundos = ?1",
                        sortObj,
                        numero
                );
            } catch (NumberFormatException e) {
                query = Musica.find(
                        "lower(titulo) like ?1",
                        sortObj,
                        "%" + q.toLowerCase() + "%"
                );
            }
        }

        List<Musica> musicas = query.page(effectivePage, size).list();

        var response = new SearchMusicaResponse();
        response.Musicas = musicas;
        response.TotalMusicas = (int) query.count();
        response.TotalPages = query.pageCount();
        response.HasMore = effectivePage < query.pageCount() - 1;
        response.NextPage = response.HasMore ? "http://localhost:8080/musicas/search?q="+(q != null ? q : "")+"&page="+(effectivePage + 1) + (size > 0 ? "&size="+size : "") : "";

        return Response.ok(response).build();
    }

    @POST
    @Operation(
            summary = "Adiciona um registro à lista de músicas (insert)",
            description = "Adiciona um item à lista de músicas por meio de POST e request body JSON. O ID é gerado e retornado na resposta."
    )
    @RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = Musica.class)
            )
    )
    @APIResponse(
            responseCode = "201",
            description = "Created - Retorna o objeto criado com o ID gerado.",
            content = @Content(
                    schema = @Schema(implementation = Musica.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Bad Request"
    )
    @Transactional
    public Response insert(@Valid Musica musica){

        if(musica.artista != null && musica.artista.id != null){
            Artista a = Artista.findById(musica.artista.id);
            if(a == null){
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Artista com id " + musica.artista.id + " não existe").build();
            }
            musica.artista = a;
        } else {
            musica.artista = null;
        }

        if(musica.generos != null && !musica.generos.isEmpty()){
            Set<GeneroMusical> resolved = new HashSet<>();
            for(GeneroMusical g : musica.generos){
                if(g == null || g.id == 0){
                    continue;
                }
                GeneroMusical fetched = GeneroMusical.findById(g.id);
                if(fetched == null){
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity("Genero Musical com id " + g.id + " não existe").build();
                }
                resolved.add(fetched);
            }
            musica.generos = resolved;
        } else {
            musica.generos = new HashSet<>();
        }

        Musica.persist(musica);

        URI location = UriBuilder.fromResource(MusicaResource.class).path("{id}").build(musica.id);
        return Response
                .created(location)
                .entity(musica)
                .build();
    }

    @DELETE
    @Operation(
            summary = "Remove um registro da lista de músicas (delete)",
            description = "Remove um item da lista de músicas por meio de Id na URL"
    )
    @APIResponse(
            responseCode = "204",
            description = "Sem conteúdo"
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    @Transactional
    @Path("{id}")
    public Response delete(@PathParam("id") long id){
        Musica entity = Musica.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        entity.generos.clear();
        entity.persist();

        Musica.deleteById(id);
        return Response.noContent().build();
    }

    @PUT
    @Operation(
            summary = "Altera um registro da lista de músicas (update)",
            description = "Edita um item da lista de músicas por meio de Id na URL e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = Musica.class)
            )
    )
    @APIResponse(
            responseCode = "200",
            description = "Item editado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Musica.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    @Transactional
    @Path("{id}")
    public Response update(@PathParam("id") long id,@Valid Musica newMusica){
        Musica entity = Musica.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        entity.titulo = newMusica.titulo;
        entity.letra = newMusica.letra;
        entity.anoLancamento = newMusica.anoLancamento;
        entity.nota = newMusica.nota;
        entity.duracaoSegundos = newMusica.duracaoSegundos;

        if(newMusica.artista != null && newMusica.artista.id != null){
            Artista a = Artista.findById(newMusica.artista.id);
            if(a == null){
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Artista com id " + newMusica.artista.id + " não existe").build();
            }
            entity.artista = a;
        } else {
            entity.artista = null;
        }

        if(newMusica.generos != null){
            Set<GeneroMusical> resolved = new HashSet<>();
            for(GeneroMusical g : newMusica.generos){
                if(g == null || g.id == 0) continue;
                GeneroMusical fetched = GeneroMusical.findById(g.id);
                if(fetched == null){
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity("Genero Musical com id " + g.id + " não existe").build();
                }
                resolved.add(fetched);
            }
            entity.generos = resolved;
        } else {
            entity.generos = new HashSet<>();
        }

        return Response.status(Response.Status.OK).entity(entity).build();
    }
}