package com.nicosarr.jazzLibraryAPI.Song;

import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/song")
public class SongCntr {

    private final SongRep rep;

    public SongCntr(SongRep rep) {
        this.rep = rep;
    }

    @GetMapping(produces = MediaType.APPLICATION_XML_VALUE)
    public String sayXMLHello() {
        return "<?xml version=\"1.0\"?><song>Song controller...</song>";
    }

    @Transactional
    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SongDTO> retrieveAll() {
        return rep.retrieveAll();
    }

    // Retrieve all songs with full main artist details
    @Transactional
    @GetMapping(value = "/allWithArtist", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SongWithArtistDTO> retrieveAllWithMainArtist() {
        return rep.retrieveAllWithMainArtist();
    }

    // Find songs by album ID
    @Transactional
    @GetMapping(value = "/byAlbum/{albumId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SongDTO> findByAlbumId(@PathVariable int albumId) {
        return rep.findByAlbumId(albumId);
    }

    // Find songs by main artist ID
    @Transactional
    @GetMapping(value = "/byArtist/{artistId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SongDTO> findByMainArtistId(@PathVariable int artistId) {
        return rep.findByMainArtistId(artistId);
    }

    // TODO: Add endpoints for create, update, delete as needed
}