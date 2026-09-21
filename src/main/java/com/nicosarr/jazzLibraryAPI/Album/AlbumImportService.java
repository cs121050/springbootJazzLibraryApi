package com.nicosarr.jazzLibraryAPI.Album;

import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.AlbumContainsArtist.AlbumContainsArtist;
import com.nicosarr.jazzLibraryAPI.AlbumContainsArtist.AlbumContainsArtistId;
import com.nicosarr.jazzLibraryAPI.util.JobContext;
import com.nicosarr.jazzLibraryAPI.util.AlbumWikipediaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AlbumImportService {

    private static final Logger logger = LoggerFactory.getLogger(AlbumImportService.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AlbumWikipediaService albumWikipediaService;

    /**
     * Process one artist's discography in its own transaction.
     * If an error occurs, only this artist's changes are rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processArtistAlbums(Artist artist, JobContext jobContext,
                                    AtomicInteger created, AtomicInteger skipped, AtomicInteger errors) {
        try {
            // 1. Fetch raw album data from Wikipedia
            List<AlbumWikipediaService.AlbumRawData> rawAlbums = albumWikipediaService.fetchDiscography(artist);
            if (rawAlbums.isEmpty()) {
                skipped.incrementAndGet();
                return;
            }

            // 2. Process each album
            for (AlbumWikipediaService.AlbumRawData raw : rawAlbums) {
                if (jobContext != null && jobContext.isCancelled()) {
                    break;
                }

             // Check if album already exists
                Album existing = findExistingAlbum(raw);
                if (existing != null) {
                    // Backfill fields that were missing when this row was first created
                    boolean changed = false;

                    if ((existing.getRaw_wikipedia_url() == null || existing.getRaw_wikipedia_url().isBlank())
                            && raw.getRawWikipediaUrl() != null && !raw.getRawWikipediaUrl().isBlank()) {
                        existing.setRaw_wikipedia_url(raw.getRawWikipediaUrl());
                        changed = true;
                    }

                    if ((existing.getWikidata_id() == null || existing.getWikidata_id().isBlank())
                            && raw.getWikidataId() != null && !raw.getWikidataId().isBlank()) {
                        existing.setWikidata_id(raw.getWikidataId());
                        changed = true;
                    }

                    if ((existing.getWikipedia_url() == null || existing.getWikipedia_url().isBlank())
                            && raw.getWikipediaUrl() != null && !raw.getWikipediaUrl().isBlank()) {
                        existing.setWikipedia_url(raw.getWikipediaUrl());
                        changed = true;
                    }

                    if (changed) {
                        entityManager.merge(existing);
                        logger.debug("Backfilled wiki fields on existing album '{}'", existing.getTitle());
                    }

                    linkArtistToAlbum(artist, existing, raw.isMain());
                    continue;
                }

                // Create and persist new album
                Album newAlbum = createAlbumFromRaw(raw);
                // Safety: truncate title if too long (DB column is 500)
                if (newAlbum.getTitle() != null && newAlbum.getTitle().length() > 500) {
                    newAlbum.setTitle(newAlbum.getTitle().substring(0, 500));
                }
                entityManager.persist(newAlbum);
                // No explicit flush – let Hibernate flush at commit
                linkArtistToAlbum(artist, newAlbum, raw.isMain());
                created.incrementAndGet();
            }

        } catch (Exception e) {
            // Log error but do NOT rethrow – transaction will roll back cleanly
            logger.error("Error processing artist {}: {}", artist.getArtist_id(), e.getMessage(), e);
            errors.incrementAndGet();
        }
    }

    // ----- Helper methods (moved from AlbumRep) -----

    private Album findExistingAlbum(AlbumWikipediaService.AlbumRawData raw) {
        // 1. Check by wikidata_id
        if (raw.getWikidataId() != null && !raw.getWikidataId().isBlank()) {
            String jpql = "SELECT a FROM Album a WHERE a.wikidata_id = :wikidataId";
            TypedQuery<Album> query = entityManager.createQuery(jpql, Album.class);
            query.setParameter("wikidataId", raw.getWikidataId());
            List<Album> results = query.getResultList();
            if (!results.isEmpty()) {
                return results.get(0);
            }
        }

        // 2. Check by title + year (only if both are present)
        if (raw.getTitle() != null && !raw.getTitle().isBlank()
                && raw.getYear() != null && !raw.getYear().isBlank()) {
            String jpql = "SELECT a FROM Album a WHERE a.title = :title AND a.year = :year";
            TypedQuery<Album> query = entityManager.createQuery(jpql, Album.class);
            query.setParameter("title", raw.getTitle());
            query.setParameter("year", Integer.parseInt(raw.getYear()));
            List<Album> results = query.getResultList();
            if (!results.isEmpty()) {
                return results.get(0);
            }
        }
        return null;
    }

    private Album createAlbumFromRaw(AlbumWikipediaService.AlbumRawData raw) {
    	
    	System.out.println(">>> PERSISTING: " + raw.getTitle() + " | " + raw.getWikipediaUrl());
    	logger.debug("Persisting '{}' url={}", raw.getTitle(), raw.getWikipediaUrl());
    	
        Album album = new Album();
        album.setTitle(raw.getTitle());
        if (raw.getYear() != null) {
            album.setYear(Integer.parseInt(raw.getYear()));
        }
        album.setReleased(raw.getReleased());
        album.setWikidata_id(raw.getWikidataId());
        album.setRelease_id(null); // will be filled later
        album.setWikipedia_url(raw.getWikipediaUrl());   
        album.setRaw_wikipedia_url(raw.getRawWikipediaUrl());
        album.setLabels(raw.getLabel());
        album.setRelease_type(raw.getReleaseType());
        return album;
    }

    private void linkArtistToAlbum(Artist artist, Album album, boolean isMain) {
        // Ensure artist is managed (re‑attach if detached)
        if (!entityManager.contains(artist)) {
            artist = entityManager.merge(artist);
        }

        AlbumContainsArtistId id = new AlbumContainsArtistId(artist.getArtist_id(), album.getAlbum_id());
        AlbumContainsArtist existing = entityManager.find(AlbumContainsArtist.class, id);
        if (existing != null) {
            // Update is_main if different
            if (existing.getIs_main() != (isMain ? 1 : 0)) {
                existing.setIs_main(isMain ? 1 : 0);
                entityManager.merge(existing);
            }
            return;
        }

        AlbumContainsArtist aca = new AlbumContainsArtist();
        aca.setId(id);
        aca.setArtist(artist);
        aca.setAlbum(album);
        aca.setIs_main(isMain ? 1 : 0);
        entityManager.persist(aca);
    }
}