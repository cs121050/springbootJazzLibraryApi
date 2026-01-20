package com.nicosarr.jazzLibraryAPI.VideoContainsArtist;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nicosarr.jazzLibraryAPI.Video.Video;
import com.nicosarr.jazzLibraryAPI.Video.VideoRep;

@RestController   //http://localhost:8080
@RequestMapping("videoContainsArtist")
public class VideoContainsArtistCntr {

	private ArrayList<VideoContainsArtist> videoContainsArtistList;   
    private final VideoContainsArtistRep videoContainsArtistRep;

    
    public VideoContainsArtistCntr(VideoContainsArtistRep videoContainsArtistRep) {
    	this.videoContainsArtistRep = videoContainsArtistRep;
    }
    
    @GetMapping(value = "", produces = MediaType.APPLICATION_XML_VALUE)
    public String sayXMLHello() { 
        return "<?xml version=\"1.0\"?>" + "<videoContainsArtistService> videoContainsArtist controler... " + "</videoContainsArtistService>";
    }
    
    @Transactional   
    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VideoContainsArtistDTO> getAllVideoContainsArtist() {
        return videoContainsArtistRep.retrieveAll();   
	}
}
