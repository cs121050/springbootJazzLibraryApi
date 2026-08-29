package com.nicosarr.jazzLibraryAPI.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WikipediaService {

    private static final Logger logger = LoggerFactory.getLogger(WikipediaService.class);
    private final RestTemplate restTemplate;

    public WikipediaService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch Wikipedia URL for an artist.
     * First tries exact title match, then search with "jazz" filter.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> fetchWikidataAndWikipedia(String musicbrainzUuid, String fullName) {
        // 1. If we have a MusicBrainz UUID, use it to query Wikidata directly
        if (musicbrainzUuid != null && !musicbrainzUuid.trim().isEmpty()) {
            String wikidataId = findWikidataIdByMusicbrainz(musicbrainzUuid);
            if (wikidataId != null) {
                String wikipediaUrl = getWikipediaUrlFromWikidata(wikidataId);
                Map<String, String> result = new HashMap<>();
                result.put("wikidataId", wikidataId);
                result.put("wikipediaUrl", wikipediaUrl);
                return result;
            }
        }

        // 2. Fallback: search Wikipedia directly
        return searchWikipedia(fullName);
    }

    /**
     * Query Wikidata for an entity that has the given MusicBrainz ID (P434).
     */
    @SuppressWarnings("unchecked")
    private String findWikidataIdByMusicbrainz(String musicbrainzUuid) {
        String url = "https://www.wikidata.org/w/api.php?action=query&list=search&srsearch=haswbstatement:P434=" + musicbrainzUuid + "&format=json";
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, getHttpEntity(), Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) return null;
            Map<String, Object> body = response.getBody();
            if (body == null) return null;

            Map<String, Object> query = (Map<String, Object>) body.get("query");
            if (query == null) return null;

            List<Map<String, Object>> searchResults = (List<Map<String, Object>>) query.get("search");
            if (searchResults == null || searchResults.isEmpty()) return null;

            String title = (String) searchResults.get(0).get("title");
            return (title != null && title.startsWith("Q")) ? title : null;
        } catch (Exception e) {
            logger.warn("MusicBrainz lookup failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Search Wikipedia by title, with "jazz" filter.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> searchWikipedia(String fullName) {
        // Try exact title match first
        String exactUrl = UriComponentsBuilder
                .fromHttpUrl("https://en.wikipedia.org/w/api.php")
                .queryParam("action", "query")
                .queryParam("titles", fullName)
                .queryParam("format", "json")
                .queryParam("prop", "info")
                .queryParam("inprop", "url")
                .build()
                .toUriString();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(exactUrl, HttpMethod.GET, getHttpEntity(), Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = response.getBody();
                if (body != null) {
                    Map<String, Object> query = (Map<String, Object>) body.get("query");
                    if (query != null) {
                        Map<String, Object> pages = (Map<String, Object>) query.get("pages");
                        if (pages != null && !pages.isEmpty()) {
                            // Check if the page exists (not a missing page)
                            for (Object pageObj : pages.values()) {
                                Map<String, Object> page = (Map<String, Object>) pageObj;
                                if (!page.containsKey("missing")) {
                                    String url = (String) page.get("fullurl");
                                    if (url != null) {
                                        Map<String, String> result = new HashMap<>();
                                        result.put("wikidataId", null); // We don't have Wikidata ID from exact match
                                        result.put("wikipediaUrl", url);
                                        return result;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Exact title match failed: {}", e.getMessage());
        }

        // If exact match fails, search with "jazz" in the title/description
        String searchQuery = fullName + " jazz";
        String searchUrl = UriComponentsBuilder
                .fromHttpUrl("https://en.wikipedia.org/w/api.php")
                .queryParam("action", "query")
                .queryParam("list", "search")
                .queryParam("srsearch", searchQuery)
                .queryParam("srlimit", 5)
                .queryParam("format", "json")
                .queryParam("prop", "info")
                .queryParam("inprop", "url")
                .build()
                .toUriString();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(searchUrl, HttpMethod.GET, getHttpEntity(), Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) return null;

            Map<String, Object> body = response.getBody();
            if (body == null) return null;

            Map<String, Object> query = (Map<String, Object>) body.get("query");
            if (query == null) return null;

            List<Map<String, Object>> searchResults = (List<Map<String, Object>>) query.get("search");
            if (searchResults == null || searchResults.isEmpty()) return null;

            // Find the first result that contains "jazz" in the title or snippet
            for (Map<String, Object> result : searchResults) {
                String title = (String) result.get("title");
                String snippet = (String) result.get("snippet");
                if (title != null && snippet != null &&
                    (title.toLowerCase().contains("jazz") || snippet.toLowerCase().contains("jazz"))) {
                    // Get the full URL for this page
                    String pageUrl = getWikipediaUrlByTitle(title);
                    if (pageUrl != null) {
                        Map<String, String> finalResult = new HashMap<>();
                        finalResult.put("wikidataId", null);
                        finalResult.put("wikipediaUrl", pageUrl);
                        return finalResult;
                    }
                }
            }

            // If none contain "jazz", take the first result
            String firstTitle = (String) searchResults.get(0).get("title");
            String firstUrl = getWikipediaUrlByTitle(firstTitle);
            if (firstUrl != null) {
                Map<String, String> finalResult = new HashMap<>();
                finalResult.put("wikidataId", null);
                finalResult.put("wikipediaUrl", firstUrl);
                return finalResult;
            }

        } catch (Exception e) {
            logger.warn("Wikipedia search failed: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get Wikipedia URL for a given page title.
     */
    @SuppressWarnings("unchecked")
    private String getWikipediaUrlByTitle(String title) {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://en.wikipedia.org/w/api.php")
                .queryParam("action", "query")
                .queryParam("titles", title)
                .queryParam("format", "json")
                .queryParam("prop", "info")
                .queryParam("inprop", "url")
                .build()
                .toUriString();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, getHttpEntity(), Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) return null;

            Map<String, Object> body = response.getBody();
            if (body == null) return null;

            Map<String, Object> query = (Map<String, Object>) body.get("query");
            if (query == null) return null;

            Map<String, Object> pages = (Map<String, Object>) query.get("pages");
            if (pages == null || pages.isEmpty()) return null;

            for (Object pageObj : pages.values()) {
                Map<String, Object> page = (Map<String, Object>) pageObj;
                if (!page.containsKey("missing")) {
                    return (String) page.get("fullurl");
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get URL for title '{}': {}", title, e.getMessage());
        }
        return null;
    }

    /**
     * Get Wikipedia URL from Wikidata ID.
     */
    private String getWikipediaUrlFromWikidata(String wikidataId) {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://en.wikipedia.org/w/api.php")
                .queryParam("action", "query")
                .queryParam("prop", "info")
                .queryParam("inprop", "url")
                .queryParam("titles", wikidataId)
                .queryParam("format", "json")
                .build()
                .toUriString();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, getHttpEntity(), Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) return null;

            Map<String, Object> body = response.getBody();
            if (body == null) return null;

            Map<String, Object> query = (Map<String, Object>) body.get("query");
            if (query == null) return null;

            Map<String, Object> pages = (Map<String, Object>) query.get("pages");
            if (pages == null || pages.isEmpty()) return null;

            for (Object pageObj : pages.values()) {
                Map<String, Object> page = (Map<String, Object>) pageObj;
                if (!page.containsKey("missing")) {
                    return (String) page.get("fullurl");
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get Wikipedia URL from Wikidata ID {}: {}", wikidataId, e.getMessage());
        }
        return null;
    }

    /**
     * Helper to create HttpEntity with proper headers.
     */
    private HttpEntity<?> getHttpEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "MyJazzApp/1.0 (https://myjazzapi.com; contact@myjazzapi.com)");
        return new HttpEntity<>(headers);
    }
}