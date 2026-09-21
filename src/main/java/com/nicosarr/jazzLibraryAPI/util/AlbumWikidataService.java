package com.nicosarr.jazzLibraryAPI.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;



import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlbumWikidataService {

    private static final Logger logger = LoggerFactory.getLogger(AlbumWikidataService.class);

    @Autowired private RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, WikidataAlbum> albumCache = new ConcurrentHashMap<>();
    private final Map<String, String>         labelCache = new ConcurrentHashMap<>();

    // ---- Wikidata property IDs ----
    private static final String P_TITLE             = "P1476";
    private static final String P_PUBLICATION_DATE  = "P577";
    private static final String P_RECORD_LABEL      = "P264";
    private static final String P_GENRE             = "P136";
    private static final String P_PERFORMER         = "P175";
    private static final String P_PRODUCER          = "P162";
    private static final String P_COMPOSER          = "P86";
    private static final String P_IMAGE             = "P18";
    private static final String P_MB_RELEASE_GROUP  = "P436";
    private static final String P_DISCOGS_MASTER    = "P1954";
    private static final String P_DISCOGS_RELEASE   = "P1955";
    private static final String P_INSTANCE_OF       = "P31";

    private static final Map<String,String> INSTANCE_TO_TYPE = Map.ofEntries(
        Map.entry("Q208569",  "album"),
        Map.entry("Q20670778","album"),
        Map.entry("Q482994",  "album"),
        Map.entry("Q222910",  "live"),
        Map.entry("Q13442814","compilation"),
        Map.entry("Q209939",  "ep"),
        Map.entry("Q169930",  "ep"),
        Map.entry("Q108352368","single"),
        Map.entry("Q134556",  "single")
    );

    // ---------------------------------------------------------------
    // Wikipedia article URL → Wikidata Q-id
    // ---------------------------------------------------------------
    @SuppressWarnings("unchecked")
    public String resolveQidFromWikipediaUrl(String wikipediaUrl) {
        if (wikipediaUrl == null || wikipediaUrl.isBlank()) return null;
        int idx = wikipediaUrl.indexOf("/wiki/");
        if (idx < 0) return null;
        String title = java.net.URLDecoder.decode(
                wikipediaUrl.substring(idx + 6), StandardCharsets.UTF_8).replace('_', ' ');

        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://en.wikipedia.org/w/api.php")
                .queryParam("action",    "query")
                .queryParam("format",    "json")
                .queryParam("prop",      "pageprops")
                .queryParam("ppprop",    "wikibase_item")
                .queryParam("redirects", "1")
                .queryParam("titles",    title)
                .build().encode(StandardCharsets.UTF_8).toUri();

        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(uri, Map.class);
            if (resp.getBody() == null) return null;
            Map<String,Object> query = (Map<String,Object>) resp.getBody().get("query");
            if (query == null) return null;
            Map<String,Object> pages = (Map<String,Object>) query.get("pages");
            if (pages == null) return null;
            for (Object p : pages.values()) {
                Map<String,Object> page = (Map<String,Object>) p;
                Map<String,Object> props = (Map<String,Object>) page.get("pageprops");
                if (props != null && props.get("wikibase_item") != null)
                    return (String) props.get("wikibase_item");
            }
        } catch (Exception e) {
            logger.warn("resolveQid failed for {}: {}", wikipediaUrl, e.getMessage());
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Batch fetch (50 per call, cached)
    // ---------------------------------------------------------------
    public Map<String, WikidataAlbum> fetchAlbums(Collection<String> qids) {
        Map<String, WikidataAlbum> result = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (String q : qids) {
            if (q == null || q.isBlank()) continue;
            WikidataAlbum c = albumCache.get(q);
            if (c != null) result.put(q, c); else missing.add(q);
        }
        for (int i = 0; i < missing.size(); i += 50) {
            List<String> batch = missing.subList(i, Math.min(i + 50, missing.size()));
            Map<String, WikidataAlbum> got = fetchBatch(batch);
            for (Map.Entry<String, WikidataAlbum> e : got.entrySet()) {
                albumCache.put(e.getKey(), e.getValue());
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    private Map<String, WikidataAlbum> fetchBatch(List<String> qids) {
        Map<String, WikidataAlbum> out = new HashMap<>();
        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://www.wikidata.org/w/api.php")
                .queryParam("action",     "wbgetentities")
                .queryParam("format",     "json")
                .queryParam("ids",        String.join("|", qids))
                .queryParam("props",      "claims|labels|sitelinks")
                .queryParam("languages",  "en")
                .queryParam("sitefilter", "enwiki")
                .build().encode(StandardCharsets.UTF_8).toUri();

        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(uri, String.class);
            if (resp.getBody() == null) return out;

            JsonNode entities = mapper.readTree(resp.getBody()).get("entities");
            if (entities == null) return out;

            Map<String, WikidataAlbum> raw = new HashMap<>();
            Set<String> refQids = new HashSet<>();

            entities.fieldNames().forEachRemaining(qid -> {
                WikidataAlbum a = parseEntity(qid, entities.get(qid), refQids);
                if (a != null) raw.put(qid, a);
            });

            Map<String, String> names = resolveLabels(refQids);
            raw.values().forEach(a -> a.resolveNames(names));
            out.putAll(raw);
        } catch (Exception e) {
            logger.warn("Wikidata batch failed ({} ids): {}", qids.size(), e.getMessage());
        }
        return out;
    }

    private WikidataAlbum parseEntity(String qid, JsonNode ent, Set<String> refQids) {
        if (ent == null || ent.has("missing")) return null;
        WikidataAlbum a = new WikidataAlbum();
        a.qid = qid;

        JsonNode en = ent.path("labels").path("en").path("value");
        if (!en.isMissingNode()) a.englishLabel = en.asText();

        JsonNode sitelinks = ent.get("sitelinks");
        if (sitelinks != null && sitelinks.has("enwiki"))
            a.enwikiTitle = sitelinks.get("enwiki").get("title").asText();

        JsonNode claims = ent.get("claims");
        if (claims == null) return a;

        List<String> dates = extractTimeValues(claims, P_PUBLICATION_DATE);
        if (!dates.isEmpty()) { dates.sort(String::compareTo); a.publicationDate = dates.get(0); }

        List<String> titles = extractMonolingualText(claims, P_TITLE);
        if (!titles.isEmpty()) a.title = titles.get(0);

        a.labelQids     = extractEntityIds(claims, P_RECORD_LABEL);
        a.genreQids     = extractEntityIds(claims, P_GENRE);
        a.performerQids = extractEntityIds(claims, P_PERFORMER);
        a.producerQids  = extractEntityIds(claims, P_PRODUCER);
        a.composerQids  = extractEntityIds(claims, P_COMPOSER);
        a.instanceQids  = extractEntityIds(claims, P_INSTANCE_OF);
        refQids.addAll(a.labelQids);
        refQids.addAll(a.genreQids);
        refQids.addAll(a.performerQids);
        refQids.addAll(a.producerQids);
        refQids.addAll(a.composerQids);

        a.imageFilenames = extractStringValues(claims, P_IMAGE);

        List<String> mb = extractStringValues(claims, P_MB_RELEASE_GROUP);
        if (!mb.isEmpty()) a.musicBrainzReleaseGroupId = mb.get(0);

        List<String> dm = extractStringValues(claims, P_DISCOGS_MASTER);
        if (!dm.isEmpty()) a.discogsMasterId = dm.get(0);

        List<String> dr = extractStringValues(claims, P_DISCOGS_RELEASE);
        if (!dr.isEmpty()) a.discogsReleaseId = dr.get(0);

        for (String q : a.instanceQids) {
            String t = INSTANCE_TO_TYPE.get(q);
            if (t != null) { a.releaseType = t; break; }
        }
        return a;
    }

    private List<String> extractTimeValues(JsonNode claims, String prop) {
        List<String> out = new ArrayList<>();
        JsonNode arr = claims.get(prop);
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode c : arr) {
            JsonNode v = c.path("mainsnak").path("datavalue").path("value").path("time");
            if (!v.isMissingNode()) out.add(v.asText());
        }
        return out;
    }
    private List<String> extractMonolingualText(JsonNode claims, String prop) {
        List<String> out = new ArrayList<>();
        JsonNode arr = claims.get(prop);
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode c : arr) {
            JsonNode v = c.path("mainsnak").path("datavalue").path("value").path("text");
            if (!v.isMissingNode()) out.add(v.asText());
        }
        return out;
    }
    private List<String> extractEntityIds(JsonNode claims, String prop) {
        List<String> out = new ArrayList<>();
        JsonNode arr = claims.get(prop);
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode c : arr) {
            String id = c.path("mainsnak").path("datavalue").path("value").path("id").asText(null);
            if (id != null && id.startsWith("Q")) out.add(id);
        }
        return out;
    }
    private List<String> extractStringValues(JsonNode claims, String prop) {
        List<String> out = new ArrayList<>();
        JsonNode arr = claims.get(prop);
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode c : arr) {
            JsonNode v = c.path("mainsnak").path("datavalue").path("value");
            if (!v.isMissingNode()) out.add(v.asText());
        }
        return out;
    }

    private Map<String, String> resolveLabels(Set<String> qids) {
        Map<String, String> result = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (String q : qids) {
            if (q == null || q.isBlank()) continue;
            String c = labelCache.get(q);
            if (c != null) result.put(q, c); else missing.add(q);
        }
        for (int i = 0; i < missing.size(); i += 50) {
            List<String> batch = missing.subList(i, Math.min(i + 50, missing.size()));
            URI uri = UriComponentsBuilder
                    .fromHttpUrl("https://www.wikidata.org/w/api.php")
                    .queryParam("action",    "wbgetentities")
                    .queryParam("format",    "json")
                    .queryParam("ids",       String.join("|", batch))
                    .queryParam("props",     "labels")
                    .queryParam("languages", "en")
                    .build().encode(StandardCharsets.UTF_8).toUri();
            try {
                ResponseEntity<String> resp = restTemplate.getForEntity(uri, String.class);
                if (resp.getBody() == null) continue;
                JsonNode entities = mapper.readTree(resp.getBody()).get("entities");
                if (entities == null) continue;
                entities.fields().forEachRemaining(e -> {
                    JsonNode en = e.getValue().path("labels").path("en").path("value");
                    if (!en.isMissingNode()) {
                        labelCache.put(e.getKey(), en.asText());
                        result.put(e.getKey(), en.asText());
                    }
                });
            } catch (Exception e) {
                logger.warn("Label resolve failed ({} ids): {}", batch.size(), e.getMessage());
            }
        }
        return result;
    }

    // ---------------------------------------------------------------
    public static class WikidataAlbum {
        public String qid;
        public String englishLabel;
        public String enwikiTitle;
        public String title;
        public String publicationDate;
        public List<String> labelQids     = new ArrayList<>();
        public List<String> genreQids     = new ArrayList<>();
        public List<String> performerQids = new ArrayList<>();
        public List<String> producerQids  = new ArrayList<>();
        public List<String> composerQids  = new ArrayList<>();
        public List<String> instanceQids  = new ArrayList<>();
        public List<String> imageFilenames = new ArrayList<>();
        public String musicBrainzReleaseGroupId;
        public String discogsMasterId;
        public String discogsReleaseId;
        public String releaseType;
        public String rawJson;

        public List<String> labelNames     = new ArrayList<>();
        public List<String> genreNames     = new ArrayList<>();
        public List<String> performerNames = new ArrayList<>();
        public List<String> producerNames  = new ArrayList<>();
        public List<String> composerNames  = new ArrayList<>();

        void resolveNames(Map<String,String> labels) {
            labelNames.clear(); genreNames.clear();
            performerNames.clear(); producerNames.clear(); composerNames.clear();
            for (String q : labelQids)     { String n = labels.get(q); if (n != null) labelNames.add(n); }
            for (String q : genreQids)     { String n = labels.get(q); if (n != null) genreNames.add(n); }
            for (String q : performerQids) { String n = labels.get(q); if (n != null) performerNames.add(n); }
            for (String q : producerQids)  { String n = labels.get(q); if (n != null) producerNames.add(n); }
            for (String q : composerQids)  { String n = labels.get(q); if (n != null) composerNames.add(n); }
        }

        public Integer getYear() {
            if (publicationDate == null) return null;
            try {
                String s = publicationDate.startsWith("+") ? publicationDate.substring(1) : publicationDate;
                return Integer.parseInt(s.substring(0, 4));
            } catch (Exception e) { return null; }
        }
        public String getIsoDate() {
            if (publicationDate == null) return null;
            String s = publicationDate.startsWith("+") ? publicationDate.substring(1) : publicationDate;
            return s.length() >= 10 ? s.substring(0, 10) : null;
        }
        public String getPrettyDate() {
            String iso = getIsoDate();
            if (iso == null) return null;
            try {
                java.time.LocalDate d = java.time.LocalDate.parse(iso);
                return d.format(java.time.format.DateTimeFormatter.ofPattern(
                        "MMMM d, yyyy", java.util.Locale.ENGLISH));
            } catch (Exception e) { return null; }
        }
    }
}