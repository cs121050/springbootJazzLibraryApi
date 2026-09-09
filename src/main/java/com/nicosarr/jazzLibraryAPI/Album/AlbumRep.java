package com.nicosarr.jazzLibraryAPI.Album;

import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Artist.ArtistRep;
import com.nicosarr.jazzLibraryAPI.AlbumContainsArtist.AlbumContainsArtist;
import com.nicosarr.jazzLibraryAPI.AlbumContainsArtist.AlbumContainsArtistId;
import com.nicosarr.jazzLibraryAPI.util.JobContext;
import com.nicosarr.jazzLibraryAPI.util.AlbumWikipediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Repository
public class AlbumRep {

    private static final Logger logger = LoggerFactory.getLogger(AlbumRep.class);
	
    @PersistenceContext
    private EntityManager entityManager;
    
    @Autowired
    private ArtistRep artistRep; // to get all artists
    
    @Autowired
    private AlbumImportService albumImportService;  
    
    // Retrieve all albums without artists
    public List<AlbumDTO> retrieveAll() {
        String jpql = "SELECT a FROM Album a ORDER BY a.album_id";
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
    
   
    
    //@Transactional(timeout = 3600)   // outer transaction (main job)
    public String processAllAlbumsFromArtists(JobContext jobContext, Integer limit) {
        String jobId = (jobContext != null) ? jobContext.getJobId() : "unknown";

        // Get artists with Wikipedia or Wikidata info
        List<Artist> artists = artistRep.findArtistsWithWikipediaOrWikidata();
        if (limit != null && limit > 0 && limit < artists.size()) {
            artists = artists.subList(0, limit);
        }

        int total = artists.size();
        AtomicInteger created = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        logger.info("[Job {}] Starting album discography import for {} artists.", jobId, total);

        for (int i = 0; i < artists.size(); i++) {
            if (jobContext != null && jobContext.isCancelled()) {
                logger.warn("[Job {}] Cancelled at artist {}/{}.", jobId, i, total);
                break;
            }

            Artist artist = artists.get(i);
            try {
                // Each artist is processed in its own transaction
                albumImportService.processArtistAlbums(artist, jobContext, created, skipped, errors);                
            } catch (Exception e) {
                // This should never happen because processArtistAlbums catches everything,
                // but we keep it as a safety net.
                errors.incrementAndGet();
                logger.error("[Job {}] Unexpected error processing artist {}: {}", jobId, artist.getArtist_id(), e.getMessage(), e);
            }

            // Progress logging
            if ((i + 1) % 5 == 0 || (i + 1) == total) {
                logger.info("[Job {}] Progress: {}/{} – Created albums: {}, Skipped: {}, Errors: {}",
                        jobId, (i + 1), total, created.get(), skipped.get(), errors.get());
            }

            // Small delay between artists to be polite
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (jobContext != null && jobContext.isCancelled()) {
                    break;
                }
            }
        }

        String summary = String.format("Processed %d artists. Created %d new albums, Skipped %d, Errors %d.",
                total, created.get(), skipped.get(), errors.get());
        logger.info("[Job {}] {}", jobId, summary);
        return summary;
    }

    
    
}