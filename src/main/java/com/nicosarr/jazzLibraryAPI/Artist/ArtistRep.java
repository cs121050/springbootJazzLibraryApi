	package com.nicosarr.jazzLibraryAPI.Artist;
	
	import java.util.ArrayList;
	import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.PersistenceContext;
	import jakarta.persistence.Query;
	import jakarta.persistence.TypedQuery;
	import jakarta.persistence.EntityManager;
	
	import org.springframework.beans.factory.annotation.Autowired;
	import org.springframework.jdbc.core.JdbcTemplate;
	import org.springframework.stereotype.Repository;
	import org.springframework.transaction.annotation.Transactional;
	
	import com.nicosarr.jazzLibraryAPI.Video.Video;
	import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtist;
	
	@Repository
	public class ArtistRep {
	
		@PersistenceContext
		private EntityManager entityManager;
		@Autowired
		private JdbcTemplate jdbcTemplate;
	
	    // Method to get all artists with videos as DTOs
	    public List<ArtistWithVideoDTO> retrieveAllWithVideos() {
	        String jpql = "SELECT a FROM Artist a " +
	                      "LEFT JOIN FETCH a.videoContainsArtists vca " +
	                      "LEFT JOIN FETCH vca.video " +
	                      "ORDER BY a.artist_id";
	        
	        TypedQuery<Artist> query = entityManager.createQuery(jpql, Artist.class);
	        List<Artist> artists = query.getResultList();
	        
	        // Convert entities to DTOs
	        return artists.stream()
	            .map(ArtistWithVideoDTO::fromEntity)
	            .collect(Collectors.toList());
	    }
	    
	    public List<ArtistDTO> retrieveAll() {
	        String jpql = "SELECT a FROM Artist a " +
	                      "ORDER BY a.artist_id";
	        
	        TypedQuery<Artist> query = entityManager.createQuery(jpql, Artist.class);
	        List<Artist> artists = query.getResultList();
	        
	        // Convert entities to DTOs
	        return artists.stream()
	            .map(ArtistDTO::fromEntity)
	            .collect(Collectors.toList());
	    }
	    
	    /**
	     * Returns all artists that have a non‑null and non‑empty musicbrainz_uuid.
	     */
	    public List<Artist> findArtistsWithMusicbrainzUuid() {
	        String jpql = "SELECT a FROM Artist a " +
	                      "WHERE a.musicbrainz_uuid IS NOT NULL " +
	                      "AND a.musicbrainz_uuid <> ''";
	        TypedQuery<Artist> query = entityManager.createQuery(jpql, Artist.class);
	        return query.getResultList();
	    }
	    
}
