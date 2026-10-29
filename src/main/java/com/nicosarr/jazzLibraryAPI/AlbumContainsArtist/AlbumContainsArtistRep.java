package com.nicosarr.jazzLibraryAPI.AlbumContainsArtist;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
    
    public List<AlbumContainsArtistDTO> retrieveAllAvailable() {
        String jpql = "SELECT aca FROM AlbumContainsArtist aca " +
                      "JOIN aca.artist a " +
                      "WHERE a.instrument.instrument_id <> 99";
        TypedQuery<AlbumContainsArtist> query = entityManager.createQuery(jpql, AlbumContainsArtist.class);
        List<AlbumContainsArtist> list = query.getResultList();
        return list.stream()
                   .map(AlbumContainsArtistDTO::fromEntity)
                   .collect(Collectors.toList());
    }
    
    
    @Transactional(readOnly = true)
    public List<AlbumContainsArtistDTO> retrievePageAfterKey(
            long cursorAlbumId, long cursorArtistId, int limit) {

        String jpql =
            "SELECT aca FROM AlbumContainsArtist aca " +
            "JOIN aca.artist a " +                                    // ← ADDED
            "WHERE a.instrument.instrument_id <> 99 " +               // ← ADDED
            "  AND (aca.id.albumId > :ca " +
            "       OR (aca.id.albumId = :ca AND aca.id.artistId > :cart)) " +
            "ORDER BY aca.id.albumId ASC, aca.id.artistId ASC";

        TypedQuery<AlbumContainsArtist> q =
            entityManager.createQuery(jpql, AlbumContainsArtist.class);
        q.setParameter("ca", (int) cursorAlbumId);
        q.setParameter("cart", (int) cursorArtistId);
        q.setMaxResults(limit);
        return q.getResultList().stream()
                .map(AlbumContainsArtistDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long count() {   // ← CHANGED: same filter as page
        return entityManager.createQuery(
                "SELECT COUNT(aca) FROM AlbumContainsArtist aca " +
                "JOIN aca.artist a " +
                "WHERE a.instrument.instrument_id <> 99",
                Long.class).getSingleResult();
    }
    

    //TODO// Save a new association

    //TODO// Find by composite key (Discogs IDs)

    //TODO// Delete by composite key

    //TODO// Check existence
}