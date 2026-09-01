package com.nicosarr.jazzLibraryAPI.Artist;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.transaction.Transactional;

import org.springframework.web.bind.annotation.PutMapping;
import java.sql.SQLException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.nicosarr.jazzLibraryAPI.service.JobManager;

@RestController // http://localhost:8080
@RequestMapping("artistService")
public class ArtistCntr {

	private ArrayList<Artist> artistList;
	private final ArtistRep artistRep;
    private final JobManager jobManager;
	
    public ArtistCntr(ArtistRep artistRep, JobManager jobManager) {
        this.artistRep = artistRep;
        this.jobManager = jobManager;
    }

	@GetMapping(value = "", produces = MediaType.APPLICATION_XML_VALUE)
	public String sayXMLHello() {
		return "<?xml version=\"1.0\"?>" + "<artistService> artist controler... " + "</artistService>";
	}
    @Transactional
    @GetMapping(value = "/allWithVideo", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ArtistWithVideoDTO> retrieveAllWithVideos() {
        return artistRep.retrieveAllWithVideos();
    }
    @Transactional
    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ArtistDTO> retrieveAll() {
        return artistRep.retrieveAll();
    }
    
    @Transactional
    @GetMapping(value = "/alWithMusicbrainz", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ArtistDTO> getArtistsWithMusicbrainz() {
        List<Artist> artists = artistRep.findArtistsWithMusicbrainzUuid();
        return artists.stream()
                      .map(ArtistDTO::fromEntity)
                      .collect(Collectors.toList());
    }
    
    @PostMapping("/updateWikipediaInfo")
    public ResponseEntity<String> updateWikipediaInfo() {
    	
    	String jobId = jobManager.startJob((jobContext) -> {
            String result = artistRep.processAllArtistsWikipedia(jobContext);
            // Use jobContext.getJobId() instead of the outer variable
            System.out.println("Job " + jobContext.getJobId() + " finished with result: " + result);
        });

        // ---- 3. Return 202 Accepted with the job ID ----
        return ResponseEntity.accepted()
                .body("Job started. ID: " + jobId);
    }    
}
