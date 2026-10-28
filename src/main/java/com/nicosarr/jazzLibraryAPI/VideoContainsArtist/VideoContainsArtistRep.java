package com.nicosarr.jazzLibraryAPI.VideoContainsArtist;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nicosarr.jazzLibraryAPI.Video.Video;
import com.nicosarr.jazzLibraryAPI.Video.VideoDTO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

@Repository
public class VideoContainsArtistRep {


    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
 // Method to get all artists with videos as DTOs
    public List<VideoContainsArtistDTO> retrieveAll() {
        String jpql = "SELECT DISTINCT vca FROM VideoContainsArtist vca "+
                "ORDER BY vca.artist.artist_id";
        
        TypedQuery<VideoContainsArtist> query = entityManager.createQuery(jpql, VideoContainsArtist.class);
        List<VideoContainsArtist> videoContainsArtist = query.getResultList();
        
        // Convert entities to DTOs
        return videoContainsArtist.stream()
            .map(VideoContainsArtistDTO::fromEntity)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<VideoContainsArtistDTO> retrievePageAfterKey(
            long cursorVideoId, long cursorArtistId, int limit) {

        String jpql =
            "SELECT vca FROM VideoContainsArtist vca " +
            "WHERE vca.id.videoId > :cv " +
            "   OR (vca.id.videoId = :cv AND vca.id.artistId > :cart) " +
            "ORDER BY vca.id.videoId ASC, vca.id.artistId ASC";

        TypedQuery<VideoContainsArtist> q =
            entityManager.createQuery(jpql, VideoContainsArtist.class);
        q.setParameter("cv", (int) cursorVideoId);
        q.setParameter("cart", (int) cursorArtistId);
        q.setMaxResults(limit);
        return q.getResultList().stream()
                .map(VideoContainsArtistDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long count() {
        return entityManager.createQuery(
            "SELECT COUNT(vca) FROM VideoContainsArtist vca", Long.class)
            .getSingleResult();
    }
    
    
	
}
