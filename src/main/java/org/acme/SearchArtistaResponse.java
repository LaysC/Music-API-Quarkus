package org.acme;

import java.util.ArrayList;
import java.util.List;

public class SearchArtistaResponse {
    public List<Artista> Artistas = new ArrayList<>();
    public long TotalArtistas;
    public int TotalPages;
    public boolean HasMore;
    public String NextPage;
}