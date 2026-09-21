package com.nicosarr.jazzLibraryAPI.Album;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.nicosarr.jazzLibraryAPI.util.AlbumWikidataService;
import com.nicosarr.jazzLibraryAPI.util.AlbumWikidataService.WikidataAlbum;
import com.nicosarr.jazzLibraryAPI.util.AlbumWikipediaScraperService;
import com.nicosarr.jazzLibraryAPI.util.AlbumWikipediaScraperService.ScrapedAlbumData;
import com.nicosarr.jazzLibraryAPI.util.JobContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Service
public class AlbumEnrichmentService {

    private static final Logger logger = LoggerFactory.getLogger(AlbumEnrichmentService.class);

    private static final Pattern QID = Pattern.compile("^Q\\d+$");
    private static final int BATCH = 200;

    @PersistenceContext private EntityManager entityManager;
    @Autowired private AlbumWikidataService wikidataService;
    @Autowired private AlbumWikipediaScraperService scraperService;
    @Autowired private TransactionTemplate transactionTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    // ===============================================================
    // ENTRY POINT
    // ===============================================================
    public String enrichAll(JobContext jobContext, Integer limit) {
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger updated   = new AtomicInteger();
        AtomicInteger errors    = new AtomicInteger();

        logger.info("=== Album enrichment job START (limit={}) ===", limit);

        int offset = 0;
        int batchNo = 0;
        while (true) {
            if (jobContext != null && jobContext.isCancelled()) {
                logger.info("Job cancelled by user at offset {}", offset);
                break;
            }

            logger.debug("Loading batch #{} (offset={}, size={})", batchNo, offset, BATCH);
            List<Album> batch = loadBatch(offset, BATCH);
            if (batch.isEmpty()) {
                logger.debug("No more albums to process at offset {}", offset);
                break;
            }
            batchNo++;
            logger.info("--- Batch #{} : {} albums loaded ---", batchNo, batch.size());

            // ---------- PHASE A : resolve Q-ids (network) ----------
            Map<Integer, String> albumToQid = resolveQids(batch);

            // ---------- PHASE B : batch-fetch Wikidata (network) ----------
            Map<String, WikidataAlbum> wdByQid = albumToQid.isEmpty()
                    ? Collections.emptyMap()
                    : wikidataService.fetchAlbums(albumToQid.values());
            logger.debug("Wikidata returned {} entities for {} requested qids",
                    wdByQid.size(), albumToQid.size());

            // ---------- PHASE C : scrape Wikipedia where needed (network) ----------
            Map<Integer, ScrapedAlbumData> scrapes = scrapeBatch(batch);

            // ---------- PHASE D : ONE transaction for the whole batch ----------
            int[] result = writeBatch(batch, albumToQid, wdByQid, scrapes, errors);
            processed.addAndGet(batch.size());
            updated.addAndGet(result[0]);

            logger.info("Batch #{} done — updated={}, errors(total)={}",
                    batchNo, result[0], errors.get());

            offset += BATCH;
            if (limit != null && offset >= limit) {
                logger.info("Reached limit {} — stopping", limit);
                break;
            }
        }

        String summary = String.format(
                "Album enrichment finished: processed=%d, updated=%d, errors=%d",
                processed.get(), updated.get(), errors.get());
        logger.info(summary);
        return summary;
    }

    // ===============================================================
    // PHASE A : resolve Q-ids for every album in the batch
    // ===============================================================
    private Map<Integer, String> resolveQids(List<Album> batch) {
        Map<Integer, String> albumToQid = new HashMap<>();
        int fromCache = 0, resolved = 0, none = 0;

        for (Album a : batch) {
            String qid = a.getWikidata_id();
            if (qid != null && QID.matcher(qid).matches()) {
                albumToQid.put(a.getAlbum_id(), qid);
                fromCache++;
                continue;
            }
            String url = a.getWikipedia_url();
            if (url == null || url.isBlank()) { none++; continue; }

            qid = wikidataService.resolveQidFromWikipediaUrl(url);
            if (qid != null && QID.matcher(qid).matches()) {
                albumToQid.put(a.getAlbum_id(), qid);
                resolved++;
                logger.debug("Resolved album {} → {} via {}", a.getAlbum_id(), qid, url);
            } else {
                none++;
                logger.debug("No Q-id for album {} (wiki url={})", a.getAlbum_id(), url);
            }
        }
        logger.debug("Q-id resolution: {} reused, {} freshly resolved, {} without id",
                fromCache, resolved, none);
        return albumToQid;
    }

