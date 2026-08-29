	package com.nicosarr.jazzLibraryAPI.Artist;
	
	import java.util.ArrayList;
	import java.util.LinkedHashMap;
	import java.util.List;
	import java.util.Map;
	import java.util.stream.Collectors;

	import jakarta.persistence.PersistenceContext;
	import jakarta.persistence.Query;
	import jakarta.persistence.TypedQuery;
	import jakarta.persistence.EntityManager;

	import org.slf4j.Logger;                      // <-- Use SLF4J API
	import org.slf4j.LoggerFactory;              // <-- Needed for getLogger()
	import org.springframework.beans.factory.annotation.Autowired;
	import org.springframework.jdbc.core.JdbcTemplate;
	import org.springframework.stereotype.Repository;
	import org.springframework.transaction.annotation.Transactional;

	import com.nicosarr.jazzLibraryAPI.Video.Video;
	import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtist;
	import com.nicosarr.jazzLibraryAPI.util.JobContext;
	import com.nicosarr.jazzLibraryAPI.util.WikipediaService;
	
	@Repository
	public class ArtistRep {
	
		@PersistenceContext
		private EntityManager entityManager;
		@Autowired
		private JdbcTemplate jdbcTemplate;
	
		@Autowired
		private WikipediaService wikipediaService;

		private static final Logger logger = LoggerFactory.getLogger(ArtistRep.class);
		
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
	    
	    
	    @Transactional(timeout = 3600)
	    public String processAllArtistsWikipedia(JobContext jobContext) {
	        String jobId = (jobContext != null) ? jobContext.getJobId() : "unknown";

	        // 1. Get artists with missing data
	        String jpql = "SELECT a FROM Artist a WHERE a.wikipedia_url IS NULL OR a.wikidata_id IS NULL";
	        List<Artist> artists = entityManager.createQuery(jpql, Artist.class).getResultList();
	        int total = artists.size();
	        int updated = 0, skipped = 0, errors = 0;

	        logger.info("[Job {}] Starting Wikipedia/Wikidata fetch for {} artists.", jobId, total);

	        Map<Integer, String> changes = new LinkedHashMap<>();

	        for (int i = 0; i < artists.size(); i++) {
	            // Check cancellation
	            if (jobContext != null && jobContext.isCancelled()) {
	                logger.warn("[Job {}] Cancelled at artist {}/{}.", jobId, i, total);
	                break;
	            }

	            Artist artist = artists.get(i);
	            int artistId = artist.getArtist_id();
	            String fullName = (artist.getArtist_name() + " " + artist.getArtist_surname()).trim();

	            // If name is empty, skip
	            if (fullName.isEmpty()) {
	                skipped++;
	                continue;
	            }

	            try {
	            	Map<String, String> result = wikipediaService.fetchWikidataAndWikipedia(
	            		    artist.getMusicbrainz_uuid(),  // pass the UUID (may be null)
	            		    fullName
	            		);
	            	if (result != null) {
	                    boolean changed = false;
	                    String newWikiUrl = result.get("wikipediaUrl");
	                    String newWikidataId = result.get("wikidataId");

	                    if (newWikiUrl != null && (artist.getWikipedia_url() == null || !artist.getWikipedia_url().equals(newWikiUrl))) {
	                        artist.setWikipedia_url(newWikiUrl);
	                        changed = true;
	                    }
	                    if (newWikidataId != null && (artist.getWikidata_id() == null || !artist.getWikidata_id().equals(newWikidataId))) {
	                        artist.setWikidata_id(newWikidataId);
	                        changed = true;
	                    }

	                    if (changed) {
	                        entityManager.merge(artist);
	                        updated++;
	                        changes.put(artistId, String.format("Updated: wiki=%s, wikidata=%s", newWikiUrl, newWikidataId));
	                    } else {
	                        skipped++;
	                    }
	                } else {
	                    errors++;
	                }
	            } catch (Exception e) {
	                errors++;
	                logger.error("[Job {}] Error processing artist id {}: {}", jobId, artistId, e.getMessage());
	            }

	            // Progress logging
	            int processed = i + 1;
	            if (processed % 10 == 0 || processed == total) {
	                logger.info("[Job {}] Progress: {}/{} – Updated: {}, Skipped: {}, Errors: {}",
	                        jobId, processed, total, updated, skipped, errors);
	            }

	            // Delay to respect API limits (500 ms)
	            try {
	                Thread.sleep(500);
	            } catch (InterruptedException e) {
	                Thread.currentThread().interrupt();
	                if (jobContext != null && jobContext.isCancelled()) {
	                    logger.warn("[Job {}] Interrupted due to cancellation.", jobId);
	                    break;
	                }
	            }
	        }

	        // Build summary
	        StringBuilder summary = new StringBuilder();
	        summary.append(String.format("Processed %d artists. Updated: %d, Skipped: %d, Errors: %d",
	                total, updated, skipped, errors));

	        if (!changes.isEmpty()) {
	            summary.append("\nChanges:\n");
	            for (Map.Entry<Integer, String> entry : changes.entrySet()) {
	                summary.append("  Artist ID ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
	            }
	        }

	        logger.info("[Job {}] {}", jobId, summary);
	        return summary.toString();
	    }
	    
	    
	    
}
