package com.nicosarr.jazzLibraryAPI.VideoContainsArtist;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
	
}
