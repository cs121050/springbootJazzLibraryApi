package com.nicosarr.jazzLibraryAPI.SyncModel;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nicosarr.jazzLibraryAPI.Album.AlbumDTO;
import com.nicosarr.jazzLibraryAPI.Album.AlbumRep;
import com.nicosarr.jazzLibraryAPI.AlbumContainsArtist.AlbumContainsArtistDTO;
import com.nicosarr.jazzLibraryAPI.AlbumContainsArtist.AlbumContainsArtistRep;
import com.nicosarr.jazzLibraryAPI.Artist.ArtistDTO;
import com.nicosarr.jazzLibraryAPI.Artist.ArtistRep;
import com.nicosarr.jazzLibraryAPI.Duration.DurationDTO;
import com.nicosarr.jazzLibraryAPI.Duration.DurationRep;
import com.nicosarr.jazzLibraryAPI.Instrument.InstrumentDTO;
import com.nicosarr.jazzLibraryAPI.Instrument.InstrumentRep;
import com.nicosarr.jazzLibraryAPI.Song.SongDTO;          // NEW
import com.nicosarr.jazzLibraryAPI.Song.SongRep;          // NEW
import com.nicosarr.jazzLibraryAPI.Type.TypeDTO;
import com.nicosarr.jazzLibraryAPI.Type.TypeRep;
import com.nicosarr.jazzLibraryAPI.Video.VideoDTO;
import com.nicosarr.jazzLibraryAPI.Video.VideoRep;
import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtistDTO;
import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtistRep;

@RestController
@RequestMapping("sync")
public class SyncCntr {

    private static final int DEFAULT_LIMIT = 500;
    private static final int MAX_LIMIT     = 20000;

    @Autowired private AlbumRep                albumRep;
    @Autowired private ArtistRep               artistRep;
    @Autowired private VideoRep                videoRep;
    @Autowired private TypeRep                 typeRep;
    @Autowired private DurationRep             durationRep;
    @Autowired private InstrumentRep           instrumentRep;
    @Autowired private SongRep                 songRep;              // NEW
    @Autowired private AlbumContainsArtistRep  albumContainsArtistRep;
    @Autowired private VideoContainsArtistRep  videoContainsArtistRep;

    // ---------- helpers ----------
    private static int clampLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private static <T> SyncPage<T> buildPage(List<T> rows, int limit,
                                             java.util.function.Function<T, String> cursorOf) {
        boolean hasMore = rows.size() > limit;
        if (hasMore) rows = rows.subList(0, limit);
        String next = rows.isEmpty() || !hasMore ? null : cursorOf.apply(rows.get(rows.size() - 1));
        return new SyncPage<>(rows, next, hasMore);
    }

    // ---------- simple tables ----------

    @GetMapping("/albums")
    public SyncPage<AlbumDTO> syncAlbums(
            @RequestParam(defaultValue = "0") long cursor,
            @RequestParam(defaultValue = "500") int limit) {
        int l = clampLimit(limit);
        List<AlbumDTO> rows = albumRep.retrievePageAfterAlbumId(cursor, l + 1);
        return buildPage(rows, l, d -> String.valueOf(d.getAlbum_id()));
    }

    @GetMapping("/artists")
    public SyncPage<ArtistDTO> syncArtists(
            @RequestParam(defaultValue = "0") long cursor,
            @RequestParam(defaultValue = "1000") int limit) {
        int l = clampLimit(limit);
        List<ArtistDTO> rows = artistRep.retrievePageAfterArtistId(cursor, l + 1);
        return buildPage(rows, l, d -> String.valueOf(d.getArtist_id()));
    }

    @GetMapping("/videos")
    public SyncPage<VideoDTO> syncVideos(
            @RequestParam(defaultValue = "0") long cursor,
            @RequestParam(defaultValue = "4000") int limit) {
        int l = clampLimit(limit);
        List<VideoDTO> rows = videoRep.retrievePageAfterVideoId(cursor, l + 1);
        return buildPage(rows, l, d -> String.valueOf(d.getVideo_id()));
    }

