package com.nicosarr.jazzLibraryAPI.AlbumContainsArtist;

import org.springframework.stereotype.Repository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class AlbumContainsArtistRep {

    @PersistenceContext
    private EntityManager entityManager;

    // Retrieve all associations
    public List<AlbumContainsArtistDTO> retrieveAll() {
        String jpql = "SELECT aca FROM AlbumContainsArtist aca";
        TypedQuery<AlbumContainsArtist> query = entityManager.createQuery(jpql, AlbumContainsArtist.class);
        List<AlbumContainsArtist> list = query.getResultList();
        return list.stream().map(AlbumContainsArtistDTO::fromEntity).collect(Collectors.toList());
    }

    //TODO// Save a new association

    //TODO// Find by composite key (Discogs IDs)

    //TODO// Delete by composite key

    //TODO// Check existence
}