package com.nicosarr.jazzLibraryAPI.AlbumContainsArtist;

import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/albumContainsArtist")
public class AlbumContainsArtistCntr {

    private final AlbumContainsArtistRep rep;

    public AlbumContainsArtistCntr(AlbumContainsArtistRep rep) {
        this.rep = rep;
    }

    @GetMapping(produces = MediaType.APPLICATION_XML_VALUE)
    public String sayXMLHello() {
        return "<?xml version=\"1.0\"?><albumContainsArtist>AlbumContainsArtist controller...</albumContainsArtist>";
    }

    @Transactional
    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AlbumContainsArtistDTO> retrieveAll() {
        return rep.retrieveAll();
    }

    //TODO// Find by Discogs IDs
    
    //TODO// Delete endpoint
}    