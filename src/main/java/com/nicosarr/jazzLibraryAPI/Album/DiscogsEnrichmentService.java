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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DiscogsEnrichmentService {

    private static final Logger logger = LoggerFactory.getLogger(DiscogsEnrichmentService.class);

    private static final int  BATCH              = 10;      // albums per DB round-trip
    private static final long DISCOGS_MIN_GAP_MS = 1100;    // 60/min authenticated

    /** Hard cap on Discogs text-search HTTP calls per album (master + release combined). */
    private static final int MAX_SEARCH_CALLS_PER_ALBUM = 8;

    /** Minimum score for a candidate returned by /database/search to be accepted. */
    private static final double MIN_MATCH_SCORE = 0.45;

    /** Per-page count for /database/search. */
    private static final int SEARCH_PER_PAGE = 5;

    /** Stop-words ignored when computing token similarity. */
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "of", "in", "on", "at", "to", "for", "with", "by"
    );

    /** Cap for the no-match id list in the summary line. */
    private static final int NO_MATCH_LOG_CAP = 200;

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    private final AtomicLong lastDiscogsCall = new AtomicLong(0);

 // ---------------- Google fallback ----------------

    /** Toggle for the Google site:discogs.com fallback. */
    @Value("${discogs.googleFallback.enabled:true}")
    private boolean googleFallbackEnabled;

    /** Realistic UA — Google returns a JS-only page (or 429) for the default Java UA. */
    @Value("${discogs.googleFallback.userAgent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
         + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36}")
    private String googleUserAgent;

    /** Minimum gap between Google calls — Google blocks fast scrapers. */
    private static final long GOOGLE_MIN_GAP_MS = 5000;

    /** Max Google HTTP calls per album. */
    private static final int MAX_GOOGLE_CALLS_PER_ALBUM = 2;

    /** Discogs release/master URL extractor (applied AFTER normalising `\/` and `%2F`). */
    private static final Pattern DISCOGS_URL_PATTERN = Pattern.compile(
            "discogs\\.com/(release|master)/(\\d+)", Pattern.CASE_INSENSITIVE);

    private final AtomicLong lastGoogleCall = new AtomicLong(0);
    
    @Value("${discogs.token:}")
    private String discogsToken;

    @PersistenceContext private EntityManager entityManager;
    private final TransactionTemplate tx;

    public DiscogsEnrichmentService(RestTemplate restTemplate, TransactionTemplate tx) {
        this.restTemplate = restTemplate;
        this.tx = tx;
        logger.debug("[DG-INIT] DiscogsEnrichmentService constructed. BATCH={}, discogsGap={}ms, maxSearchCalls/album={}",
                     BATCH, DISCOGS_MIN_GAP_MS, MAX_SEARCH_CALLS_PER_ALBUM);
    }

    // ===============================================================
    // ENTRY POINT
    // ===============================================================
    public String enrichAll(JobContext jobContext, Integer limit) {

        String jobId = (jobContext == null) ? "?" : jobContext.getJobId();
        long jobStart = System.currentTimeMillis();

        boolean authenticated = discogsToken != null && !discogsToken.isBlank();
        if (!authenticated) {
            logger.warn("[DG-JOB {}] No discogs.token set — running UNAUTHENTICATED (25 req/min). "
                      + "Set 'discogs.token' in application.properties for 60 req/min.", jobId);
        } else {
            logger.debug("[DG-JOB {}] Discogs token present ({} chars)", jobId, discogsToken.length());
        }

        int pendingAtStart = countPending();
        logger.info("[DG-JOB {}] === Discogs enrichment START — {} albums missing master_id or release_id (limit={}) ===",
                    jobId, pendingAtStart, limit);

        if (pendingAtStart == 0) {
            String done = "Discogs enrichment: nothing to do — every album already has both master_id and release_id.";
            logger.info("[DG-JOB {}] {}", jobId, done);
            return done;
        }

        AtomicInteger processed     = new AtomicInteger();
        AtomicInteger masterFilled  = new AtomicInteger();
        AtomicInteger releaseFilled = new AtomicInteger();
        AtomicInteger alreadyHad    = new AtomicInteger();   // <-- new
        AtomicInteger noMatch       = new AtomicInteger();
        AtomicInteger errors        = new AtomicInteger();

        List<Integer> noMatchIds = new ArrayList<>();

        Integer lastProcessedId = null;
        int batchNo = 0;

        while (true) {
            if (jobContext != null && jobContext.isCancelled()) {
                logger.info("[DG-JOB {}] Cancelled after album_id={}", jobId, lastProcessedId);
                break;
            }

            List<Integer> ids = loadPendingIdsAfterId(lastProcessedId, BATCH);
            if (ids.isEmpty()) {
                logger.debug("[DG-JOB {}] No more pending albums after album_id={}", jobId, lastProcessedId);
                break;
            }
            batchNo++;

            List<Album> batch = loadAlbumsWithArtists(ids);
            logger.info("[DG-JOB {}] --- Batch #{} : {} albums loaded (of {} ids requested) ---",
                        jobId, batchNo, batch.size(), ids.size());

            long batchStart = System.currentTimeMillis();
            int bMaster = 0, bRelease = 0, bAlreadyHad = 0, bNoMatch = 0, bErrors = 0;
            
            for (Album album : batch) {
                if (jobContext != null && jobContext.isCancelled()) break;

                int albumId = album.getAlbum_id();
                long albumStart = System.currentTimeMillis();

                try {
                    Outcome o = enrichOne(album);

                    if (o == Outcome.NO_MATCH) {
                        bNoMatch++;
                        noMatch.incrementAndGet();
                        noMatchIds.add(albumId);
                        logger.debug("[DG-JOB {}] ✘ Album {} '{}' — no match ({} ms)",
                                     jobId, albumId, album.getTitle(),
                                     System.currentTimeMillis() - albumStart);

                    } else if (o == Outcome.ALREADY_HAD_IT) {
                        bAlreadyHad++;
                        alreadyHad.incrementAndGet();
                        logger.debug("[DG-JOB {}] = Album {} '{}' — Discogs matched but album already had these ids",
                                     jobId, albumId, album.getTitle());

                    } else if (o == Outcome.NOTHING_TO_DO) {
                        // no counter

                    } else {
                        if (o.masterFilled)  { bMaster++;  masterFilled.incrementAndGet(); }
                        if (o.releaseFilled) { bRelease++; releaseFilled.incrementAndGet(); }
                        logger.info("[DG-JOB {}] ✔ Album {} '{}' — {} ({} ms)",
                                    jobId, albumId, album.getTitle(), o,
                                    System.currentTimeMillis() - albumStart);
                    }
                } catch (Exception e) {
                    bErrors++;
                    errors.incrementAndGet();
                    logger.error("[DG-JOB {}] Album {} ('{}') threw: {}",
                                 jobId, albumId, album.getTitle(), e.getMessage(), e);
                }
                processed.incrementAndGet();
            }

            // Advance the keyset cursor to the largest id we just looked at.
            Integer maxId = ids.get(ids.size() - 1);
            lastProcessedId = maxId;

            logger.info("[DG-JOB {}] Batch #{} done in {} ms — masterIds+={}, releaseIds+={}, alreadyHad={}, noMatch={}, errors={} (cursor={})",
                    jobId, batchNo, System.currentTimeMillis() - batchStart,
                    bMaster, bRelease, bAlreadyHad, bNoMatch, bErrors, lastProcessedId);

            if (limit != null && processed.get() >= limit) {
                logger.info("[DG-JOB {}] Reached limit {} — stopping", jobId, limit);
                break;
            }
        }

        long elapsed = System.currentTimeMillis() - jobStart;
        double avgMs = processed.get() == 0 ? 0 : (double) elapsed / processed.get();

        String summary = String.format(
                "Discogs enrichment finished — %d master_ids and %d release_ids filled "
              + "out of %d albums pending; looked up=%d, alreadyHad=%d, noMatch=%d, errors=%d, "
              + "elapsed=%d ms (%.0f ms/album).",
                masterFilled.get(), releaseFilled.get(), pendingAtStart,
                processed.get(), alreadyHad.get(), noMatch.get(), errors.get(),
                elapsed, avgMs);

        String noMatchLine = formatIdList(noMatchIds, NO_MATCH_LOG_CAP);

        logger.info("[DG-JOB {}] ===================================================", jobId);
        logger.info("[DG-JOB {}] {}", jobId, summary);
        if (noMatchIds.isEmpty()) {
            logger.info("[DG-JOB {}] Albums with NO match: none — every album was resolved.", jobId);
        } else {
            logger.info("[DG-JOB {}] Albums with NO match ({} total): {}",
                        jobId, noMatchIds.size(), noMatchLine);
            logger.debug("[DG-JOB {}] Full noMatch id list: {}", jobId, noMatchIds);
        }
        logger.info("[DG-JOB {}] ===================================================", jobId);

        return noMatchIds.isEmpty()
                ? summary
                : summary + " No-match album ids: " + noMatchLine;
    }

    // ===============================================================
    // Per-album logic
    //
    // Path 1 — existing master_id → fetch /masters/{id}.main_release
    // Path 3 — /database/search by title+artist (with variants), then link
    //          whichever side we found to the other side.
    // (Path 2 — MusicBrainz url-rels — has been removed.)
    // ===============================================================
    private Outcome enrichOne(Album album) {

        int albumId = album.getAlbum_id();
        Integer haveMaster  = normalizeId(album.getMaster_id());
        Integer haveRelease = normalizeId(album.getRelease_id());

        logger.info("[DG-ONE {}] '{}' | master={} release={} artist='{}'",
                albumId, album.getTitle(), haveMaster, haveRelease, pickArtistName(album));

        if (haveMaster != null && haveRelease != null) {
            logger.debug("[DG-ONE {}] both IDs already present — skipping", albumId);
            return Outcome.NOTHING_TO_DO;
        }

        // -------- Path 1: use existing master to fill release_id --------
        if (haveMaster != null && haveRelease == null) {
            Integer mainRelease = fetchDiscogsMainRelease(haveMaster);
            if (mainRelease != null) {
                logger.debug("[DG-PATH1 {}] master={} → main_release={}", albumId, haveMaster, mainRelease);
                return writeResult(albumId, null, mainRelease);
            }
            logger.debug("[DG-PATH1 {}] master={} has no main_release — falling through to search",
                         albumId, haveMaster);
        }

        // -------- Path 3: title + artist search --------
        List<String> titleVariants  = buildTitleVariants(album.getTitle());
        List<String> artistVariants = buildArtistVariants(album);

        logger.debug("[DG-ONE {}] search plan: {} title variants × {} artist variants",
                albumId, titleVariants.size(), artistVariants.size());

        if (titleVariants.isEmpty() || artistVariants.isEmpty()) {
            logger.debug("[DG-ONE {}] no usable title/artist — NO_MATCH", albumId);
            return Outcome.NO_MATCH;
        }

        MatchHit hit = searchByTitleAndArtist(album, titleVariants, artistVariants);

        if (hit == null) {
            logger.info("[DG-ONE {}] title+artist search exhausted — falling back to TITLE-ONLY search",
                        albumId);
            hit = searchByTitleOnly(album, titleVariants);
        }

        if (hit == null) {
            logger.info("[DG-ONE {}] title-only search exhausted — falling back to GOOGLE",
                        albumId);
            hit = searchByGoogleFallback(album, titleVariants, artistVariants);
        }

        if (hit == null) {
            logger.debug("[DG-ONE {}] no acceptable search hit — NO_MATCH", albumId);
            return Outcome.NO_MATCH;
        }
        
        Integer foundMaster  = hit.masterId;
        Integer foundRelease = hit.releaseId;

        // Try to fill the other side of the pair
        if (foundMaster != null && foundRelease == null && haveRelease == null) {
            Integer mainRel = fetchDiscogsMainRelease(foundMaster);
            if (mainRel != null) foundRelease = mainRel;
        }
        if (foundRelease != null && foundMaster == null && haveMaster == null) {
            Integer masterOfRelease = fetchDiscogsMasterOfRelease(foundRelease);
            if (masterOfRelease != null) foundMaster = masterOfRelease;
        }

        return writeResult(albumId, foundMaster, foundRelease);
    }

    // ===============================================================
    // Title / artist variant generation
    // ===============================================================

    /**
     * Build a small ordered list of candidate titles from a raw DB title.
     * The first element is always the raw title. Subsequent variants strip
     * common patterns that make Discogs' strict release_title filter miss:
     *   - trailing " (…)" / " […]" suffixes  (e.g. "(Impulse!, 1978)", "[Live]", "(album)")
     *   - " with X and Y" tails
     *   - "Artist / Artist – Real Title" patterns (keep the part after the last em/en-dash)
     *   - diacritics and curly punctuation normalized to ASCII
     * The list is capped at 4 to keep the search bounded.
     */
    private List<String> buildTitleVariants(String raw) {
        if (raw == null) return List.of();
        String t = raw.trim();
        if (t.isEmpty()) return List.of();

        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(t);

        // 1) trailing parenthesised / bracketed suffix
        String noTail = t.replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]\\s*$", "").trim();
        if (!noTail.isEmpty() && !out.contains(noTail)) out.add(noTail);

        // 2) "with X" tail
        String noWith = t.replaceFirst("(?i)\\s+with\\s+.+$", "").trim();
        if (!noWith.isEmpty() && !out.contains(noWith)) out.add(noWith);

        // 3) "A / B / C – Real Title" → "Real Title"
        int idx = t.lastIndexOf(" – ");
        if (idx < 0) idx = t.lastIndexOf(" — ");
        if (idx > 0) {
            String after = t.substring(idx + 3).trim();
            if (!after.isEmpty() && !out.contains(after)) out.add(after);
        }

        // 4) ASCII-normalized raw
        String ascii = toAscii(t);
        if (!ascii.isEmpty() && !out.contains(ascii)) out.add(ascii);

        // 5) aggressive clean: remove ALL parens/brackets + with-tail + ASCII
        String cleaned = t
                .replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", " ")
                .replaceFirst("(?i)\\s+with\\s+.+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        cleaned = toAscii(cleaned);
        if (!cleaned.isEmpty() && !out.contains(cleaned)) out.add(cleaned);

        List<String> asList = new ArrayList<>(out);
        if (asList.size() > 4) asList = asList.subList(0, 4);
        return asList;
    }

    /**
     * Order: main artist first, then up to two collaborators.
     */
    private List<String> buildArtistVariants(Album album) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        List<String> collaborators = new ArrayList<>();
        for (AlbumContainsArtist aca : album.getAlbumContainsArtists()) {
            Artist ar = aca.getArtist();
            if (ar == null) continue;
            String n = fullNameOf(ar);
            if (n == null) continue;
            if (aca.getIs_main() == 1) {
                out.add(n);
            } else {
                collaborators.add(n);
            }
        }
        out.addAll(collaborators);
        List<String> asList = new ArrayList<>(out);
        if (asList.size() > 3) asList = asList.subList(0, 3);
        return asList;
    }

    private String toAscii(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.replace('–', '-').replace('—', '-');
        n = n.replace('’', '\'').replace('‘', '\'');
        n = n.replace('“', '"').replace('”', '"');
        return n.replaceAll("\\s+", " ").trim();
    }

    // ===============================================================
    // Search
    // ===============================================================

    /**
     * Iterate (title, artist) pairs. For each pair try master then release.
     * Stop as soon as a candidate passes scoring. Capped at
     * MAX_SEARCH_CALLS_PER_ALBUM total HTTP calls.
     */
    private MatchHit searchByTitleAndArtist(Album album, List<String> titles, List<String> artists) {
        int calls = 0;
        for (String title : titles) {
            for (String artist : artists) {
                if (calls + 2 > MAX_SEARCH_CALLS_PER_ALBUM) return null;

                // master
                calls++;
                JsonNode masterHit = trySearch(album, title, artist, "master");
                if (masterHit != null) {
                    int id = masterHit.path("id").asInt(0);
                    if (id > 0) {
                        logger.info("[DG-SEARCH] ✔ master hit id={} title='{}'",
                                    id, masterHit.path("title").asText(""));
                        return new MatchHit(id, null);
                    }
                }

                // release
                calls++;
                JsonNode releaseHit = trySearch(album, title, artist, "release");
                if (releaseHit != null) {
                    int id = releaseHit.path("id").asInt(0);
                    int masterId = releaseHit.path("master_id").asInt(0);
                    if (id > 0) {
                        logger.info("[DG-SEARCH] ✔ release hit id={} master_id={} title='{}'",
                                    id, masterId, releaseHit.path("title").asText(""));
                        return new MatchHit(masterId > 0 ? masterId : null, id);
                    }
                }
            }
        }
        return null;
    }

    private JsonNode trySearch(Album album, String title, String artist, String type) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://api.discogs.com/database/search")
                .queryParam("release_title", title)
                .queryParam("artist",        artist)
                .queryParam("type",          type)
                .queryParam("per_page",      SEARCH_PER_PAGE)
                .queryParam("token",         discogsToken)
                .build().encode(StandardCharsets.UTF_8).toUri();

        logger.info("[DG-SEARCH] type={} title='{}' artist='{}' → {}", type, title, artist, sanitizeUrl(uri));

        JsonNode body = discogsGet(uri);
        if (body == null) {
            logger.info("[DG-SEARCH] type={} — null body", type);
            return null;
        }
        JsonNode results = body.get("results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            logger.info("[DG-SEARCH] type={} — 0 results", type);
            return null;
        }

        logger.info("[DG-SEARCH] type={} — {} candidates", type, results.size());
        int i = 0;
        for (JsonNode r : results) {
            i++;
            logger.info("[DG-SEARCH]   #{}. id={} master_id={} title='{}' year={} country={} format={}",
                        i,
                        r.path("id").asInt(0),
                        r.path("master_id").asInt(0),
                        r.path("title").asText(""),
                        r.path("year").asText(""),
                        r.path("country").asText(""),
                        r.path("format").isArray() ? joinArray(r.get("format")) : r.path("format").asText(""));
        }

        return pickBestResult(album, title, artist, results);
    }

    private JsonNode pickBestResult(Album album, String usedTitle, String usedArtist, JsonNode results) {
        JsonNode best = null;
        double bestScore = 0;
        for (JsonNode r : results) {
            double score = scoreCandidate(album, usedTitle, usedArtist, r);
            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }
        if (best == null) return null;
        if (bestScore < MIN_MATCH_SCORE) {
            logger.info("[DG-SEARCH] best candidate scored {} (<{}) — rejecting",
                        String.format("%.2f", bestScore), MIN_MATCH_SCORE);
            return null;
        }
        logger.info("[DG-SEARCH] accepting candidate with score {}", String.format("%.2f", bestScore));
        return best;
    }

    /**
     * 0.0 … 1.0 score for a Discogs search result against the album + query strings.
     *  - title  : token Jaccard, boosted to ≥0.85 if one contains the other
     *  - artist : 1.0 if the used artist appears in candidate title, 0.85 if any other
     *             credited artist appears, else 0.3
     *  - year   : 1.0 exact / 0.8 ±2 / 0.5 ±5 / 0.1 otherwise
     *  - format : −0.15 each for "compilation", "single", "45 rpm"
     */
    private double scoreCandidate(Album album, String usedTitle, String usedArtist, JsonNode candidate) {
        String candTitle = candidate.path("title").asText("").toLowerCase();
        String usedTitleLower = usedTitle.toLowerCase();

        double titleScore = jaccard(usedTitleLower, candTitle);
        if (candTitle.contains(usedTitleLower) || usedTitleLower.contains(candTitle)) {
            titleScore = Math.max(titleScore, 0.85);
        }

        // --- ARTIST: 1.0 only if the queried artist matches; 0.85 if any credited
        //     artist appears; 0.3 otherwise. When usedArtist == null (title-only
        //     fallback) the first branch is skipped and we cap at 0.85. ---
        double artistScore = 0.3;
        boolean specificArtistMatches =
                usedArtist != null && candTitle.contains(usedArtist.toLowerCase());

        if (specificArtistMatches) {
            artistScore = 1.0;
        } else {
            for (AlbumContainsArtist aca : album.getAlbumContainsArtists()) {
                Artist ar = aca.getArtist();
                if (ar == null) continue;
                String n = fullNameOf(ar);
                if (n != null && candTitle.contains(n.toLowerCase())) {
                    artistScore = Math.max(artistScore, 0.85);
                }
            }
        }

        double yearScore = 0.5;
        String candYear = candidate.path("year").asText("");
        Integer albumYear = album.getYear();
        if (albumYear != null && !candYear.isEmpty()) {
            try {
                int cy = Integer.parseInt(candYear);
                int diff = Math.abs(cy - albumYear);
                if (diff == 0)      yearScore = 1.0;
                else if (diff <= 2) yearScore = 0.8;
                else if (diff <= 5) yearScore = 0.5;
                else                yearScore = 0.1;
            } catch (NumberFormatException ignored) { /* keep default 0.5 */ }
        }

        double formatPenalty = 0;
        JsonNode fmt = candidate.get("format");
        if (fmt != null) {
            String fmtStr = (fmt.isArray() ? joinArray(fmt) : fmt.asText("")).toLowerCase();
            if (fmtStr.contains("compilation")) formatPenalty += 0.15;
            if (fmtStr.contains("single"))      formatPenalty += 0.15;
            if (fmtStr.contains("45 rpm"))      formatPenalty += 0.15;
        }

        double score = titleScore * 0.55 + artistScore * 0.30 + yearScore * 0.15 - formatPenalty;
        return Math.max(0, score);
    }

    private static double jaccard(String a, String b) {
        Set<String> at = tokens(a);
        Set<String> bt = tokens(b);
        if (at.isEmpty() || bt.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(at);
        inter.retainAll(bt);
        Set<String> union = new HashSet<>(at);
        union.addAll(bt);
        return (double) inter.size() / union.size();
    }

    private static Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        for (String t : s.toLowerCase().split("[^a-z0-9]+")) {
            if (t.length() >= 2 && !STOP_WORDS.contains(t)) out.add(t);
        }
        return out;
    }

    private static String joinArray(JsonNode arr) {
        if (arr == null || !arr.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode n : arr) {
            if (sb.length() > 0) sb.append('|');
            sb.append(n.asText(""));
        }
        return sb.toString();
    }

    // ===============================================================
    // Discogs HTTP
    // ===============================================================

    /** GET /masters/{id} → main_release (int > 0), or null. */
    private Integer fetchDiscogsMainRelease(int masterId) {
        if (masterId <= 0) return null;
        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://api.discogs.com/masters/" + masterId)
                .queryParam("token", discogsToken)
                .build().encode(StandardCharsets.UTF_8).toUri();

        JsonNode body = discogsGet(uri);
        if (body == null) return null;
        JsonNode mr = body.get("main_release");
        return (mr != null && mr.isInt() && mr.asInt() > 0) ? mr.asInt() : null;
    }

    /** GET /releases/{id} → master_id (int > 0), or null. */
    private Integer fetchDiscogsMasterOfRelease(int releaseId) {
        if (releaseId <= 0) return null;
        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://api.discogs.com/releases/" + releaseId)
                .queryParam("token", discogsToken)
                .build().encode(StandardCharsets.UTF_8).toUri();

        JsonNode body = discogsGet(uri);
        if (body == null) return null;
        JsonNode mid = body.get("master_id");
        return (mid != null && mid.isInt() && mid.asInt() > 0) ? mid.asInt() : null;
    }

    /** GET with throttle + retry on 429/503. Returns parsed JSON, or null. */
    private JsonNode discogsGet(URI uri) {
        ResponseEntity<String> resp = callThrottled(uri, lastDiscogsCall, DISCOGS_MIN_GAP_MS, "DG");
        if (resp == null || resp.getBody() == null) return null;
        try {
            return mapper.readTree(resp.getBody());
        } catch (Exception e) {
            logger.warn("[DG-HTTP] parse failed: {}", e.getMessage());
            return null;
        }
    }

    // ===============================================================
    // Generic throttled HTTP call
    // ===============================================================
    private static final int BODY_PREVIEW = 1500;

    private ResponseEntity<String> callThrottled(URI uri, AtomicLong lastCall, long minGap, String tag) {
        long now  = System.currentTimeMillis();
        long wait = minGap - (now - lastCall.get());
        if (wait > 0) {
            logger.debug("[{}-THROTTLE] sleeping {} ms", tag, wait);
            try { Thread.sleep(wait); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
        }
        lastCall.set(System.currentTimeMillis());

        long[] backoff = { 2000, 5000, 15000 };
        for (int attempt = 0; attempt <= backoff.length; attempt++) {
            long t0 = System.currentTimeMillis();
            try {
                logger.info("[{}-HTTP] attempt {} → GET {}", tag, attempt + 1, sanitizeUrl(uri));
                ResponseEntity<String> resp = restTemplate.getForEntity(uri, String.class);
                int status = resp.getStatusCode().value();
                String body = resp.getBody();
                logger.info("[{}-HTTP] attempt {} ← {} ({} ms, {} bytes)",
                            tag, attempt + 1, status,
                            System.currentTimeMillis() - t0,
                            body == null ? 0 : body.length());
                if (logger.isDebugEnabled() && body != null) {
                    logger.debug("[{}-HTTP] body: {}", tag, truncate(body, BODY_PREVIEW));
                }
                return resp;
            }
            catch (org.springframework.web.client.HttpStatusCodeException hsce) {
                int status = hsce.getStatusCode().value();
                String body = hsce.getResponseBodyAsString();
                logger.info("[{}-HTTP] attempt {} ← {} ({} ms) body={}",
                            tag, attempt + 1, status,
                            System.currentTimeMillis() - t0,
                            truncate(body, 500));
 
                if (status != 429 && status < 500) {
                    logger.debug("[{}-HTTP] status {} is terminal — not retrying", tag, status);
                    return null;
                }
                if (attempt == backoff.length) {
                    logger.warn("[{}-HTTP] giving up after {} attempts (status={})",
                                tag, attempt + 1, status);
                    return null;
                }
                logger.warn("[{}-HTTP] {} — sleeping {} ms", tag, status, backoff[attempt]);
                try { Thread.sleep(backoff[attempt]); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
            }
            catch (Exception e) {
                logger.warn("[{}-HTTP] attempt {} threw {}: {}",
                            tag, attempt + 1, e.getClass().getSimpleName(), e.getMessage());
                if (attempt == backoff.length) return null;
                try { Thread.sleep(backoff[attempt]); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...(+" + (s.length() - max) + ")";
    }

    /** Replace ?token=... with token=*** in log output. */
    private static String sanitizeUrl(URI uri) {
        String s = uri.toString();
        int idx = s.indexOf("token=");
        if (idx < 0) return s;
        int end = s.indexOf('&', idx);
        if (end < 0) end = s.length();
        return s.substring(0, idx) + "token=***" + s.substring(end);
    }

    // ===============================================================
    // Persistence — non-overwriting, treats 0 as NULL
    // NOTE: release_id uniqueness guard has been intentionally removed.
    //       Duplicates are allowed; the operator will deduplicate later.
    // ===============================================================
    private Outcome writeResult(int albumId, Integer masterId, Integer releaseId) {

        boolean wantMaster  = masterId  != null && masterId  > 0;
        boolean wantRelease = releaseId != null && releaseId > 0;

        if (!wantMaster && !wantRelease) return Outcome.NO_MATCH;

        boolean m = false;
        boolean r = false;

        // ------------------------------------------------------------
        // 1) MASTER — its own transaction. No unique index on master_id,
        //    so this write never gets rolled back by a release collision.
        // ------------------------------------------------------------
        if (wantMaster) {
            Boolean wrote = tx.execute(status -> {
                Album a = entityManager.find(Album.class, albumId);
                if (a == null) return false;

                Integer existing = a.getMaster_id();
                if (existing != null && existing != 0) return false;

                a.setMaster_id(masterId);
                logger.debug("[DG-PERSIST] album {} ← master_id={}", albumId, masterId);
                return true;
            });
            m = Boolean.TRUE.equals(wrote);
        }

        // ------------------------------------------------------------
        // 2) RELEASE — its own transaction, and only if no other album
        //    already owns this release_id. Also catch the race where
        //    another thread commits the same value between the check
        //    and the flush.
        // ------------------------------------------------------------
        if (wantRelease) {
            try {
                Boolean wrote = tx.execute(status -> {
                    Album a = entityManager.find(Album.class, albumId);
                    if (a == null) return false;

                    Integer existing = a.getRelease_id();
                    if (existing != null && existing != 0) return false;

                    Long taken = entityManager.createQuery(
                            "SELECT COUNT(a) FROM Album a " +
                            "WHERE a.release_id = :rid AND a.album_id <> :aid", Long.class)
                            .setParameter("rid", releaseId)
                            .setParameter("aid", albumId)
                            .getSingleResult();

                    if (taken != null && taken > 0) {
                        logger.info("[DG-PERSIST] album {} — release_id={} already owned by {} other album(s); "
                                  + "leaving release_id NULL (master_id still persisted if it was filled)",
                                    albumId, releaseId, taken);
                        return false;
                    }

                    a.setRelease_id(releaseId);
                    logger.debug("[DG-PERSIST] album {} ← release_id={}", albumId, releaseId);
                    return true;
                });
                r = Boolean.TRUE.equals(wrote);
            } catch (DataIntegrityViolationException dive) {
                logger.warn("[DG-PERSIST] album {} — release_id={} collided (race) — release left NULL",
                            albumId, releaseId);
                r = false;
            }
        }

        // ------------------------------------------------------------
        // 3) Classify
        // ------------------------------------------------------------
        if (m && r) return Outcome.FILLED_BOTH;
        if (m)      return Outcome.FILLED_MASTER_ONLY;
        if (r)      return Outcome.FILLED_RELEASE_ONLY;

        // Nothing was written. Did the album already have the values we wanted?
        boolean[] state = tx.execute(status -> {
            Album a = entityManager.find(Album.class, albumId);
            if (a == null) return new boolean[]{ false, false };

            boolean hasMaster  = wantMaster
                              && a.getMaster_id()  != null && a.getMaster_id()  > 0;
            boolean hasRelease = wantRelease
                              && a.getRelease_id() != null && a.getRelease_id() > 0;
            return new boolean[]{ hasMaster, hasRelease };
        });

        boolean masterAlready  = state != null && state[0];
        boolean releaseAlready = state != null && state[1];

        if (masterAlready || releaseAlready) return Outcome.ALREADY_HAD_IT;

        return Outcome.NO_MATCH;
    }
    
    // ===============================================================
    // Loading — keyset pagination
    // ===============================================================
    private int countPending() {
        Long c = tx.execute(status ->
                entityManager.createQuery(
                    "SELECT COUNT(a) FROM Album a " +
                    "WHERE a.title IS NOT NULL AND a.title <> '' " +
                    "  AND ( (a.master_id IS NULL OR a.master_id = 0) " +
                    "     OR (a.release_id IS NULL OR a.release_id = 0) )", Long.class)
                .getSingleResult());
        return c == null ? 0 : c.intValue();
    }

    /**
     * Keyset pagination — always returns the next N rows after the given album_id.
     * No offset: filling an album mid-run cannot cause subsequent albums to be skipped.
     */
    private List<Integer> loadPendingIdsAfterId(Integer afterId, int size) {
        return tx.execute(status -> {
            StringBuilder jpql = new StringBuilder(
                "SELECT a.album_id FROM Album a " +
                "WHERE a.title IS NOT NULL AND a.title <> '' " +
                "  AND ( (a.master_id IS NULL OR a.master_id = 0) " +
                "     OR (a.release_id IS NULL OR a.release_id = 0) ) "
            );
            if (afterId != null) jpql.append("  AND a.album_id > :afterId ");
            jpql.append("ORDER BY a.album_id");

            var query = entityManager.createQuery(jpql.toString(), Integer.class);
            if (afterId != null) query.setParameter("afterId", afterId);
            return query.setMaxResults(size).getResultList();
        });
    }

    /**
     * Loads albums with their artists. No SELECT DISTINCT (SQL Server chokes on TEXT).
     * Dedupes by album_id because LEFT JOIN FETCH fans out one row per junction.
     */
    private List<Album> loadAlbumsWithArtists(List<Integer> ids) {
        List<Album> raw = tx.execute(status ->
                entityManager.createQuery(
                    "SELECT a FROM Album a " +
                    "LEFT JOIN FETCH a.albumContainsArtists aca " +
                    "LEFT JOIN FETCH aca.artist " +
                    "WHERE a.album_id IN :ids", Album.class)
                .setParameter("ids", ids)
                .getResultList());

        Map<Integer, Album> byId = new LinkedHashMap<>();
        for (Album a : raw) byId.putIfAbsent(a.getAlbum_id(), a);
        List<Album> deduped = new ArrayList<>(byId.values());

        logger.debug("[DG-LOAD] ids={} → raw rows={}, distinct albums={}",
                     ids.size(), raw.size(), deduped.size());
        return deduped;
    }

    // ===============================================================
    // Helpers
    // ===============================================================
    private String pickArtistName(Album a) {
        String fallback = null;
        for (AlbumContainsArtist aca : a.getAlbumContainsArtists()) {
            Artist ar = aca.getArtist();
            if (ar == null) continue;
            String name = fullNameOf(ar);
            if (name == null) continue;
            if (aca.getIs_main() == 1) return name;
            if (fallback == null) fallback = name;
        }
        return fallback;
    }

    private static String fullNameOf(Artist ar) {
        String full = trim(ar.getArtist_fullname());
        if (full != null) return full;
        String n = trim(ar.getArtist_name());
        String s = trim(ar.getArtist_surname());
        if (n == null) return s;
        if (s == null) return n;
        return n + " " + s;
    }

    private static String trim(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** null and 0 both mean "not present". */
    private static Integer normalizeId(Integer id) {
        return (id == null || id == 0) ? null : id;
    }

    /** Comma-separated rendering of album ids, capped at `cap` with "+N more". */
    private static String formatIdList(List<Integer> ids, int cap) {
        if (ids == null || ids.isEmpty()) return "[]";
        int shown = Math.min(ids.size(), cap);
        StringBuilder sb = new StringBuilder(shown * 6 + 16);
        sb.append('[');
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append(", ");
            sb.append(ids.get(i));
        }
        sb.append(']');
        if (ids.size() > shown) sb.append(" (+").append(ids.size() - shown).append(" more)");
        return sb.toString();
    }
    
    /**
     * Title-only fallback. Used when title+artist returns nothing — most often
     * because our DB's "main" artist is actually a sideman on the Discogs release,
     * so Discogs' strict artist filter never matches.
     *
     * Scoring: {@link #scoreCandidate} is called with usedArtist=null, so the
     * "specific artist appears in the candidate" branch never fires and the
     * artist component tops out at 0.85 (any credited artist appears). That is
     * a natural, small penalty versus the title+artist pass.
     */
    private MatchHit searchByTitleOnly(Album album, List<String> titles) {
        int calls = 0;
        for (String title : titles) {
            if (calls + 2 > MAX_SEARCH_CALLS_PER_ALBUM) return null;

            // master
            calls++;
            JsonNode masterHit = trySearchTitleOnly(album, title, "master");
            if (masterHit != null) {
                int id = masterHit.path("id").asInt(0);
                if (id > 0) {
                    logger.info("[DG-SEARCH/TITLEONLY] ✔ master hit id={} title='{}'",
                                id, masterHit.path("title").asText(""));
                    return new MatchHit(id, null);
                }
            }

            // release
            calls++;
            JsonNode releaseHit = trySearchTitleOnly(album, title, "release");
            if (releaseHit != null) {
                int id       = releaseHit.path("id").asInt(0);
                int masterId = releaseHit.path("master_id").asInt(0);
                if (id > 0) {
                    logger.info("[DG-SEARCH/TITLEONLY] ✔ release hit id={} master_id={} title='{}'",
                                id, masterId, releaseHit.path("title").asText(""));
                    return new MatchHit(masterId > 0 ? masterId : null, id);
                }
            }
        }
        return null;
    }
    
    private JsonNode trySearchTitleOnly(Album album, String title, String type) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://api.discogs.com/database/search")
                .queryParam("release_title", title)
                .queryParam("type",          type)
                .queryParam("per_page",      SEARCH_PER_PAGE)
                .queryParam("token",         discogsToken)
                .build().encode(StandardCharsets.UTF_8).toUri();

        logger.info("[DG-SEARCH/TITLEONLY] type={} title='{}' → {}",
                    type, title, sanitizeUrl(uri));

        JsonNode body = discogsGet(uri);
        if (body == null) {
            logger.info("[DG-SEARCH/TITLEONLY] type={} — null body", type);
            return null;
        }
        JsonNode results = body.get("results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            logger.info("[DG-SEARCH/TITLEONLY] type={} — 0 results", type);
            return null;
        }

        logger.info("[DG-SEARCH/TITLEONLY] type={} — {} candidates", type, results.size());
        int i = 0;
        for (JsonNode r : results) {
            i++;
            logger.info("[DG-SEARCH/TITLEONLY]   #{}. id={} master_id={} title='{}' year={} country={} format={}",
                        i,
                        r.path("id").asInt(0),
                        r.path("master_id").asInt(0),
                        r.path("title").asText(""),
                        r.path("year").asText(""),
                        r.path("country").asText(""),
                        r.path("format").isArray() ? joinArray(r.get("format")) : r.path("format").asText(""));
        }

        // usedArtist=null → artist component is capped at 0.85
        return pickBestResult(album, title, null, results);
    }
    
 // ===============================================================
 // Google fallback — site:discogs.com search
 // ===============================================================

 /**
  * Last-resort fallback. When the Discogs API can't find a match even with
  * title-only search, ask Google:
  *
  *     site:discogs.com "title" "artist"
  *
  * and extract the first release/master URL from the returned HTML.
  * The resulting MatchHit is treated exactly like an API hit, so the caller
  * still tries to fill the *other* side (master → main_release, or
  * release → master_id).
  *
  * Disabled by default via `discogs.googleFallback.enabled=false`.
  */
 private MatchHit searchByGoogleFallback(Album album, List<String> titles, List<String> artists) {
     if (!googleFallbackEnabled) {
         logger.debug("[DG-GOOGLE {}] google fallback disabled — skipping", album.getAlbum_id());
         return null;
     }
     if (titles.isEmpty() || artists.isEmpty()) return null;

     int albumId = album.getAlbum_id();
     int calls   = 0;

     for (String title : titles) {
         for (String artist : artists) {
             if (calls >= MAX_GOOGLE_CALLS_PER_ALBUM) {
                 logger.debug("[DG-GOOGLE {}] hit call cap ({}) — stopping", albumId,
                              MAX_GOOGLE_CALLS_PER_ALBUM);
                 return null;
             }
             calls++;

             String html = googleSearch(albumId, title, artist);
             if (html == null || html.isEmpty()) continue;

             MatchHit hit = extractFromGoogleHtml(html);
             if (hit != null) {
                 logger.info("[DG-GOOGLE {}] ✔ extracted master={} release={} for title='{}' artist='{}'",
                             albumId, hit.masterId, hit.releaseId, title, artist);
                 return hit;
             }
             logger.info("[DG-GOOGLE {}] no discogs URL found for title='{}' artist='{}'",
                         albumId, title, artist);
         }
     }
     return null;
 }

 /** GET https://www.google.com/search?q=site:discogs.com+"..."+"..." */
 private String googleSearch(int albumId, String title, String artist) {

     // ---- throttle ----
     long now  = System.currentTimeMillis();
     long wait = GOOGLE_MIN_GAP_MS - (now - lastGoogleCall.get());
     if (wait > 0) {
         try { Thread.sleep(wait); }
         catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
     }
     lastGoogleCall.set(System.currentTimeMillis());

     // Escape any quotes in the search terms so they can't break the query.
     String safeTitle  = title.replace("\"", " ");
     String safeArtist = artist.replace("\"", " ");

     URI uri = UriComponentsBuilder
             .fromHttpUrl("https://www.google.com/search")
             .queryParam("q",   "site:discogs.com \"" + safeTitle + "\" \"" + safeArtist + "\"")
             .queryParam("num", 10)
             .queryParam("hl",  "en")
             .build().encode(StandardCharsets.UTF_8).toUri();

     try {
         HttpHeaders headers = new HttpHeaders();
         headers.set("User-Agent",      googleUserAgent);
         headers.set("Accept-Language", "en-US,en;q=0.9");
         headers.set("Accept",          "text/html,application/xhtml+xml");

         HttpEntity<Void> req = new HttpEntity<>(headers);

         logger.info("[DG-GOOGLE {}] GET {}", albumId, uri);
         long t0 = System.currentTimeMillis();
         ResponseEntity<String> resp = restTemplate.exchange(uri, HttpMethod.GET, req, String.class);
         String body = resp.getBody();

         logger.info("[DG-GOOGLE {}] ← {} ({} ms, {} bytes)",
                     albumId, resp.getStatusCode().value(),
                     System.currentTimeMillis() - t0,
                     body == null ? 0 : body.length());

         if (resp.getStatusCode().value() == 429) {
             logger.warn("[DG-GOOGLE {}] 429 from Google — backing off", albumId);
             // extra-punish ourselves so the next album doesn't immediately retry
             lastGoogleCall.set(System.currentTimeMillis() + 30_000);
         }
         return body;

     } catch (Exception e) {
         logger.warn("[DG-GOOGLE {}] request failed: {}", albumId, e.getMessage());
         return null;
     }
 }

 /**
  * Normalise the (very messy) Google HTML then pull the first master and/or
  * release id out of any discogs.com URL we find.
  *
  * Handles:
  *   discogs.com/release/12345
  *   discogs.com\/release\/12345      (JSON-escaped)
  *   discogs.com%2Frelease%2F12345    (URL-encoded, as used in /url?q=...)
  *   discogs.com%252Frelease%252F12345 (double-encoded)
  */
 private MatchHit extractFromGoogleHtml(String html) {
     String normalized = html
             .replace("\\/",   "/")               // JSON escape
             .replace("%252F", "/").replace("%252f", "/")  // double-encoded
             .replace("%2F",   "/").replace("%2f",   "/"); // single-encoded

     Matcher m = DISCOGS_URL_PATTERN.matcher(normalized);

     Integer masterId  = null;
     Integer releaseId = null;
     Set<Integer> seen = new HashSet<>();

     while (m.find()) {
         String type = m.group(1).toLowerCase();
         int    id;
         try { id = Integer.parseInt(m.group(2)); }
         catch (NumberFormatException nfe) { continue; }

         if (id <= 0 || !seen.add(id)) continue;

         if ("master".equals(type) && masterId == null) {
             masterId = id;
             // prefer master — keep scanning for a release to complete the pair
         } else if ("release".equals(type) && releaseId == null) {
             releaseId = id;
         }

         if (masterId != null && releaseId != null) break;
     }

     if (masterId == null && releaseId == null) return null;
     return new MatchHit(masterId, releaseId);
 }
    
    // ===============================================================
    // Inner types
    // ===============================================================
    private enum Outcome {
        FILLED_BOTH         (true,  true,  false),
        FILLED_MASTER_ONLY  (true,  false, false),
        FILLED_RELEASE_ONLY (false, true,  false),
        ALREADY_HAD_IT      (false, false, true),
        NO_MATCH            (false, false, false),
        NOTHING_TO_DO       (false, false, false);

        final boolean masterFilled;
        final boolean releaseFilled;
        final boolean alreadyHad;

        Outcome(boolean m, boolean r, boolean ah) {
            this.masterFilled  = m;
            this.releaseFilled = r;
            this.alreadyHad    = ah;
        }
    }
    
    
    private static class MatchHit {
        final Integer masterId;
        final Integer releaseId;
        MatchHit(Integer masterId, Integer releaseId) {
            this.masterId  = masterId;
            this.releaseId = releaseId;
        }
    }
}