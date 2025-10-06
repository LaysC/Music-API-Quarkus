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
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker; // NOVO IMPORT
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong; // NOVO IMPORT

@Path("/artistas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ArtistaResource {
    
    private static final Logger LOGGER = Logger.getLogger(ArtistaResource.class);
    
    // CAMPOS NECESSÁRIOS PARA O CIRCUIT BREAKER E SIMULAÇÃO
    private final AtomicLong counter = new AtomicLong(0); 

    // ====================================================================
    // 1. ENDPOINT COM @TIMEOUT e @FALLBACK (TOLERÂNCIA A FALHAS)
    // ====================================================================
    @GET
    @Path("/{id}/resiliente")
    @Operation(
        summary = "Busca detalhada resiliente",
        description = "Busca que simula lentidão e usa Timeout/Fallback para garantir resposta rápida."
    )
    @APIResponse(responseCode = "200", description = "Retorno do Artista ou do Fallback.")
    @Timeout(500) // TEMPO LIMITE: 500 milissegundos
    @Fallback(fallbackMethod = "retornarArtistaPadrao") // ALTERNATIVA: Chama o fallback se o Timeout estourar
    public Response buscarDetalhesResilientes(
            @Parameter(description = "Id do artista", required = true)
            @PathParam("id") long id) throws InterruptedException {
        
        // SIMULAÇÃO DE LENTIDÃO (pode estourar o Timeout de 500ms)
        Thread.sleep(new Random().nextInt(800)); 

        Artista entity = Artista.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        
        return Response.ok(entity).build();
    }
    
    /**
     * MÉTODO DE FALLBACK (Alternativa Segura)
     */
    public Response retornarArtistaPadrao(long id) {
        LOGGER.warnf("Timeout acionado para Artista ID %d. Retornando objeto padrão.", id);
        
        Artista fallbackArtista = new Artista();
        fallbackArtista.nomeArtistico = "DADOS TEMPORARIAMENTE INDISPONÍVEIS";
        fallbackArtista.paisDeOrigem = "Fallback Ativo";
        
        return Response.ok(fallbackArtista).build();
    }
    
    // ====================================================================
    // 2. MÉTODO POST COM @CIRCUITBREAKER
    // ====================================================================
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
    @CircuitBreaker( // DISJUNTOR: Protege a persistência contra falhas em cascata
        requestVolumeThreshold = 5,
        failureRatio = 0.6,
        delay = 10000 
    )
    public Response insert(@Valid Artista artista){
        
        // --- CÓDIGO DE SIMULAÇÃO DO CIRCUIT BREAKER ---
        final Long invocationNumber = counter.getAndIncrement();
        if (invocationNumber % 5 >= 3) { 
            LOGGER.errorf("Simulação de Falha #%d: Forçando RuntimeException para abrir o Circuit Breaker.", invocationNumber);
            throw new RuntimeException("Falha na persistência simulada para abrir o disjuntor.");
        }
        // ---------------------------------------------------------

        Artista.persist(artista);

        URI location = URI.create("/artistas/" + artista.id);
        return Response
                .created(location)
                .entity(artista)
                .build();
    }

    // ====================================================================
    // MÉTODOS CRUD RESTANTES
    // ====================================================================

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
    public Response getById(
            @Parameter(description = "Id do artista a ser pesquisado", required = true)
            @PathParam("id") long id){
        Artista entity = Artista.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(entity).build();
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
        response.NextPage = response.HasMore ? "http://localhost:8080/artistas/search?q="+(q != null ? q : "")+"&page="+(effectivePage + 1) + (size > 0 ? "&size="+size : "") : "";

        return Response.ok(response).build();
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
