package com.nicosarr.jazzLibraryAPI.Album;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.nicosarr.jazzLibraryAPI.service.JobManager;

import java.util.List;

@RestController
@RequestMapping("/album")
public class AlbumCntr {

    private final AlbumRep rep;
    private final JobManager jobManager;

    public AlbumCntr(AlbumRep rep, JobManager jobManager) {
        this.rep = rep;
        this.jobManager = jobManager;
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

    @PostMapping("/importDiscographies")
    public ResponseEntity<String> importDiscographies(@RequestParam(required = false) Integer limit) {
        String jobId = jobManager.startJob((jobContext) -> {
            String result = rep.processAllAlbumsFromArtists(jobContext, limit);
            System.out.println("Job " + jobContext.getJobId() + " finished: " + result);
        });
        return ResponseEntity.accepted().body("Job started. ID: " + jobId);
    }

    
    // TODO: Add endpoints for find by release_id, master_id, year, etc.
    // TODO: Add create, update, delete endpoints as needed
}