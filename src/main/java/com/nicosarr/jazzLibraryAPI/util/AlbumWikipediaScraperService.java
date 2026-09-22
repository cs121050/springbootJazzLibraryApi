package com.nicosarr.jazzLibraryAPI.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class AlbumWikipediaScraperService {

    private static final Logger logger = LoggerFactory.getLogger(AlbumWikipediaScraperService.class);

    private final ObjectMapper mapper = new ObjectMapper();
    
    // Respect the Wiki API: identify ourselves with contact info (see
    // https://meta.wikimedia.org/wiki/User-Agent_policy).
    private static final String USER_AGENT =
            "JazzLibrary/1.0 (https://github.com/nicosarr/jazzLibraryAPI; nicko.sarr@gmail.com)";

    // 5 req/s max. Wikimedia asks for at most ~200 req/s under normal load; we
    // stay far below that to be a good citizen.
    private static final long MIN_GAP_MS = 200;
    private final AtomicLong lastFetch = new AtomicLong(0);

    private static final Pattern PERSONNEL_LINE =
            Pattern.compile("^(.+?)\\s+[–—]\\s+(.+)$");
    private static final Pattern TRACK_LENGTH =
            Pattern.compile("^(\\d{1,2}:)?\\d{1,2}:\\d{2}$");

    private static final Set<String> ALLOWED_SECTIONS = Set.of(
    	    "background",
    	    "reissues",
    	    "critical reception",
    	    "reception",
    	    "composition and recording",
    	    "recording and music",
    	    "recording",
    	    "composition",
    	    "release",
    	    "accolades and legacy",
    	    "accolades",
    	    "legacy"
    	);
    
    /** Per-section character cap — keeps a single section from blowing up the row. */
    private static final int MAX_SECTION_LEN = 20_000;
    // Hard cap on stored article text (safety; column is TEXT but let's not
    // bloat rows with 500 KB of prose).
    private static final int MAX_ARTICLE_TEXT = 60_000;

    // ---------------------------------------------------------------
    public ScrapedAlbumData scrape(String wikipediaUrl) {
        if (wikipediaUrl == null || wikipediaUrl.isBlank()) {
            logger.debug("Scrape SKIP (null/blank url)");
            return null;
        }

        logger.debug("Scrape START '{}'", wikipediaUrl);

        Document doc = fetchPage(wikipediaUrl);
        if (doc == null) {
            logger.warn("Scrape FAILED fetch '{}'", wikipediaUrl);
            return null;
        }

        logger.debug("Scrape FETCHED ok title='{}' bytes={}",
                     doc.title(), doc.html().length());

        ScrapedAlbumData data = new ScrapedAlbumData();
        data.articleText = extractArticleText(doc);
        data.wikidataId = extractWikibaseItemId(doc);
        logger.debug("Scrape WIKIDATA '{}': qid={}", wikipediaUrl, data.wikidataId);
        data.personnel   = scrapePersonnel(doc);
        data.tracklist   = scrapeTracklist(doc);

        logger.debug("Scrape PARSED '{}': articleText={} chars, personnel={}, tracks={}",
                     wikipediaUrl,
                     data.articleText == null ? 0 : data.articleText.length(),
                     data.personnel.size(),
                     data.tracklist.size());

        if (data.personnel.isEmpty()
                && data.tracklist.isEmpty()
                && (data.articleText == null || data.articleText.isBlank())
                && data.wikidataId == null) {  
            logger.warn("Scrape DROPPED '{}' — nothing extracted (no personnel, no tracks, no article text)",
                        wikipediaUrl);
            return null;
        }
        return data;
    }

    // ---------------------------------------------------------------
    private Document fetchPage(String url) {
        long now  = System.currentTimeMillis();
        long wait = MIN_GAP_MS - (now - lastFetch.get());
        if (wait > 0) {
            logger.trace("Throttling {} ms", wait);
            try { Thread.sleep(wait); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        lastFetch.set(System.currentTimeMillis());

        try {
        	String encoded = safeUrl(url);
        	logger.debug("Jsoup GET -> '{}' (raw was '{}')", encoded, url);
        	Document doc = Jsoup.connect(encoded)
        	        .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en")
                    .timeout(15000)
                    .maxBodySize(0)
                    .get();
            logger.debug("Jsoup GET <- '{}' status=OK title='{}'", url, doc.title());
            return doc;
        } catch (IOException e) {
            logger.warn("Jsoup GET FAILED '{}': {} — {}", url, e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }
	
	/**
	 * Returns a JSON object mapping section headings → their paragraph text,
	 * e.g. {"Intro":"...","Background":"...","Critical reception":"..."}.
	 * Only sections listed in ALLOWED_SECTIONS are kept; everything else
	 * (Track listing, Personnel, References, See also, External links,
	 * infobox, navbox, ...) is ignored.
	 *
	 * Handles both the modern Parsoid layout (<section> wrappers) and the
	 * legacy flat layout (h2/h3 siblings).
	 */
	private String extractArticleText(Document doc) {
	    Element body = doc.selectFirst("div.mw-parser-output");
	    if (body == null) body = doc.body();
	    if (body == null) return null;
	
	    // Preserve insertion order so the JSON reads Intro → Background → … as on the page.
	    LinkedHashMap<String, StringBuilder> sections = new LinkedHashMap<>();
	
	    // Collect top-level <section> children (Parsoid)
	    List<Element> topSections = new ArrayList<>();
	    for (Element c : body.children()) {
	        if ("section".equals(c.tagName())) topSections.add(c);
	    }
	
	    if (!topSections.isEmpty()) {
	        // ---------- Modern Parsoid layout ----------
	        for (Element sec : topSections) {
	            // Find the section heading (h2/h3 wrapped in .mw-heading, or bare h2/h3)
	            String heading = "Intro";
	            Element h = null;
	            for (Element c : sec.children()) {
	                if (c.hasClass("mw-heading")) {
	                    Element hh = c.selectFirst("h2, h3, h4");
	                    if (hh != null) { h = hh; break; }
	                } else if (c.tagName().matches("h[2-6]")) {
	                    h = c; break;
	                }
	            }
	            if (h != null) {
	                String t = h.text().trim();
	                if (!t.isEmpty()) heading = t;
	            }
	
	            boolean keepIntro = heading.equalsIgnoreCase("Intro");
	            boolean keepNamed = ALLOWED_SECTIONS.contains(heading.toLowerCase(Locale.ROOT));
	            if (!keepIntro && !keepNamed) continue;
	
	            StringBuilder sb = new StringBuilder();
	            for (Element c : sec.children()) {
	                if (!"p".equals(c.tagName())) continue;
	                String t = stripWikiMarkers(c.text());
	                if (t.isEmpty()) continue;
	                if (sb.length() > 0) sb.append('\n');
	                sb.append(t);
	                if (sb.length() > MAX_SECTION_LEN) break;
	            }
	            if (sb.length() > 0) sections.put(heading, sb);
	        }
	    } else {
	        // ---------- Legacy flat layout ----------
	        String current = "Intro";
	        for (Element c : body.children()) {
	            if (c.tagName().matches("h[2-6]")) {
	                String t = c.text().trim();
	                current = t.isEmpty() ? "Intro" : t;
	                continue;
	            }
	            if (c.hasClass("mw-heading")) {
	                Element hh = c.selectFirst("h2, h3, h4");
	                if (hh != null) {
	                    String t = hh.text().trim();
	                    current = t.isEmpty() ? "Intro" : t;
	                }
	                continue;
	            }
	            if (!"p".equals(c.tagName())) continue;
	
	            boolean keepIntro = current.equalsIgnoreCase("Intro");
	            boolean keepNamed = ALLOWED_SECTIONS.contains(current.toLowerCase(Locale.ROOT));
	            if (!keepIntro && !keepNamed) continue;
	
	            String t = stripWikiMarkers(c.text());
	            if (t.isEmpty()) continue;
	            sections.computeIfAbsent(current, k -> new StringBuilder());
	            StringBuilder sb = sections.get(current);
	            if (sb.length() > 0) sb.append('\n');
	            sb.append(t);
	            if (sb.length() > MAX_SECTION_LEN) break;
	        }
	    }
	
	    if (sections.isEmpty()) return null;
	
	    // Build compact JSON: {"Intro":"…","Background":"…", …}
	    ObjectNode root = mapper.createObjectNode();
	    for (Map.Entry<String, StringBuilder> e : sections.entrySet()) {
	        String v = e.getValue().toString().trim();
	        if (!v.isEmpty()) root.put(e.getKey(), v);
	    }
	    if (root.isEmpty()) return null;
	    return root.toString();
	}
	
	/** Remove [1], [citation needed], [edit], etc. and collapse whitespace. */
	private String stripWikiMarkers(String t) {
	    if (t == null) return "";
	    t = t.replaceAll("\\[\\d+\\]", "");
	    t = t.replaceAll("\\[(?:citation needed|clarification needed|who\\?|when\\?|by whom\\?|according to whom\\?|edit)\\]", "");
	    t = t.replaceAll("\\s+", " ");
	    return t.trim();
	}

    // ===============================================================
    // PERSONNEL
    // ===============================================================
    private List<Map<String, Object>> scrapePersonnel(Document doc) {
        List<Map<String, Object>> out = new ArrayList<>();

        Element section = findSection(doc,
                "Personnel", "Credits", "Personnel and recording crew", "Musicians");
        if (section == null) {
            logger.trace("No personnel section found");
            return out;
        }

        String currentGroup = null;

        for (Element child : section.children()) {

            if (child.tagName().equals("dl")) {
                for (Element dt : child.select("dt")) {
                    String t = dt.text().trim();
                    if (!t.isEmpty() && !PERSONNEL_LINE.matcher(t).matches()) {
                        currentGroup = t;
                        logger.trace("Personnel sub-group: {}", currentGroup);
                    }
                }
                Elements dts = child.select("dt");
                Elements dds = child.select("dd");
                for (int i = 0; i < Math.min(dts.size(), dds.size()); i++) {
                    String name  = dts.get(i).text().trim();
                    String roles = dds.get(i).text().trim();
                    if (!name.isEmpty() && !roles.isEmpty()
                            && !PERSONNEL_LINE.matcher(name).matches()
                            && roles.length() > 1) {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("name", name);
                        p.put("roles", splitRoles(roles));
                        if (currentGroup != null) p.put("group", currentGroup);
                        out.add(p);
                    }
                }
            }

            if (child.tagName().equals("ul")) {
                for (Element li : child.select("li")) {
                    Element bold = li.selectFirst("b, strong");
                    String liText = li.text().trim();

                    if (bold != null && liText.equals(bold.text().trim())
                            && !PERSONNEL_LINE.matcher(liText).matches()) {
                        currentGroup = liText;
                        logger.trace("Personnel sub-group: {}", currentGroup);
                        continue;
                    }

                    Map<String, Object> person = parsePersonnelLine(liText);
                    if (person != null) {
                        if (currentGroup != null) person.put("group", currentGroup);
                        out.add(person);
                    }
                }
            }

            if (child.tagName().equals("p")) {
                Element bold = child.selectFirst("b, strong");
                if (bold != null && child.text().trim().equals(bold.text().trim())) {
                    currentGroup = bold.text().trim();
                }
            }
        }
        return out;
    }

    private Map<String, Object> parsePersonnelLine(String text) {
        if (text == null || text.isBlank()) return null;
        text = text.replaceAll("\\[\\d+\\]", "").trim();

        Matcher m = PERSONNEL_LINE.matcher(text);
        if (!m.matches()) {
            if (text.split("\\s+").length <= 4 && text.length() > 2) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", text);
                p.put("roles", Collections.emptyList());
                return p;
            }
            return null;
        }
        String name     = m.group(1).trim();
        String rolesRaw = m.group(2).trim();
        if (name.isEmpty() || rolesRaw.isEmpty()) return null;

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("roles", splitRoles(rolesRaw));
        return p;
    }

    private List<String> splitRoles(String raw) {
        List<String> roles = new ArrayList<>();
        for (String part : raw.split("\\s*[,;]\\s*")) {
            String r = part.trim();
            if (!r.isEmpty()) roles.add(r);
        }
        return roles;
    }

 // ===============================================================
 // TRACKLIST
 // ===============================================================
    
    private List<Map<String, Object>> scrapeTracklist(Document doc) {
        Element section = findSection(doc,
                "Track listing", "Track list", "Tracks", "Songs", "Tracklist");
        if (section == null) {
            logger.trace("No tracklist section found");
            return Collections.emptyList();
        }

        List<Map<String, Object>> out = new ArrayList<>();

        // 1. {{Track listing}} tables — may be several (Side one / Side two / Disc N)
        for (Element t : section.select("table.tracklist")) out.addAll(parseTrackTable(t));
        if (!out.isEmpty()) return renumber(out);

        // 2. Generic wikitables
        for (Element t : section.select("table.wikitable")) out.addAll(parseTrackTable(t));
        if (!out.isEmpty()) return renumber(out);

        // 3. Numbered lists (one or many, e.g. one per side)
        for (Element ol : section.select("ol")) out.addAll(parseTrackList(ol));
        if (!out.isEmpty()) return renumber(out);

        // 4. Definition lists — Sonny Meets Hawk! style
        for (Element dl : section.select("dl")) out.addAll(parseDlTracks(dl));

        return renumber(out);
    }
    
    /** <dl> list of "<dd>1. \"Title\" (Writer) – 5:13</dd>". */
    private List<Map<String, Object>> parseDlTracks(Element dl) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Element dd : dl.select("dd")) {
            String raw = dd.text().trim().replaceAll("\\[\\d+\\]", "");
            if (raw.isEmpty()) continue;

            Map<String, Object> track = new LinkedHashMap<>();

            // Leading number "1." / "1 " / "1)"
            java.util.regex.Matcher numM =
                    java.util.regex.Pattern.compile("^\\s*(\\d+)\\s*[\\.\\)]?\\s*").matcher(raw);
            if (numM.find()) {
                track.put("number", numM.group(1));
                raw = raw.substring(numM.end());
            } else {
                track.put("number", String.valueOf(out.size() + 1));
            }

            // Trailing length "– 5:13" or "- 5:13"
            java.util.regex.Matcher lenM =
                    java.util.regex.Pattern.compile("[–\\-]\\s*(\\d{1,2}:\\d{2})\\s*$").matcher(raw);
            if (lenM.find()) {
                track.put("length", lenM.group(1));
                raw = raw.substring(0, lenM.start()).trim();
            }

            // Parenthetical writers "(Jerome Kern, Otto Harbach)"
            java.util.regex.Matcher wrM =
                    java.util.regex.Pattern.compile("\\(([^)]+)\\)\\s*$").matcher(raw);
            if (wrM.find()) {
                track.put("writers", wrM.group(1).trim());
                raw = raw.substring(0, wrM.start()).trim();
            }

            // Whatever is left, de-quoted, is the title
            String title = raw.replaceAll("^[\"“”]+|[\"“”]+$", "").trim();
            if (title.isEmpty()) continue;
            track.put("title", title);

            out.add(track);
        }
        return out;
    }
    
    
    /** Renumber 1..N so duplicate numbers across sides/discs disappear. */
    private List<Map<String, Object>> renumber(List<Map<String, Object>> tracks) {
        int n = 0;
        for (Map<String, Object> t : tracks) t.put("number", String.valueOf(++n));
        return tracks;
    }
    
    
    private List<Map<String, Object>> parseTrackTable(Element table) {
        List<Map<String, Object>> out = new ArrayList<>();

        // ---- 1. Locate the header row: first row whose every cell is <th> ----
        Element headerRow = null;
        for (Element tr : table.select("tr")) {
            Elements cells = tr.children();
            if (cells.isEmpty()) continue;
            boolean allTh = true;
            for (Element c : cells) {
                if (!"th".equals(c.tagName())) { allTh = false; break; }
            }
            if (allTh && cells.size() >= 2) { headerRow = tr; break; }
        }
        if (headerRow == null) headerRow = table.selectFirst("tr");
        if (headerRow == null) return out;

        // ---- 2. Map column indices from the header row ----
        Elements headerCells = headerRow.children();
        int noIdx = -1, titleIdx = -1, lengthIdx = -1, writerIdx = -1;
        for (int i = 0; i < headerCells.size(); i++) {
            String h = headerCells.get(i).text().trim().toLowerCase();
            if (h.equals("no.") || h.equals("no") || h.equals("#") || h.equals("track"))
                noIdx = i;
            else if (h.contains("title"))
                titleIdx = i;
            else if (h.contains("length") || h.contains("time"))
                lengthIdx = i;
            else if (h.contains("writer") || h.contains("music")
                    || h.contains("lyrics") || h.contains("note"))
                writerIdx = i;
        }
        if (titleIdx == -1) titleIdx = 1;

        // ---- 3. Data rows ----
        boolean pastHeader = false;
        int trackNo = 0;
        for (Element row : table.select("tr")) {
            if (!pastHeader) {
                if (row == headerRow) pastHeader = true;
                continue;
            }
            Elements cells = row.children();
            if (cells.isEmpty()) continue;

            // Skip sub-headers (all <th>) and "Total length" rows.
            boolean allTh = true;
            for (Element c : cells) {
                if (!"th".equals(c.tagName())) { allTh = false; break; }
            }
            if (allTh) continue;
            String first = cells.first().text().trim().toLowerCase();
            if (first.startsWith("total") || first.contains("total length")) continue;

            if (cells.size() <= titleIdx) continue;

            trackNo++;
            Map<String, Object> track = new LinkedHashMap<>();

            // Number: {{Track listing}} puts it in <th scope="row">, but the
            // index-based access works regardless of td/th.
            if (noIdx != -1 && cells.size() > noIdx) {
                String n = cells.get(noIdx).text().trim();
                track.put("number", n.isEmpty() ? String.valueOf(trackNo) : n);
            } else {
                track.put("number", String.valueOf(trackNo));
            }

            String rawTitle = cells.get(titleIdx).text().trim()
                    .replaceAll("^[\"“”]+|[\"“”]+$", "")
                    .replaceAll("\\[\\d+\\]", "").trim();
            if (rawTitle.isEmpty()) continue;
            track.put("title", rawTitle);

            if (lengthIdx != -1 && cells.size() > lengthIdx) {
                String len = cells.get(lengthIdx).text().trim();
                if (TRACK_LENGTH.matcher(len).matches()) track.put("length", len);
            }
            if (writerIdx != -1 && cells.size() > writerIdx) {
                String w = cells.get(writerIdx).text().trim()
                        .replaceAll("\\[\\d+\\]", "")
                        .replaceAll("\\s+", " ").trim();
                if (!w.isEmpty()) track.put("writers", w);
            }
            out.add(track);
        }
        return out;
    }
 
 

private List<Map<String, Object>> parseTrackList(Element ol) {
    List<Map<String, Object>> out = new ArrayList<>();
    int n = 0;
    for (Element li : ol.select("li")) {
        n++;
        String raw = li.text().trim().replaceAll("\\[\\d+\\]", "");
        if (raw.isEmpty()) continue;

        Map<String, Object> track = new LinkedHashMap<>();
        track.put("number", String.valueOf(n));

        Matcher m = Pattern.compile("^(.*?)\\s*[\\(–-]\\s*(\\d{1,2}:\\d{2})\\)?\\s*$").matcher(raw);
        if (m.matches()) {
            track.put("title", cleanTitle(m.group(1)));
            track.put("length", m.group(2));
        } else {
            track.put("title", cleanTitle(raw));
        }
        out.add(track);
    }
    return out;
}

private String safeUrl(String raw) {
    if (raw == null) return null;

    // 1. Try the strict parser first. If it accepts the URL, it is already
    //    valid — return it unchanged. This preserves any existing %XX.
    try {
        return new java.net.URI(raw).toASCIIString();
    } catch (java.net.URISyntaxException ignored) {
        // fall through
    }

    // 2. Strict parse failed. Escape only the characters that are illegal
    //    in a URI but that are likely to appear in a Wikipedia article title.
    //    Leave '%' alone — it is the marker for an already-encoded byte.
    String escaped = raw
            .replace(" ", "%20")
            .replace("\"", "%22")
            .replace("<",  "%3C")
            .replace(">",  "%3E")
            .replace("[",  "%5B")
            .replace("]",  "%5D")
            .replace("{",  "%7B")
            .replace("}",  "%7D")
            .replace("|",  "%7C")
            .replace("\\", "%5C")
            .replace("^",  "%5E")
            .replace("`",  "%60");

    try {
        return new java.net.URI(escaped).toASCIIString();
    } catch (Exception e) {
        logger.warn("Could not normalise URL '{}': {}", raw, e.getMessage());
        return raw;
    }
}

    private String cleanTitle(String s) {
        return s.replaceAll("^[\"“”]+|[\"“”]+$", "").trim();
    }

    // ===============================================================
    // SECTION FINDER
    // ===============================================================
    private Element findSection(Document doc, String... keywords) {
        Elements headings = doc.select("h2, h3, h4");
        for (Element h : headings) {
            String text = h.text().toLowerCase(Locale.ROOT).trim();
            boolean match = false;
            for (String kw : keywords) {
                // space-insensitive: "Track list" ≡ "Tracklist"
                if (text.contains(kw.toLowerCase(Locale.ROOT))
                        || text.replaceAll("\\s+", "").contains(
                           kw.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""))) {
                    match = true;
                    break;
                }
            }
            if (!match) continue;

            Element content = getSectionContent(h);
            if (content != null && !content.children().isEmpty()) return content;
        }
        return null;
    }

    private Element getSectionContent(Element heading) {
    Element container = heading.parent();
    Element startFrom = (container != null && container.tagName().equals("div")
                         && container.hasClass("mw-heading"))
            ? container.nextElementSibling()
            : heading.nextElementSibling();
    if (startFrom == null) return null;

    int level = Integer.parseInt(heading.tagName().substring(1));
    Element section = new Element("div");
    Element cur = startFrom;

    while (cur != null) {
        // A sibling <section> belongs to us unless it starts a new top-level heading.
        if ("section".equals(cur.tagName())) {
            Element innerHead = findFirstHeading(cur);
            if (innerHead != null) {
                int innerLevel = Integer.parseInt(innerHead.tagName().substring(1));
                if (innerLevel <= level) break;
            }
            section.appendChild(cur.clone());
            cur = cur.nextElementSibling();
            continue;
        }

        if (cur.tagName().matches("h[2-6]")) {
            int nextLevel = Integer.parseInt(cur.tagName().substring(1));
            if (nextLevel <= level) break;
        }
        section.appendChild(cur.clone());
        cur = cur.nextElementSibling();
    }
    return section;
}

    
    
    
private Element findFirstHeading(Element sec) {
    for (Element c : sec.children()) {
        if (c.hasClass("mw-heading")) {
            Element h = c.selectFirst("h2, h3, h4, h5, h6");
            if (h != null) return h;
        } else if (c.tagName().matches("h[2-6]")) {
            return c;
        }
    }
    return null;
}


private static final Pattern WIKIBASE_ITEM =
Pattern.compile("\"wgWikibaseItemId\"\\s*:\\s*\"(Q\\d+)\"");

/** Reads the Q-id MediaWiki embeds in every page it renders. */
private String extractWikibaseItemId(Document doc) {
for (Element script : doc.select("script")) {
String data = script.data();
if (data == null || !data.contains("wgWikibaseItemId")) continue;
Matcher m = WIKIBASE_ITEM.matcher(data);
if (m.find()) return m.group(1);
}
return null;
}

    // ===============================================================
    public static class ScrapedAlbumData {
        public String articleText;                                              // → wikipedia_data
        public String wikidataId;  
        public List<Map<String, Object>> personnel = Collections.emptyList();   // → extra_artists
        public List<Map<String, Object>> tracklist = Collections.emptyList();   // → tracklist
    }
}