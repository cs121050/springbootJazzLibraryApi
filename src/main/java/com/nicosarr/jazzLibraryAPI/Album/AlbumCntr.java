package com.nicosarr.jazzLibraryAPI.Album;

import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/album")
public class AlbumCntr {

    private final AlbumRep rep;

    public AlbumCntr(AlbumRep rep) {
        this.rep = rep;
    }

    @GetMapping(produces = MediaType.APPLICATION_XML_VALUE)
    public String sayXMLHello() {
        return "<?xml version=\"1.0\"?><album>Album controller...</album>";
    }

    @Transactional
    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AlbumDTO> retrieveAll() {
        return rep.retrieveAll();
    }

    @Transactional
    @GetMapping(value = "/allWithArtists", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AlbumWithArtistDTO> retrieveAllWithArtists() {
        return rep.retrieveAllWithArtists();
    }

    // TODO: Add endpoints for find by release_id, master_id, year, etc.
    // TODO: Add create, update, delete endpoints as needed
}