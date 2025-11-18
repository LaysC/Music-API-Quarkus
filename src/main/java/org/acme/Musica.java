package org.acme;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Musica extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(readOnly = true, example = "1")
    public Long id;

    @NotBlank(message = "O título não pode ser vazio")
    @Size(min = 1, max = 200)
    public String titulo;

    @NotBlank(message = "A letra é obrigatória")
    @Size(max = 2000)
    public String letra;

    @Min(value = 1900, message = "Ano de lançamento inválido")
    public int anoLancamento;

    @DecimalMin(value = "0.0", inclusive = true, message = "Nota mínima é 0.0")
    @DecimalMax(value = "10.0", inclusive = true, message = "Nota máxima é 10.0")
    public double nota;

    @Min(value = 0, message = "Duração não pode ser negativa")
    public int duracaoSegundos;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "artista_id")
    public Artista artista;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "musica_genero",
            joinColumns = @JoinColumn(name = "musica_id"),
            inverseJoinColumns = @JoinColumn(name = "genero_musical_id")
    )
    public Set<GeneroMusical> generos = new HashSet<>();

    public Musica() {}

    public Musica(Long id, String titulo, String letra, int anoLancamento, double nota, int duracaoSegundos) {
        this.id = id;
        this.titulo = titulo;
        this.letra = letra;
        this.anoLancamento = anoLancamento;
        this.nota = nota;
        this.duracaoSegundos = duracaoSegundos;
    }
}