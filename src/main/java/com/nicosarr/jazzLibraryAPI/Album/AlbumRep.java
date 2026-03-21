package com.nicosarr.jazzLibraryAPI.Album;

import org.springframework.stereotype.Repository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class AlbumRep {

    @PersistenceContext
    private EntityManager entityManager;

    // Retrieve all albums without artists
    public List<AlbumDTO> retrieveAll() {
        String jpql = "SELECT a FROM Album a Where a.releaseType like 'album' ORDER BY a.album_id";
        TypedQuery<Album> query = entityManager.createQuery(jpql, Album.class);
        List<Album> albums = query.getResultList();
        return albums.stream().map(AlbumDTO::fromEntity).collect(Collectors.toList());
    }

    // Retrieve all albums with their associated artists (fetch join)
    public List<AlbumWithArtistDTO> retrieveAllWithArtists() {
        String jpql = "SELECT DISTINCT a FROM Album a " +
                      "LEFT JOIN FETCH a.albumContainsArtists aca " +
                      "LEFT JOIN FETCH aca.artist " +
                      "Where a.releaseType like 'album'" +
                      "ORDER BY a.album_id";
        TypedQuery<Album> query = entityManager.createQuery(jpql, Album.class);
        List<Album> albums = query.getResultList();
        return albums.stream().map(AlbumWithArtistDTO::fromEntity).collect(Collectors.toList());
    }
}