    // ===============================================================
    // PHASE C : scrape album Wikipedia pages (only when needed)
    // ===============================================================
    private Map<Integer, ScrapedAlbumData> scrapeBatch(List<Album> batch) {
        Map<Integer, ScrapedAlbumData> out = new HashMap<>();
        int needed = 0, skipped = 0, ok = 0;

        for (Album a : batch) {
            String url = a.getWikipedia_url();
            if (url == null || url.isBlank()) { skipped++; continue; }

            boolean needsPersonnel = isBlank(a.getExtra_artists());
            boolean needsTracklist = isBlank(a.getTracklist());
            boolean needsArticle   = isBlank(a.getWikipedia_data());

            if (!needsPersonnel && !needsTracklist && !needsArticle) {
                skipped++;
                continue;
            }
            needed++;

            ScrapedAlbumData data = scraperService.scrape(url);
            if (data != null) {
                out.put(a.getAlbum_id(), data);
                ok++;
            }
        }
        logger.debug("Scraping: {} needed, {} fetched, {} skipped (already populated)",
                needed, ok, skipped);
        return out;
    }

    // ===============================================================
    // PHASE D : apply everything inside ONE transaction
    // ===============================================================
    private int[] writeBatch(List<Album> batch,
                             Map<Integer, String> albumToQid,
                             Map<String, WikidataAlbum> wdByQid,
                             Map<Integer, ScrapedAlbumData> scrapes,
                             AtomicInteger errors) {

        Integer[] ids = batch.stream().map(Album::getAlbum_id).toArray(Integer[]::new);

        return transactionTemplate.execute(status -> {
            // Load the full batch in a single query → all managed.
            List<Album> managed = entityManager
                    .createQuery("SELECT a FROM Album a WHERE a.album_id IN :ids", Album.class)
                    .setParameter("ids", Arrays.asList(ids))
                    .getResultList();

            Map<Integer, Album> byId = new HashMap<>();
            for (Album a : managed) byId.put(a.getAlbum_id(), a);

            int updated = 0;
            for (Album stale : batch) {
                Album a = byId.get(stale.getAlbum_id());
                if (a == null) {
                    logger.debug("Album {} disappeared between load and write", stale.getAlbum_id());
                    continue;
                }

                boolean any = false;
                try {
                    // ---- Wikidata ----
                    String qid = albumToQid.get(a.getAlbum_id());
                    if (qid != null) {
                        WikidataAlbum wd = wdByQid.get(qid);
                        if (wd != null) {
                            any |= applyWikidata(a, wd);
                        }
                    }

                    // ---- Scrape ----
                    ScrapedAlbumData sc = scrapes.get(a.getAlbum_id());
                    if (sc != null) any |= applyScrape(a, sc);

                    if (any) updated++;
                } catch (Exception e) {
                    errors.incrementAndGet();
                    logger.error("Album {} could not be enriched: {}",
                                 a.getAlbum_id(), e.getMessage(), e);
                }
            }
            logger.debug("Batch write complete: {} of {} albums updated", updated, batch.size());
            return new int[]{ updated };
        });
    }

    // ===============================================================
    // Helpers
    // ===============================================================
    private List<Album> loadBatch(int offset, int size) {
        String jpql = "SELECT a FROM Album a " +
                      "WHERE (a.wikidata_id IS NOT NULL AND a.wikidata_id <> '') " +
                      "   OR (a.wikipedia_url IS NOT NULL AND a.wikipedia_url <> '') " +
                      "ORDER BY a.album_id";
        TypedQuery<Album> q = entityManager.createQuery(jpql, Album.class);
        q.setFirstResult(offset);
        q.setMaxResults(size);
        return q.getResultList();
    }

