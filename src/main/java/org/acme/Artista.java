package org.acme;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Entity
public class Artista extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(readOnly = true)
    public Long id;

    @NotBlank(message = "O nome artístico não pode ser vazio")
    @Size(min = 2, max = 100, message = "O nome artístico deve ter entre 2 e 100 caracteres")
    public String nomeArtistico;

    public String nomeCompleto;

    @Past(message = "A data de estreia deve ser no passado")
    public LocalDate dataDeEstreia;

    @NotBlank(message = "O país de origem é obrigatório")
    @Size(max = 80)
    public String paisDeOrigem;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "perfil_artista_id")
    public PerfilArtista perfil;

    @OneToMany(mappedBy = "artista", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    public List<Musica> musicas = new ArrayList<>();

    public Artista() {}

    public Artista(Long id, String nomeArtistico, String nomeCompleto, LocalDate dataDeEstreia, String paisDeOrigem, PerfilArtista perfil) {
        this.id = id;
        this.nomeArtistico = nomeArtistico;
        this.nomeCompleto = nomeCompleto;
        this.dataDeEstreia = dataDeEstreia;
        this.paisDeOrigem = paisDeOrigem;
        this.perfil = perfil;
    }
}