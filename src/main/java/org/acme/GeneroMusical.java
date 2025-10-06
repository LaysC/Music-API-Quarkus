package org.acme;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "GeneroMusical")
public class GeneroMusical extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(readOnly = true, example = "1")
    public Long id;

    @NotBlank(message = "O nome do gênero não pode ser vazio")
    @Size(min = 2, max = 50, message = "Nome do gênero deve ter entre 2 e 50 caracteres")
    public String nome;

    @Size(max = 200, message = "A descrição não pode ultrapassar 200 caracteres")
    public String descricao;

    // Many-to-Many inverso
    @ManyToMany(mappedBy = "generos", fetch = FetchType.LAZY)
    @JsonIgnore // Oculta a lista de músicas no JSON, evitando ciclos infinitos
    public Set<Musica> musicas = new HashSet<>();

    public GeneroMusical() {}
}