    // ===============================================================
    // Wikidata → Album (per album, inside tx)
    // ===============================================================
    private boolean applyWikidata(Album a, WikidataAlbum wd) {
        boolean changed = false;

        if (isBlank(a.getWikidata_id()) && wd.qid != null) {
            a.setWikidata_id(wd.qid); changed = true;
        }

        // OVERRIDE FIELDS
        String newTitle = firstNonBlank(wd.title, wd.englishLabel);
        if (newTitle != null && !newTitle.equals(a.getTitle())) {
            a.setTitle(truncate(newTitle, 500)); changed = true;
        }
        Integer wdYear = wd.getYear();
        if (wdYear != null && !wdYear.equals(a.getYear())) {
            a.setYear(wdYear); changed = true;
        }
        String iso = wd.getIsoDate();
        if (iso != null && !iso.equals(a.getReleased())) {
            a.setReleased(iso); changed = true;
        }
        if (!wd.labelNames.isEmpty()) {
            String joined = String.join(", ", wd.labelNames);
            if (!joined.equals(a.getLabels())) { a.setLabels(joined); changed = true; }
        }

        // FILL-ONLY FIELDS
        if (isBlank(a.getReleased_formatted())) {
            String pretty = wd.getPrettyDate();
            if (pretty != null) { a.setReleased_formatted(pretty); changed = true; }
        }
        if (isBlank(a.getGenres()) && !wd.genreNames.isEmpty()) {
            a.setGenres(writeJsonArray(wd.genreNames)); changed = true;
        }
        if (isBlank(a.getStyles()) && !wd.genreNames.isEmpty()) {
            a.setStyles(writeJsonArray(wd.genreNames)); changed = true;
        }
        if (isBlank(a.getExtra_artists())) {
            List<Map<String,Object>> credits = new ArrayList<>();
            for (String n : wd.performerNames) credits.add(personEntry(n, List.of("performer")));
            for (String n : wd.producerNames)  credits.add(personEntry(n, List.of("producer")));
            for (String n : wd.composerNames)  credits.add(personEntry(n, List.of("composer")));
            if (!credits.isEmpty()) {
                try { a.setExtra_artists(mapper.writeValueAsString(credits)); changed = true; }
                catch (Exception ignored) { }
            }
        }
        if (isBlank(a.getCompanies()) && !wd.labelNames.isEmpty()) {
            a.setCompanies(writeJsonArray(wd.labelNames)); changed = true;
        }
        if (isBlank(a.getImages()) && !wd.imageFilenames.isEmpty()) {
            List<String> urls = new ArrayList<>();
            for (String fn : wd.imageFilenames) {
                urls.add("https://commons.wikimedia.org/wiki/Special:FilePath/"
                        + fn.replace(' ', '_'));
            }
            a.setImages(writeJsonArray(urls)); changed = true;
        }
        if (isBlank(a.getMusicbrainz_uuid()) && wd.musicBrainzReleaseGroupId != null) {
            a.setMusicbrainz_uuid(wd.musicBrainzReleaseGroupId); changed = true;
        }
        if (isBlank(a.getCoverartarchive_thumb()) && wd.musicBrainzReleaseGroupId != null) {
            a.setCoverartarchive_thumb(
                "https://coverartarchive.org/release-group/"
                + wd.musicBrainzReleaseGroupId + "/front-250");
            changed = true;
        }
        if (a.getMaster_id() == null && wd.discogsMasterId != null) {
            try { a.setMaster_id(Integer.parseInt(wd.discogsMasterId)); changed = true; }
            catch (NumberFormatException ignored) { }
        }
        if (a.getRelease_id() == null && wd.discogsReleaseId != null) {
            try { a.setRelease_id(Integer.parseInt(wd.discogsReleaseId)); changed = true; }
            catch (NumberFormatException ignored) { }
        }
        if (isBlank(a.getRelease_type()) && wd.releaseType != null) {
            a.setRelease_type(wd.releaseType); changed = true;
        }
        if (isBlank(a.getWikipedia_url()) && wd.enwikiTitle != null) {
            a.setWikipedia_url("https://en.wikipedia.org/wiki/"
                    + wd.enwikiTitle.replace(' ', '_'));
            changed = true;
        }
        // NOTE: wikipedia_data is NOT set here — it comes from the scraper.

        return changed;
    }

    // ===============================================================
    // Scraper → Album (per album, inside tx)
    // ===============================================================
    private boolean applyScrape(Album a, ScrapedAlbumData sc) {
        boolean changed = false;
        try {
            // Personnel: Wikipedia wins over the Wikidata fallback.
            if (!sc.personnel.isEmpty()) {
                String json = mapper.writeValueAsString(sc.personnel);
                if (!json.equals(a.getExtra_artists())) {
                    a.setExtra_artists(json); changed = true;
                }
            }
            // Tracklist
            if (!sc.tracklist.isEmpty()) {
                String json = mapper.writeValueAsString(sc.tracklist);
                if (!json.equals(a.getTracklist())) {
                    a.setTracklist(json); changed = true;
                }
            }
            // Article text → wikipedia_data
            if (sc.articleText != null && !sc.articleText.isBlank()) {
                if (!sc.articleText.equals(a.getWikipedia_data())) {
                    a.setWikipedia_data(sc.articleText); changed = true;
                }
            }
        } catch (Exception e) {
            logger.warn("Serialize scrape result for album {} failed: {}",
                        a.getAlbum_id(), e.getMessage());
        }
        return changed;
    }

    private Map<String,Object> personEntry(String name, List<String> roles) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("roles", roles);
        return m;
    }

    private String writeJsonArray(List<String> values) {
        ArrayNode arr = mapper.createArrayNode();
        values.forEach(arr::add);
        return arr.toString();
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String firstNonBlank(String... vs) {
        for (String v : vs) if (v != null && !v.isBlank()) return v;
        return null;
    }
    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}