    @GetMapping("/types")
    public SyncPage<TypeDTO> syncTypes(
            @RequestParam(defaultValue = "0") long cursor,
            @RequestParam(defaultValue = "500") int limit) {
        int l = clampLimit(limit);
        List<TypeDTO> rows = typeRep.retrievePageAfterTypeId(cursor, l + 1);
        return buildPage(rows, l, d -> String.valueOf(d.getType_id()));
    }

    @GetMapping("/durations")
    public SyncPage<DurationDTO> syncDurations(
            @RequestParam(defaultValue = "0") long cursor,
            @RequestParam(defaultValue = "500") int limit) {
        int l = clampLimit(limit);
        List<DurationDTO> rows = durationRep.retrievePageAfterDurationId(cursor, l + 1);
        return buildPage(rows, l, d -> String.valueOf(d.getDuration_id()));
    }

    @GetMapping("/instruments")
    public SyncPage<InstrumentDTO> syncInstruments(
            @RequestParam(defaultValue = "0") long cursor,
            @RequestParam(defaultValue = "500") int limit) {
        int l = clampLimit(limit);
        List<InstrumentDTO> rows = instrumentRep.retrievePageAfterInstrumentId(cursor, l + 1);
        return buildPage(rows, l, d -> String.valueOf(d.getInstrument_id()));
    }

    // ---------- NEW: songs ----------

    @GetMapping("/songs")
    public SyncPage<SongDTO> syncSongs(
            @RequestParam(defaultValue = "0") long cursor,
            @RequestParam(defaultValue = "10000") int limit) {
        int l = clampLimit(limit);
        List<SongDTO> rows = songRep.retrievePageAfterSongId(cursor, l + 1);
        return buildPage(rows, l, d -> String.valueOf(d.getSong_id()));
    }

    // ---------- junction tables (composite cursor "albumId:artistId") ----------

    @GetMapping("/album-artists")
    public SyncPage<AlbumContainsArtistDTO> syncAlbumArtists(
            @RequestParam(defaultValue = "") String cursor,
            @RequestParam(defaultValue = "10000") int limit) {

        int l = clampLimit(limit);
        long ca = 0, cart = 0;
        if (!cursor.isEmpty()) {
            String[] p = cursor.split(":", 2);
            ca   = Long.parseLong(p[0]);
            cart = p.length > 1 ? Long.parseLong(p[1]) : 0;
        }
        List<AlbumContainsArtistDTO> rows =
                albumContainsArtistRep.retrievePageAfterKey(ca, cart, l + 1);
        return buildPage(rows, l,
                d -> d.getAlbum_id() + ":" + d.getArtist_id());
    }

    @GetMapping("/video-artists")
    public SyncPage<VideoContainsArtistDTO> syncVideoArtists(
            @RequestParam(defaultValue = "") String cursor,
            @RequestParam(defaultValue = "10000") int limit) {

        int l = clampLimit(limit);
        long cv = 0, cart = 0;
        if (!cursor.isEmpty()) {
            String[] p = cursor.split(":", 2);
            cv   = Long.parseLong(p[0]);
            cart = p.length > 1 ? Long.parseLong(p[1]) : 0;
        }
        List<VideoContainsArtistDTO> rows =
                videoContainsArtistRep.retrievePageAfterKey(cv, cart, l + 1);
        return buildPage(rows, l,
                d -> d.getVideo_id() + ":" + d.getArtist_id());
    }

    // ---------- manifest (optional, recommended) ----------

    @GetMapping("/manifest")
    public List<SyncResource> manifest() {
        return List.of(
                new SyncResource("types",         "/sync/types",          typeRep.count()),
                new SyncResource("durations",     "/sync/durations",      durationRep.count()),
                new SyncResource("instruments",   "/sync/instruments",    instrumentRep.count()),
                new SyncResource("artists",       "/sync/artists",        artistRep.count()),
                new SyncResource("albums",        "/sync/albums",         albumRep.count()),
                new SyncResource("songs",         "/sync/songs",          songRep.count()),        // NEW
                new SyncResource("videos",        "/sync/videos",         videoRep.count()),
                new SyncResource("album-artists", "/sync/album-artists",  albumContainsArtistRep.count()),
                new SyncResource("video-artists", "/sync/video-artists",  videoContainsArtistRep.count())
        );
    }

    public record SyncResource(String name, String path, long count) {}
}