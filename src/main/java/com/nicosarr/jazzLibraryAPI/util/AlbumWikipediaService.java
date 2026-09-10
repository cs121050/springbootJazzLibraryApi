package com.nicosarr.jazzLibraryAPI.util;

import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AlbumWikipediaService {

    private static final Logger logger = LoggerFactory.getLogger(AlbumWikipediaService.class);

    @Autowired
    private RestTemplate restTemplate;

    private static final Set<String> LABEL_KEYWORDS = Set.of(
        "ecm", "blue note", "impulse", "verve", "prestige", "atlantic",
        "riverside", "moodsville", "columbia", "rca", "victor", "savoy",
        "candid", "muse", "black saint", "hat hut", "incus",
        "moers", "charly", "soul note", "nine winds", "music & arts",
        "timeless", "amigo", "concord", "legacy"
    );

    private static final Set<String> NON_ALBUM_KEYWORDS = Set.of(
        "retrieved", "isbn", "university", "press", "edited", "births",
        "deaths", "last edited", "use mdy", "use american", "oxford",
        "foreword", "interview", "blog", "archive", "wayback",
        "journal", "editorial", "doi", "issn", "review", "obituary",        "bibliography", "references", "notes", "further reading"
    );

    // ---------- Public entry point ----------
    public List<AlbumRawData> fetchDiscography(Artist artist) {
        logger.debug("Fetching discography for artist: {} (id={})", artist.getArtist_name(), artist.getArtist_id());
        List<AlbumRawData> albums = new ArrayList<>();

        String wikipediaUrl = artist.getWikipedia_url();
        if (wikipediaUrl == null || wikipediaUrl.isBlank()) {
            String name = artist.getArtist_name() + " " + artist.getArtist_surname();
            wikipediaUrl = "https://en.wikipedia.org/wiki/" + name.trim().replace(' ', '_');
            logger.debug("Constructed Wikipedia URL from name: {}", wikipediaUrl);
        }

        Document doc = fetchPage(wikipediaUrl);
        if (doc == null) {
            logger.warn("Could not fetch Wikipedia page for artist: {}", artist.getArtist_id());
            return albums;
        }

        albums.addAll(parseDiscographySections(doc, artist));

        if (albums.isEmpty()) {
            logger.debug("No albums found on main page, looking for a dedicated discography link.");
            Element discographyLink = findArtistSpecificDiscographyLink(doc, artist);
            if (discographyLink != null) {
                String href = discographyLink.attr("href");
                if (href.startsWith("/wiki/") || href.startsWith("https://en.wikipedia.org/wiki/")) {
                    String discographyUrl = href.startsWith("http") ? href : "https://en.wikipedia.org" + href;
                    logger.debug("Following artist-specific discography link: {}", discographyUrl);
                    doc = fetchPage(discographyUrl);
                    if (doc != null) {
                        albums.addAll(parseDiscographySections(doc, artist));
                    } else {
                        logger.warn("Could not fetch dedicated discography page for artist: {}", artist.getArtist_id());
                    }
                } else {
                    logger.debug("Skipping external discography link: {}", href);
                }
            } else {
                logger.debug("No artist-specific discography link found.");
            }
        }

        if (albums.isEmpty() && doc != null) {
            logger.debug("Still no albums found, trying a limited fallback.");
            albums.addAll(fallbackParse(doc, artist));
        }

        logger.debug("Total albums found for artist {}: {}", artist.getArtist_id(), albums.size());
        return albums;
    }

    // ---------- Find artist-specific discography link ----------
    private Element findArtistSpecificDiscographyLink(Document doc, Artist artist) {
        String artistName = artist.getArtist_name().toLowerCase();
        String artistSurname = artist.getArtist_surname().toLowerCase();
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String href = link.attr("href").toLowerCase();
            if (!href.contains("/wiki/") && !href.contains("en.wikipedia.org/wiki/")) continue;
            String text = link.text().toLowerCase();
            if (text.contains("discography") &&
                (text.contains(artistName) || text.contains(artistSurname)) &&
                (href.contains("discography") || href.contains("_discography"))) {
                return link;
            }
        }
        return null;
    }

    // ---------- Page fetching ----------
    private Document fetchPage(String url) {
        logger.debug("Fetching page: {}", url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();
            logger.debug("Page fetched successfully: {}", url);
            logger.debug("Full HTML of {}:\n{}", url, doc.html()); // DEBUG only
            return doc;
        } catch (IOException e) {
            logger.warn("Failed to fetch page: {} - {}", url, e.getMessage());
            return null;
        }
    }

    // ---------- Parse discography sections ----------
    private List<AlbumRawData> parseDiscographySections(Document doc, Artist artist) {
        List<AlbumRawData> albums = new ArrayList<>();
        Elements headings = doc.select("h2, h3");
        logger.debug("Found {} headings to scan for discography.", headings.size());

        for (Element heading : headings) {
            String headingText = heading.text().toLowerCase().trim();
            if (!headingText.contains("discography") && !headingText.contains("albums")) {
                continue;
            }

            logger.debug("Found discography section: '{}'", heading.text());

            boolean isMain = headingText.contains("studio")
                    || headingText.contains("as leader")
                    || headingText.contains("as sole leader")
                    || headingText.contains("albums");
            if (!isMain && !headingText.contains("live")
                    && !headingText.contains("compilation")
                    && !headingText.contains("sideman")) {
                isMain = true;
            }
            logger.debug("Heading '{}' -> isMain={}", heading.text(), isMain);

            Element sectionContent = getSectionContent(heading);
            if (sectionContent == null) {
                logger.debug("No content found for heading: {}", heading.text());
                continue;
            }

            // --- Parse unordered lists ---
            for (Element ul : sectionContent.select("ul")) {
                for (Element li : ul.select("li")) {
                    AlbumRawData data = parseListItem(li, artist);
                    if (data != null) {
                        data.setMain(isMain);
                        albums.add(data);
                        logger.debug("Added album from list: '{}' (year={})", data.getTitle(), data.getYear());
                    }
                }
            }

            // --- Parse tables ---
            for (Element table : sectionContent.select("table.wikitable")) {
                int titleColumnIndex = findTitleColumnIndex(table);
                if (titleColumnIndex == -1) {
                    titleColumnIndex = findColumnWithLinks(table);
                }
                if (titleColumnIndex == -1) {
                    titleColumnIndex = 0;
                }
                
                int yearColumnIndex = findYearColumnIndex(table); 

                for (Element row : table.select("tr")) {
                    if (row.select("th").isEmpty()) {
                        // Skip metadata rows
                        boolean isMetadataRow = false;
                        for (Element cell : row.select("td")) {
                            String cellText = cell.text().trim().toLowerCase();
                            if (cellText.matches("^(label|released|recorded|riaa|format)\\s*[:.]?.*")) {
                                isMetadataRow = true;
                                break;
                            }
                        }
                        if (isMetadataRow) {
                            continue;
                        }

                        Elements cells = row.select("td");
                        if (cells.size() > titleColumnIndex) {
                            Element titleCell = cells.get(titleColumnIndex);
                            
                            // NEW: if the title cell has no year, try the year column
                            String raw = titleCell.text().trim();
                            if (yearColumnIndex != -1 && cells.size() > yearColumnIndex
                                    && !raw.matches(".*\\b(19|20)\\d{2}\\b.*")) {
                                String y = cells.get(yearColumnIndex).text().trim();
                                if (y.matches(".*\\b(19|20)\\d{2}\\b.*")) {
                                    raw = y + ": " + raw;      // synthesise "1955: Ahmad Jamal Plays"
                                }
                            }
                            
                            if (!raw.matches(".*\\b(19|20)\\d{2}\\b.*")) {
                                for (Element c : cells) {
                                    String t = c.text().trim();
                                    if (t.matches("\\s*(19|20)\\d{2}\\s*")) {
                                        raw = t + ": " + raw;
                                        break;
                                    }
                                }
                            }
                            
                            AlbumRawData data = extractFromRaw(raw, titleCell.html(), artist);
                            if (data != null) {
                                data.setMain(isMain);
                                albums.add(data);
                                logger.debug("Added album from table: '{}' (year={})",
                                             data.getTitle(), data.getYear());
                            }                           
                        }
                    }
                }
            }
        }
        return albums;
    }

    // ---------- Get the content block following a heading ----------
    private Element getSectionContent(Element heading) {
        Element container = heading.parent();
        Element startFrom;
        if (container != null && container.tagName().equals("div") && container.hasClass("mw-heading")) {
            startFrom = container.nextElementSibling();
        } else {
            startFrom = heading.nextElementSibling();
        }

        if (startFrom == null) {
            return null;
        }

        int headingLevel = Integer.parseInt(heading.tagName().substring(1));
        Element section = new Element("div");
        Element current = startFrom;

        while (current != null) {
            if (current.tagName().matches("h[23]")) {
                int nextLevel = Integer.parseInt(current.tagName().substring(1));
                if (nextLevel <= headingLevel) {
                    break;
                }
            }
            section.appendChild(current.clone());
            current = current.nextElementSibling();
        }
        return section;
    }

    // ---------- Table column detection ----------
    private int findTitleColumnIndex(Element table) {
        Elements headers = table.select("th");
        for (int i = 0; i < headers.size(); i++) {
        	String h = headers.get(i).text().trim().toLowerCase();
        	if (h.equals("title") || h.equals("album")) return i;
        }
        for (int i = 0; i < headers.size(); i++) {
            String lower = headers.get(i).text().toLowerCase();
            if (lower.contains("title") && !lower.contains("details") && !lower.contains("release")) {
                return i;
            }
        }
        return -1;
    }

    private int findColumnWithLinks(Element table) {
        Elements rows = table.select("tr");
        if (rows.isEmpty()) return -1;
        int maxCols = 0;
        for (Element row : rows) {
            maxCols = Math.max(maxCols, row.select("td").size());
        }
        if (maxCols == 0) return -1;
        int[] linkCounts = new int[maxCols];
        for (Element row : rows) {
            Elements cells = row.select("td");
            for (int i = 0; i < cells.size() && i < maxCols; i++) {
            	int score = 0;
            	if (!cells.get(i).select("a[href*=/wiki/]").isEmpty()) score += 1;
            	if (!cells.get(i).select("i").isEmpty())                 score += 5;
            	linkCounts[i] += score;
            }
        }
        int bestCol = -1, bestCount = 0;
        for (int i = 0; i < linkCounts.length; i++) {
            if (linkCounts[i] > bestCount) {
                bestCount = linkCounts[i];
                bestCol = i;
            }
        }
        return bestCol;
    }

    // ========== CORE PARSING ==========

    /**
     * Core extraction: given raw text, returns an AlbumRawData with title, year, label, etc.
     * Handles leading "YYYY:" format, nested parentheses, multiple years, and label extraction.
     */
    private AlbumRawData extractFromRaw(String raw, String html, Artist artist) {
    	if (raw == null || raw.isBlank() || isNonAlbumLine(raw)) {
            return null;
        }

        String title = null;
        String year = null;
        String label = null;
        String released = null;      
        String wikidataId = null;
        String wikipediaUrl = null;

        // ---- Step 1: Find parenthetical groups that contain a year ----
        Pattern parenGroup = Pattern.compile("\\(([^)]*)\\)");
        Matcher pm = parenGroup.matcher(raw);
        String lastYearParen = null;
        int lastYearStart = -1, lastYearEnd = -1;

        while (pm.find()) {
            String inside = pm.group(1);
            Matcher yearMatcher = Pattern.compile("\\b(19|20)\\d{2}\\b").matcher(inside);
            if (yearMatcher.find()) {
                // Skip groups that contain "recorded" or "rec."
                if (!inside.toLowerCase().contains("recorded") && !inside.toLowerCase().contains("rec.")) {
                    lastYearParen = pm.group(0);
                    lastYearStart = pm.start();
                    lastYearEnd = pm.end();
                }
            }
        }

        // If we found a valid parenthetical group, extract year and title
        if (lastYearParen != null) {
            Matcher yearInside = Pattern.compile("\\b(19|20)\\d{2}\\b").matcher(lastYearParen);
            if (yearInside.find()) {
                year = yearInside.group();
            }
            // Title is everything before the opening parenthesis of this group
            title = raw.substring(0, lastYearStart).trim();

            // ---- Extract label from the same parentheses ----
            String inside = lastYearParen.substring(1, lastYearParen.length() - 1);
            for (String kw : LABEL_KEYWORDS) {
                if (inside.toLowerCase().contains(kw)) {
                    // Split on comma or semicolon, take first part
                    String[] parts = inside.split("[,;]");
                    if (parts.length > 0) {
                        String candidate = parts[0].trim();
                        // Ensure it's not just a number and not empty
                        if (!candidate.matches("\\d+") && !candidate.isBlank()) {
                            label = candidate;
                        }
                    }
                    break;
                }
            }
        }

        // ---- Step 2: If no parenthetical year, try leading "YYYY:" pattern ----
        if (year == null) {
            Pattern leadingYear = Pattern.compile("^(19|20)(\\d{2})\\s*[:–-]\\s*");
            Matcher ly = leadingYear.matcher(raw);
            if (ly.find()) {
                year = ly.group(1) + ly.group(2);
                title = raw.substring(ly.end()).trim();
            }
        }

        // ---- Step 3: Fallback: find the last standalone 4-digit year (anywhere) ----
        if (year == null) {
            Pattern standalone = Pattern.compile("\\b(19|20)\\d{2}\\b");
            Matcher sm = standalone.matcher(raw);
            String lastYear = null;
            int lastPos = -1;
            while (sm.find()) {
                int start = sm.start();
                String prefix = (start >= 7) ? raw.substring(start - 7, start).toLowerCase() : "";
                if (prefix.contains("recorded") || prefix.contains("rec.")) {
                    continue;
                }
                lastYear = sm.group();
                lastPos = sm.start();
            }
            if (lastYear != null) {
                year = lastYear;
                title = raw.substring(0, lastPos).trim();
                // Remove trailing punctuation
                title = title.replaceAll("\\s*[,;:–-]\\s*$", "");
            }
        }

        // ---- If still no year, give up ----
        if (year == null) {
            title = raw.trim();          // <-- keep the whole raw as title
            }

        // ---- Clean the title ----
        // 1. Remove date/list prefixes that Wikipedia uses:
        //    "1956.01: " or "1973: " (4-digit year, optional .MM)
        //    "1922–47: " (year ranges)
        //    "73: " (2-digit year)
        //    Note: require whitespace after separator to avoid "3:10" (no space) being matched.
        title = title.replaceFirst("^\\s*\\d{4}(?:\\.\\d{2})?\\s*[:–-]\\s+", "");
        title = title.replaceFirst("^\\s*\\d{4}[-–]\\d{2,4}\\s*[:–-]\\s+", "");
        title = title.replaceFirst("^\\s*\\d{1,2}\\s*[:–-]\\s+", "");

        // 2. Strip format annotations at the end, e.g. "(8xCD)", "(4xCD)", "(CD)", "(LP)"
        title = title.replaceAll("\\s*\\(\\d+[xX]CD\\)\\s*$", "");
        title = title.replaceAll("\\s*\\(CD\\)\\s*$", "");
        title = title.replaceAll("\\s*\\(LP\\)\\s*$", "");

        // 3. Remove parentheses containing "recorded" or "rec."
        title = title.replaceAll("(?i)\\s*\\([^)]*?(?:recorded|rec\\.)[^)]*?\\)", "");

        // 4. Remove trailing extra descriptive text (reissued, contains, etc.)
        title = title.replaceAll("(?i)\\s+(reissued|contains|recorded|including)\\s+.*$", "");
        title = title.replaceAll("\\s+[;–-]\\s+[a-z].*$", "");
//      ^^^^        ^^^^   require whitespace around the dash
        
        // 5. Remove trailing punctuation
        title = title.replaceAll("\\s*[,;:–-]\\s*$", "");

        title = title.replaceAll("\\[\\s*(?:\\d+|[a-z]+|note[^\\]]*)\\s*\\]", "").trim();
        
        // 6. Trim
        title = title.trim();

        // If title is empty after cleaning, try to get it from a Wikipedia link
        if (title.isEmpty()) {
            Element link = Jsoup.parse(raw).selectFirst("a[href*=/wiki/]");
            if (link != null) {
                String linkText = link.text().trim();
                if (!LABEL_KEYWORDS.contains(linkText.toLowerCase()) && linkText.length() > 2) {
                    title = linkText;
                    String href = link.attr("href");
                    int wikiIdx = href.indexOf("/wiki/");
                    if (wikiIdx >= 0) {
                        String pageTitle = href.substring(wikiIdx + "/wiki/".length());
                        if (pageTitle.contains("#")) pageTitle = pageTitle.substring(0, pageTitle.indexOf('#'));
                        pageTitle = java.net.URLDecoder.decode(pageTitle, StandardCharsets.UTF_8);
                        wikipediaUrl = "https://en.wikipedia.org/wiki/" + pageTitle;
                        wikidataId = getWikidataIdFromPageTitle(pageTitle);
                    }
                }
            }
        }

        // If still empty, fallback to raw text up to where we found the year
        if (title.isEmpty() && lastYearStart > 0) {
            title = raw.substring(0, lastYearStart).trim();
        }

        // ---- Final plausibility ----
        if (title == null || title.length() < 3 || !isPlausibleTitle(title, false)) {
            return null;
        }

     // Look up Wikipedia URL and Wikidata ID from the raw HTML
     // Look up Wikipedia URL and Wikidata ID from the raw HTML
        if (wikipediaUrl == null && html != null && !html.isBlank()) {
            Document liDoc = Jsoup.parse(html);

            // Prefer a link inside an <i> tag (album titles are italicised)
            Element link = liDoc.selectFirst("i a[href*=/wiki/]");
            if (link == null) {
                // Fallback: first wiki link, but skip obvious label/artist links
                for (Element cand : liDoc.select("a[href*=/wiki/]")) {
                    String text = cand.text().toLowerCase();
                    if (LABEL_KEYWORDS.contains(text)) continue;
                    link = cand;
                    break;
                }
            }

            if (link != null) {
                String href = link.attr("href");

                // href can be "/wiki/X" or "https://en.wikipedia.org/wiki/X"
                String pageTitle = null;
                int wikiIdx = href.indexOf("/wiki/");
                if (wikiIdx >= 0) {
                    pageTitle = href.substring(wikiIdx + "/wiki/".length());
                }

                if (pageTitle != null) {
                    int hashIdx = pageTitle.indexOf('#');
                    if (hashIdx > 0) pageTitle = pageTitle.substring(0, hashIdx);
                    // URL-decode (Parsoid sometimes emits %27 etc.)
                    pageTitle = java.net.URLDecoder.decode(pageTitle, StandardCharsets.UTF_8);

                    wikipediaUrl = "https://en.wikipedia.org/wiki/" + pageTitle;
                    wikidataId   = getWikidataIdFromPageTitle(pageTitle);
                }
            }
        }
        
     // ---- Collect min/max year and override year / set released ----
        int[] minMax = extractMinMaxYears(raw);
        if (minMax != null) {
            year     = String.valueOf(minMax[0]);   // earliest year
            released = String.valueOf(minMax[1]);   // latest year
        }
        
        AlbumRawData data = new AlbumRawData();
        data.setTitle(title);
        data.setYear(year);
        data.setReleased(released); 
        data.setLabel(label);   // extracted label (may be null)
        data.setWikidataId(wikidataId);
        data.setWikipediaUrl(wikipediaUrl);
        return data;
    }

    // ---------- Parse list item ----------
    private AlbumRawData parseListItem(Element li, Artist artist) {
    	String raw = li.text().trim();
    	if (raw.isEmpty()) return null;
    	String html = li.html();                       // <-- new
    	AlbumRawData data = extractFromRaw(raw, html, artist);
        if (data != null) {
            logger.debug("Parsed list item: '{}' -> '{}' ({})", raw, data.getTitle(), data.getYear());
        } else {
            logger.debug("Skipped list item: '{}'", raw);
        }
        return data;
    }

    // ---------- Parse table cell ----------
    private AlbumRawData parseAlbumCell(Element cell, Artist artist) {
    	String raw = cell.text().trim();
    	if (raw.isEmpty()) return null;
    	String html = cell.html();                     // <-- new
    	AlbumRawData data = extractFromRaw(raw, html, artist);
        if (data != null) {
            logger.debug("Parsed table cell: '{}' -> '{}' ({})", raw, data.getTitle(), data.getYear());
        } else {
            logger.debug("Skipped table cell: '{}'", raw);
        }
        return data;
    }

    // ---------- Fallback ----------
    private List<AlbumRawData> fallbackParse(Document doc, Artist artist) {
        List<AlbumRawData> albums = new ArrayList<>();
        if (doc == null) return albums;

        Element content = doc.selectFirst("div.mw-parser-output");
        if (content == null) return albums;

        for (Element child : content.children()) {
            if (child.tagName().matches("h[23]")) {
                String text = child.text().toLowerCase();
                if (!text.contains("discography") && !text.contains("albums")) {
                    break;
                }
            }
            if (child.tagName().equals("ul")) {
                for (Element li : child.select("li")) {
                    String raw = li.text().trim();
                    if (raw.isEmpty() || isNonAlbumLine(raw)) continue;
                    if (raw.matches(".*\\b\\d{4}\\b.*") && !raw.matches("^\\d+\\.\\d+.*")) {
                    	AlbumRawData data = extractFromRaw(raw, li.html(), artist);
                        if (data != null) {
                            data.setMain(false);
                            albums.add(data);
                            logger.debug("Added album from fallback list: '{}' (year={})", data.getTitle(), data.getYear());
                        }
                    }
                }
            }
        }
        return albums;
    }

    // ---------- Helpers ----------

    private boolean isNonAlbumLine(String text) {
        String lower = text.toLowerCase();
        for (String keyword : NON_ALBUM_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        if (lower.matches("^(released|recorded|label|riaa|format|disc|track)\\s*[:.]?.*")) {
            return true;
        }
        String trimmed = text.trim();
        if (LABEL_KEYWORDS.contains(trimmed.toLowerCase())) {
            return true;
        }
        if (text.split("\\s+").length < 2) {
            return true;
        }
        return false;
    }

    // ---------- Plausibility (lenient) ----------
    private boolean isPlausibleTitle(String title, boolean fromLink) {
        if (title == null || title.isBlank()) return false;
        if (title.length() < 3) return false;
        if (title.matches("\\d{4}")) return false;
        if (LABEL_KEYWORDS.contains(title.toLowerCase())) return false;
        if (title.replaceAll("[^a-zA-Z0-9]", "").isEmpty()) return false;
        return true;
    }

    // ---------- Wikidata lookup ----------
    private String getWikidataIdFromPageTitle(String pageTitle) {
    	String encoded = URLEncoder.encode(pageTitle.replace('_', ' '), StandardCharsets.UTF_8);
    	String url = "https://en.wikipedia.org/w/api.php?action=query&titles=" + encoded +
    	        "&prop=pageprops&format=json";
        try {
            var response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> query = (Map<String, Object>) body.get("query");
                Map<String, Object> pages = (Map<String, Object>) query.get("pages");
                for (Object pageObj : pages.values()) {
                    Map<String, Object> page = (Map<String, Object>) pageObj;
                    Map<String, Object> pageprops = (Map<String, Object>) page.get("pageprops");
                    if (pageprops != null && pageprops.containsKey("wikibase_item")) {
                        return (String) pageprops.get("wikibase_item");
                    }
                }
            }
        } catch (Exception e) {
            logger.trace("Failed to get Wikidata ID for '{}'", pageTitle, e);
        }
        return null;
    }
    
    /**
     * Scans the raw text and returns [minYear, maxYear] of all plausible 4-digit years,
     * or null if none found. Skips years preceded by "recorded" or "rec.".
     */
    private int[] extractMinMaxYears(String raw) {
        if (raw == null) return null;
        Pattern p = Pattern.compile("\\b(19|20)\\d{2}\\b");
        Matcher m = p.matcher(raw);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        boolean found = false;

        while (m.find()) {
            int start = m.start();
            String prefix = (start >= 7) ? raw.substring(start - 7, start).toLowerCase() : "";
            if (prefix.contains("recorded") || prefix.contains("rec.")) continue;

            int y = Integer.parseInt(m.group());
            if (y < min) min = y;
            if (y > max) max = y;
            found = true;
        }
        return found ? new int[] { min, max } : null;
    }

    private int findYearColumnIndex(Element table) {
        Elements headers = table.select("th");
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i).text().trim().toLowerCase();
            if (h.equals("year") || h.contains("year")) return i;
        }
        return -1;
    }
    
    // ---------- Inner class ----------
    public static class AlbumRawData {
        private String title;
        private String year;
        private boolean isMain;
        private String released;    
        private String label;          // NEW: extracted label
        private String wikidataId;
        private String wikipediaUrl;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getYear() { return year; }
        public void setYear(String year) { this.year = year; }

        public boolean isMain() { return isMain; }
        public void setMain(boolean main) { isMain = main; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getWikidataId() { return wikidataId; }
        public void setWikidataId(String wikidataId) { this.wikidataId = wikidataId; }

        public String getWikipediaUrl() { return wikipediaUrl; }
        public void setWikipediaUrl(String wikipediaUrl) { this.wikipediaUrl = wikipediaUrl; }
        
        public String getReleased() { return released; }             // <-- ADD
        public void setReleased(String released) { this.released = released; }  // <-- ADD
    }
}