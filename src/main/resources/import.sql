-- This file allow to write SQL commands that will be emitted in test and dev.
-- The commands are commented as their support depends of the database
-- insert into myentity (id, field) values(1, 'field-1');
-- insert into myentity (id, field) values(2, 'field-2');
-- insert into myentity (id, field) values(3, 'field-3');
-- alter sequence myentity_seq restart with 4;

insert into PerfilArtista (descricaoCarreira, estiloMusicalPrincipal, premiosEReconhecimentos) values(
                                                                                                         'Vocalista, compositor e líder da banda de rock Queen. Sua carreira foi marcada por performances energéticas e um alcance vocal lendário. É considerado um dos maiores cantores da história da música.',
                                                                                                         'Rock, Pop, Ópera',
                                                                                                         'Grammy Lifetime Achievement Award, Rock and Roll Hall of Fame, Brit Awards, Ivor Novello Award'
                                                                                                     );

insert into PerfilArtista (descricaoCarreira, estiloMusicalPrincipal, premiosEReconhecimentos) values(
                                                                                                         'Banda de rock britânica formada em Londres em 1970. É conhecida por seu som eclético, com a fusão de hard rock, rock progressivo e ópera. Seus shows grandiosos e hits atemporais a tornaram uma das maiores de todos os tempos.',
                                                                                                         'Rock, Glam Rock, Hard Rock',
                                                                                                         'Brit Awards for Outstanding Contribution to British Music, Rock and Roll Hall of Fame'
                                                                                                     );

insert into PerfilArtista (descricaoCarreira, estiloMusicalPrincipal, premiosEReconhecimentos) values(
                                                                                                         'Cantora, compositora e produtora americana. Famosa por sua voz potente e habilidade de misturar gêneros como R&B, hip-hop, pop e soul. É uma das artistas mais premiadas da história.',
                                                                                                         'Pop, R&B, Hip-Hop',
                                                                                                         '28 Grammy Awards, MTV Video Music Awards, Billboard Music Awards'
                                                                                                     );

insert into PerfilArtista (descricaoCarreira, estiloMusicalPrincipal, premiosEReconhecimentos) values(
                                                                                                         'Banda de rock alternativo formada em 1985 em Los Angeles. Sua música é caracterizada por letras melancólicas, paisagens sonoras etéreas e a voz única de Robert Smith. A banda foi pioneira do gênero gótico.',
                                                                                                         'Rock Alternativo, Pós-Punk, Gótico',
                                                                                                         'BRIT Awards, indicações ao Grammy, Ivor Novello Award'
                                                                                                     );

insert into PerfilArtista (descricaoCarreira, estiloMusicalPrincipal, premiosEReconhecimentos) values(
                                                                                                         'Banda de rock britânica que se formou em 1968. Conhecida pela sua sonoridade distinta, com uso de bateria, percussão, teclados e vocais complexos. É considerada uma das bandas mais influentes do rock progressivo.',
                                                                                                         'Rock Progressivo, Art Rock',
                                                                                                         'Rock and Roll Hall of Fame, 7 Grammys, Hall of Fame da revista Rolling Stone'
                                                                                                     );

-- Insere dados na tabela Artista
insert into artista (nomeArtistico, nomeCompleto, dataDeEstreia, paisDeOrigem, perfil_artista_id) values('Freddie Mercury', 'Farrokh Bulsara', '1970-01-01', 'Reino Unido', 1);
insert into artista (nomeArtistico, nomeCompleto, dataDeEstreia, paisDeOrigem, perfil_artista_id) values('Queen', 'Queen', '1970-06-27', 'Reino Unido', 2);
insert into artista (nomeArtistico, nomeCompleto, dataDeEstreia, paisDeOrigem, perfil_artista_id) values('Beyoncé', 'Beyoncé Giselle Knowles-Carter', '1997-01-01', 'Estados Unidos', 3);
insert into artista (nomeArtistico, nomeCompleto, dataDeEstreia, paisDeOrigem, perfil_artista_id) values('The Cure', 'The Cure', '1976-05-18', 'Reino Unido', 4);
insert into artista (nomeArtistico, nomeCompleto, dataDeEstreia, paisDeOrigem, perfil_artista_id) values('Genesis', 'Genesis', '1967-01-01', 'Reino Unido', 5);

-- Insere dados na tabela GeneroMusical
insert into GeneroMusical (nome, descricao) values('Rock', 'Um gênero musical popular que se desenvolveu na década de 1950 com ritmos fortes e uso de guitarras elétricas.');
insert into GeneroMusical (nome, descricao) values('Pop', 'Gênero que apela a um público amplo, com melodias cativantes e estruturas simples.');
insert into GeneroMusical (nome, descricao) values('Hip-Hop', 'Um estilo musical e movimento cultural que surgiu nos anos 70, com rimas faladas e batidas marcadas.');
insert into GeneroMusical (nome, descricao) values('Eletrônica', 'Música produzida com instrumentos eletrônicos, como sintetizadores e caixas de ritmos.');
insert into GeneroMusical (nome, descricao) values('Soul', 'Gênero que combina elementos do gospel, R&B e jazz, com ênfase em vocais emocionais.');
insert into GeneroMusical (nome, descricao) values('Progressivo', 'Subgênero do rock com composições complexas, instrumentação variada e experimentação sonora.');

-- Insere dados na tabela Musica
insert into musica (titulo, letra, anoLancamento, nota, duracaoSegundos, artista_id) values(
                                                                                               'Bohemian Rhapsody',
                                                                                               'Is this the real life? Is this just fantasy? Caught in a landslide, no escape from reality. Open your eyes, look up to the skies and see...',
                                                                                               1975, 9.8, 355, 2
                                                                                           );

insert into musica (titulo, letra, anoLancamento, nota, duracaoSegundos, artista_id) values(
                                                                                               'Crazy Little Thing Called Love',
                                                                                               'This thing called love, I just can''t handle it. This thing called love, I must get round to it. I ain''t ready, crazy little thing called love.',
                                                                                               1979, 8.5, 160, 2
                                                                                           );

insert into musica (titulo, letra, anoLancamento, nota, duracaoSegundos, artista_id) values(
                                                                                               'Single Ladies (Put a Ring on It)',
                                                                                               'All the single ladies, now put your hands up! Up in the club, we just broke up, I''m doing my own little thing. You decided to dip, I decided to be free...',
                                                                                               2008, 9.0, 201, 3
                                                                                           );

insert into musica (titulo, letra, anoLancamento, nota, duracaoSegundos, artista_id) values(
                                                                                               'Lovesong',
                                                                                               'Whenever I''m alone with you, you make me feel like I am home again. Whenever I''m alone with you, you make me feel like I am whole again...',
                                                                                               1989, 9.2, 210, 4
                                                                                           );

insert into musica (titulo, letra, anoLancamento, nota, duracaoSegundos, artista_id) values(
                                                                                               'Sussudio',
                                                                                               'There''s a girl that I know, a girl that I know, a girl that I know... Ooh, she''s a good-lookin'' girl, a good-lookin'' girl...',
                                                                                               1985, 7.5, 260, 5
                                                                                           );

-- Associações musica-gênero (Many-to-Many)
insert into musica_genero (musica_id, genero_musical_id) values (1, 1), (1, 2);
insert into musica_genero (musica_id, genero_musical_id) values (2, 1);
insert into musica_genero (musica_id, genero_musical_id) values (3, 2), (3, 3), (3, 5);
insert into musica_genero (musica_id, genero_musical_id) values (4, 1);
insert into musica_genero (musica_id, genero_musical_id) values (5, 2), (5, 6);