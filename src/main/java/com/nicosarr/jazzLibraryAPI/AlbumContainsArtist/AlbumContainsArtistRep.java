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

    // Save a new association
    public void save(AlbumContainsArtist aca) {
        entityManager.persist(aca);
    }

    // Find by composite key (Discogs IDs)
    public AlbumContainsArtist findByDiscogsIds(int discogsArtistId, int discogsReleaseId) {
        AlbumContainsArtistId id = new AlbumContainsArtistId(discogsArtistId, discogsReleaseId);
        return entityManager.find(AlbumContainsArtist.class, id);
    }

    // Delete by composite key
    public void deleteByDiscogsIds(int discogsArtistId, int discogsReleaseId) {
        AlbumContainsArtist aca = findByDiscogsIds(discogsArtistId, discogsReleaseId);
        if (aca != null) {
            entityManager.remove(aca);
        }
    }

    // Check existence
    public boolean existsByDiscogsIds(int discogsArtistId, int discogsReleaseId) {
        return findByDiscogsIds(discogsArtistId, discogsReleaseId) != null;
    }
}