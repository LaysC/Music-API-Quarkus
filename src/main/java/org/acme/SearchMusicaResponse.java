package org.acme;

import java.util.ArrayList;
import java.util.List;

public class SearchMusicaResponse {
    public List<Musica> Musicas = new ArrayList<>();
    public long TotalMusicas;
    public int TotalPages;
    public boolean HasMore;
    public String NextPage;
}