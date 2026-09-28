package com.nicosarr.jazzLibraryAPI.Album;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicosarr.jazzLibraryAPI.AlbumContainsArtist.AlbumContainsArtist;
import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.util.JobContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MusicbrainzEnrichmentService {

    private static final Logger logger = LoggerFactory.getLogger(MusicbrainzEnrichmentService.class);

    private static final int  BATCH             = 20;
    private static final long MB_MIN_GAP_MS     = 1100;
    private static final int  MIN_ACCEPT_SCORE  = 100;
    private static final int  BODY_PREVIEW_LEN  = 800;

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong lastCall = new AtomicLong(0);

    @PersistenceContext private EntityManager entityManager;
    private final TransactionTemplate tx;

    public MusicbrainzEnrichmentService(RestTemplate restTemplate, TransactionTemplate tx) {
        this.restTemplate = restTemplate;
        this.tx = tx;
    }

    // ===============================================================
    // ENTRY POINT
    // ===============================================================
    public String enrichAll(JobContext jobContext, Integer limit) {

        String jobId = (jobContext == null) ? "?" : jobContext.getJobId();
        long jobStart = System.currentTimeMillis();

        // -----------------------------------------------------------
        // Pre-count: how many albums are we actually going to look at?
        // -----------------------------------------------------------
        int pendingAtStart = countAlbumsMissingMbid();
        logger.info("[MB-JOB {}] === MusicBrainz ID job START — {} albums missing an MBID (limit={}) ===",
                    jobId, pendingAtStart, limit);

        if (pendingAtStart == 0) {
            String summary = "MusicBrainz ID job: nothing to do — every album already has an MBID.";
            logger.info("[MB-JOB {}] {}", jobId, summary);
            return summary;
        }

        AtomicInteger processed = new AtomicInteger();
        AtomicInteger updated   = new AtomicInteger();   // <-- "new MBIDs found"
        AtomicInteger noMatch   = new AtomicInteger();
        AtomicInteger errors    = new AtomicInteger();

        int offset  = 0;
        int batchNo = 0;

        while (true) {
            if (jobContext != null && jobContext.isCancelled()) {
                logger.info("[MB-JOB {}] Cancelled by user at offset {}", jobId, offset);
                break;
            }

            // Only albums whose musicbrainz_uuid is NULL or empty.
            List<Integer> ids = loadPendingIds(offset, BATCH);
            if (ids.isEmpty()) {
                logger.debug("[MB-JOB {}] No more pending albums at offset {}", jobId, offset);
                break;
            }
            batchNo++;

            List<Album> batch = loadAlbumsWithArtists(ids);
            logger.info("[MB-JOB {}] --- Batch #{} : {} albums loaded (of {} ids requested) ---",
                        jobId, batchNo, batch.size(), ids.size());

            int batchUpdated = 0;
            int batchNoMatch = 0;
            int batchErrors  = 0;

            long batchStart = System.currentTimeMillis();
            for (Album album : batch) {
                if (jobContext != null && jobContext.isCancelled()) break;

                int albumId = album.getAlbum_id();
                long albumStart = System.currentTimeMillis();

                try {
                    String mbid = lookupMbid(album, errors);

                    if (mbid == null) {
                        batchNoMatch++;
                        noMatch.incrementAndGet();
                        logger.debug("[MB-JOB {}] ✘ Album {} '{}' — no MBID match ({} ms)",
                                     jobId, albumId, album.getTitle(),
                                     System.currentTimeMillis() - albumStart);
                        continue;
                    }

                    if (persist(albumId, mbid)) {
                        batchUpdated++;
                        logger.info("[MB-JOB {}] ✔ NEW MBID for album {} '{}' → {}",
                                    jobId, albumId, album.getTitle(), mbid);
                    } else {
                        logger.debug("[MB-JOB {}] Persist skipped for album {} (already set)",
                                     jobId, albumId);
                    }
                } catch (Exception e) {
                    batchErrors++;
                    errors.incrementAndGet();
                    logger.error("[MB-JOB {}] Album {} ('{}') lookup threw: {}",
                                 jobId, albumId, album.getTitle(), e.getMessage(), e);
                }
                processed.incrementAndGet();
            }

            updated.addAndGet(batchUpdated);
            logger.info("[MB-JOB {}] Batch #{} done in {} ms — new={}, noMatch={}, errors={}",
                        jobId, batchNo, System.currentTimeMillis() - batchStart,
                        batchUpdated, batchNoMatch, batchErrors);

            offset += BATCH;
            if (limit != null && offset >= limit) {
                logger.info("[MB-JOB {}] Reached limit {} — stopping", jobId, limit);
                break;
            }
        }

        // -----------------------------------------------------------
        // Final summary
        // -----------------------------------------------------------
        long elapsed = System.currentTimeMillis() - jobStart;
        double avgMs = processed.get() == 0 ? 0 : (double) elapsed / processed.get();

        String summary = String.format(
                "MusicBrainz ID job finished — %d new MBIDs found out of %d albums that were missing one. " +
                "Looked up=%d, noMatch=%d, errors=%d, elapsed=%d ms (%.0f ms/album).",
                updated.get(), pendingAtStart,
                processed.get(), noMatch.get(), errors.get(),
                elapsed, avgMs);

        logger.info("[MB-JOB {}] ===================================================", jobId);
        logger.info("[MB-JOB {}] {}", jobId, summary);
        logger.info("[MB-JOB {}] ===================================================", jobId);

        return summary;
    }

    // ===============================================================
    // Per-album MB lookup (unchanged from before, kept for reference)
    // ===============================================================
    private String lookupMbid(Album album, AtomicInteger errors) {

        int albumId = album.getAlbum_id();
        String title = album.getTitle();
        if (title == null || title.isBlank()) return null;

        String artistMbid = null;
        String artistName = null;

        for (AlbumContainsArtist aca : album.getAlbumContainsArtists()) {
            Artist ar = aca.getArtist();
            if (ar == null) continue;

            String mbid = trim(ar.getMusicbrainz_uuid());
            String name = fullNameOf(ar);

            if (artistMbid == null && mbid != null) artistMbid = mbid;
            if (artistName == null || aca.getIs_main() == 1) {
                if (name != null) artistName = name;
                if (aca.getIs_main() == 1) break;
            }
        }

        if (artistMbid == null && (artistName == null || artistName.isBlank())) {
            logger.debug("[MB-LOOKUP {}] skipped — no usable artist info", albumId);
            return null;
        }

        String query = (artistMbid != null)
                ? "arid:" + artistMbid + " AND releasegroup:\"" + escapeLucene(title) + "\""
                : "releasegroup:\"" + escapeLucene(title) + "\" AND artist:\"" + escapeLucene(artistName) + "\"";

        logger.debug("[MB-LOOKUP {}] query='{}'", albumId, query);

        JsonNode body = search(query);
        if (body == null) { errors.incrementAndGet(); return null; }

        JsonNode groups = body.get("release-groups");
        if (groups == null || !groups.isArray() || groups.isEmpty()) return null;

        int bestScore = -1;
        String bestMbid = null;

        for (JsonNode g : groups) {
            String id = g.path("id").asText(null);
            if (id == null) continue;
            int score = scoreCandidate(g, title, artistMbid, artistName);
            if (score > bestScore) { bestScore = score; bestMbid = id; }
        }

        if (bestMbid == null || bestScore < MIN_ACCEPT_SCORE) {
            logger.debug("[MB-LOOKUP {}] '{}' rejected — bestScore={} threshold={}",
                         albumId, title, bestScore, MIN_ACCEPT_SCORE);
            return null;
        }
        logger.debug("[MB-LOOKUP {}] '{}' → {} (score={})", albumId, title, bestMbid, bestScore);
        return bestMbid;
    }

    private int scoreCandidate(JsonNode g, String title, String artistMbid, String artistName) {
        int score = g.path("score").asInt(0);

        String gTitle = g.path("title").asText("");
        String nT = normalize(title), nG = normalize(gTitle);
        if (nG.equals(nT)) score += 40;
        else if (nG.contains(nT) || nT.contains(nG)) score += 15;

        JsonNode credits = g.get("artist-credit");
        if (credits != null && credits.isArray()) {
            for (JsonNode c : credits) {
                JsonNode ar = c.get("artist");
                if (ar == null) continue;
                String aid  = ar.path("id").asText(null);
                String anam = ar.path("name").asText("");
                if (artistMbid != null && artistMbid.equalsIgnoreCase(aid)) { score += 40; break; }
                if (artistName != null && !artistName.isBlank()
                        && normalize(anam).equals(normalize(artistName))) { score += 40; break; }
            }
        }

        String ptype = g.path("primary-type").asText("").toLowerCase(Locale.ROOT);
        if (ptype.equals("album"))  score += 10;
        if (ptype.equals("single")) score -= 25;
        if (ptype.equals("ep"))     score -= 25;

        JsonNode secTypes = g.get("secondary-types");
        if (secTypes != null && secTypes.isArray()) {
            for (JsonNode s : secTypes) {
                String sv = s.asText("").toLowerCase(Locale.ROOT);
                if (sv.contains("compilation") || sv.contains("live")) { score -= 15; break; }
            }
        }
        return score;
    }

    // ===============================================================
    // HTTP
    // ===============================================================
    private JsonNode search(String luceneQuery) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://musicbrainz.org/ws/2/release-group")
                .queryParam("query", luceneQuery)
                .queryParam("fmt",   "json")
                .queryParam("limit", 5)
                .build().encode(StandardCharsets.UTF_8).toUri();

        ResponseEntity<String> resp = callWithThrottle(uri);
        if (resp == null || resp.getBody() == null) return null;
        try {
            return mapper.readTree(resp.getBody());
        } catch (Exception e) {
            logger.warn("[MB-HTTP] Cannot parse response: {}", e.getMessage());
            return null;
        }
    }

    private ResponseEntity<String> callWithThrottle(URI uri) {
        long now  = System.currentTimeMillis();
        long wait = MB_MIN_GAP_MS - (now - lastCall.get());
        if (wait > 0) {
            try { Thread.sleep(wait); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
        }
        lastCall.set(System.currentTimeMillis());

        long[] backoff = { 2000, 5000, 15000 };
        for (int attempt = 0; attempt <= backoff.length; attempt++) {
            try {
                ResponseEntity<String> resp = restTemplate.getForEntity(uri, String.class);
                int status = resp.getStatusCode().value();
                if (status != 429 && status != 503) return resp;
                if (attempt == backoff.length) return resp;
                logger.warn("[MB-HTTP] {} (attempt {}), sleeping {} ms...",
                            status, attempt + 1, backoff[attempt]);
                Thread.sleep(backoff[attempt]);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                if (attempt == backoff.length) {
                    logger.warn("[MB-HTTP] Final attempt failed: {}", e.getMessage());
                    return null;
                }
                try { Thread.sleep(backoff[attempt]); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        return null;
    }

    // ===============================================================
    // Persistence
    // ===============================================================
    private boolean persist(int albumId, String mbid) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            Album a = entityManager.find(Album.class, albumId);
            if (a == null) return false;
            if (a.getMusicbrainz_uuid() != null && !a.getMusicbrainz_uuid().isBlank()) {
                return false;    // second-chance guard
            }
            a.setMusicbrainz_uuid(mbid);
            return true;
        }));
    }

    // ===============================================================
    // Loading
    // ===============================================================
    /** COUNT(*) of albums that still need an MBID. Used for the job's final summary. */
    private int countAlbumsMissingMbid() {
        Integer c = tx.execute(status ->
                entityManager.createQuery(
                    "SELECT COUNT(a) FROM Album a " +
                    "WHERE a.title IS NOT NULL AND a.title <> '' " +
                    "  AND (a.musicbrainz_uuid IS NULL OR a.musicbrainz_uuid = '')",
                    Long.class)
                .getSingleResult()
                .intValue()
        );
        return c == null ? 0 : c;
    }

    /** Only albums with no MBID yet. */
    private List<Integer> loadPendingIds(int offset, int size) {
        return tx.execute(status ->
                entityManager.createQuery(
                    "SELECT a.album_id FROM Album a " +
                    "WHERE a.title IS NOT NULL AND a.title <> '' " +
                    "  AND (a.musicbrainz_uuid IS NULL OR a.musicbrainz_uuid = '') " +
                    "ORDER BY a.album_id", Integer.class)
                .setFirstResult(offset)
                .setMaxResults(size)
                .getResultList()
        );
    }

    /**
     * Loads albums with their artists. No SELECT DISTINCT — SQL Server refuses
     * it over TEXT columns. LEFT JOIN FETCH fans out one row per junction row,
     * but Hibernate returns the *same* Album instance each time, so deduping
     * by album_id gives us one Album per album with its artist list attached.
     */
    private List<Album> loadAlbumsWithArtists(List<Integer> ids) {
        List<Album> raw = tx.execute(status ->
                entityManager.createQuery(
                    "SELECT a FROM Album a " +
                    "LEFT JOIN FETCH a.albumContainsArtists aca " +
                    "LEFT JOIN FETCH aca.artist " +
                    "WHERE a.album_id IN :ids", Album.class)
                .setParameter("ids", ids)
                .getResultList()
        );

        Map<Integer, Album> byId = new LinkedHashMap<>();
        for (Album a : raw) byId.putIfAbsent(a.getAlbum_id(), a);
        List<Album> deduped = new ArrayList<>(byId.values());

        logger.debug("[MB-LOAD] ids={} → raw rows={}, distinct albums={}",
                     ids.size(), raw.size(), deduped.size());
        return deduped;
    }

    // ===============================================================
    // Small helpers
    // ===============================================================
    private String fullNameOf(Artist ar) {
        String full = trim(ar.getArtist_fullname());
        if (full != null) return full;
        String n = trim(ar.getArtist_name());
        String s = trim(ar.getArtist_surname());
        if (n == null) return s;
        if (s == null) return n;
        return n + " " + s;
    }

    private static String trim(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String escapeLucene(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': case '"': case '+': case '-': case '!': case '(':
                case ')':  case '{': case '}': case '[': case ']': case '^':
                case '~':  case '*': case '?': case ':': case '/':
                    sb.append('\\').append(c);
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}