package com.nicosarr.jazzLibraryAPI.BootStrapModel;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Artist.ArtistRep;

import jakarta.annotation.PostConstruct;

@RestController   //http://localhost:8080
@RequestMapping("bootStrapService")
public class BootStrapCntr {

    private final BootStrapRep bootStrapRep;

	
    public BootStrapCntr(BootStrapRep bootStrapRep) {
    	this.bootStrapRep = bootStrapRep;
    }	
	
    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)  // 2 minutes to response - 4.3mb retrieved
    public BootStrapModel retrieveAll() { 
        return bootStrapRep.retrieveAll();    
   
    }   
    
    @PostConstruct
    public void init() {
        System.out.println("BootStrapCntr initialized!");
    }
    
}
