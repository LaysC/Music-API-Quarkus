package org.acme;

import java.util.ArrayList;
import java.util.List;

public class SearchGeneroMusicalResponse {
    public List<GeneroMusical> GenerosMusicais = new ArrayList<>();
    public long TotalGenerosMusicais;
    public int TotalPages;
    public boolean HasMore;
    public String NextPage;
}