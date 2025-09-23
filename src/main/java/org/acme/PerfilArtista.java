package org.acme;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Entity
public class PerfilArtista extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(readOnly = true)
    public Long id;

    @Size(max = 2000, message = "A descrição da carreira não pode ultrapassar 2000 caracteres")
    @Column(length = 2000)
    public String descricaoCarreira;

    @Size(max = 200, message = "O estilo musical não pode ultrapassar 200 caracteres")
    public String estiloMusicalPrincipal;

    public String premiosEReconhecimentos;

    // One-to-One: um perfil pertence a um artista
    @OneToOne(mappedBy = "perfil", fetch = FetchType.LAZY)
    @JsonIgnore
    public Artista artista;

    public PerfilArtista() {}

    public PerfilArtista(String descricaoCarreira, String estiloMusicalPrincipal, String premiosEReconhecimentos) {
        this.descricaoCarreira = descricaoCarreira;
        this.estiloMusicalPrincipal = estiloMusicalPrincipal;
        this.premiosEReconhecimentos = premiosEReconhecimentos;
    }
}