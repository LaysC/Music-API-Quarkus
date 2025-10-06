package org.acme;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Path("/artistas")
@Produces(MediaType.APPLICATION_JSON) // Retorno padrão JSON para todos os métodos
@Consumes(MediaType.APPLICATION_JSON) // Consumo padrão JSON para POST/PUT
public class ArtistaResource {

    // Artista.listAll() está correto e retorna uma lista
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
    public Response getAll(){
        return Response.ok(Artista.listAll()).build();
    }

    // Artista.findById(id) retorna um único artista
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
                    // CORRIGIDO: Removida a menção a 'type = SchemaType.ARRAY'
                    schema = @Schema(implementation = Artista.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    public Response getById(
            @Parameter(description = "Id do artista a ser pesquisado", required = true)
            @PathParam("id") long id){
        Artista entity = Artista.findById(id);
        if(entity == null){
            // Retorna NOT_FOUND sem corpo, por isso não precisa de 'content' no 404
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(entity).build();
    }

    // O método Search é complexo e está muito bem feito!
    @GET
    @Operation(
            summary = "Retorna os artistas conforme o sistema de pesquisa (search)",
            description = "Retorna uma lista de artistas filtrada conforme a pesquisa por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = SearchArtistaResponse.class) // Assumindo que SearchArtistaResponse é o schema correto
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

        // O ArtistaResource deve ter acesso à classe SearchArtistaResponse
        var response = new SearchArtistaResponse();
        response.Artistas = artistas;
        response.TotalArtistas = (int) query.count(); // Melhor usar count() para o total, não query.list().size()
        response.TotalPages = query.pageCount();
        response.HasMore = effectivePage < query.pageCount() - 1;
        response.NextPage = response.HasMore ? "http://localhost:8080/artistas/search?q="+(q != null ? q : "")+"&page="+(effectivePage + 1) + (size > 0 ? "&size="+size : "") : "";

        return Response.ok(response).build();
    }

    // Método POST corrigido para retornar o objeto criado e a URI (Melhor Prática REST)
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
    public Response insert(@Valid Artista artista){
        Artista.persist(artista);

        // CORRIGIDO: Retorna o objeto completo com o ID gerado e o Location Header
        URI location = URI.create("/artistas/" + artista.id);
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

        // Assumindo que a classe Musica existe e tem o método count.
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
                    // CORRIGIDO: Removida a menção a 'type = SchemaType.ARRAY'
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

        // Atualização dos campos (persistência é automática devido ao @Transactional)
        entity.nomeArtistico = newArtista.nomeArtistico;
        entity.nomeCompleto = newArtista.nomeCompleto;
        entity.dataDeEstreia = newArtista.dataDeEstreia;
        entity.paisDeOrigem = newArtista.paisDeOrigem;

        // Atualizar perfil
        if(newArtista.perfil != null){
            if(entity.perfil == null){
                // Assumindo que PerfilArtista existe e é uma Entidade ou Embeddable
                entity.perfil = new PerfilArtista();
            }
            entity.perfil.descricaoCarreira = newArtista.perfil.descricaoCarreira;
            entity.perfil.estiloMusicalPrincipal = newArtista.perfil.estiloMusicalPrincipal;
            entity.perfil.premiosEReconhecimentos = newArtista.perfil.premiosEReconhecimentos;
        } else {
            // Se não vier perfil no request, limpa o perfil existente
            entity.perfil = null;
        }

        return Response.status(Response.Status.OK).entity(entity).build();
    }
}