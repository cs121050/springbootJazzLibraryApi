package com.nicosarr.jazzLibraryAPI.Artist;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import jakarta.persistence.*;

import com.nicosarr.jazzLibraryAPI.Video.Video;
import com.nicosarr.jazzLibraryAPI.Instrument.Instrument;
import com.nicosarr.jazzLibraryAPI.Quote.Quote;
import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtist;

@Entity
@Table(name = "Artist")  // database tables
@JacksonXmlRootElement(localName = "artist")
public class Artist {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY if your DB supports auto-increment
    @Column(name = "artist_id")	
    private int artist_id;
	
    @Column(name = "artist_name")
    private String artist_name;
    
    @Column(name = "artist_surname")    
    private String artist_surname;
    
    @Column(name = "artist_rank")    
    private Integer  artist_rank;    
    
    @ManyToOne(fetch = FetchType.LAZY)  // This makes it lazy loaded
    @JoinColumn(
    		name = "instrument_id", 
    		referencedColumnName = "instrument_id", 
    		foreignKey = @ForeignKey(name = "FK_instrument_id")
    )
    @JsonIgnore  // This will exclude instrument from JSON serialization
    private Instrument instrument;  // Change from int to Instrument object
    @Transient
    private int instrument_id; 
        
    @OneToMany(mappedBy = "artist", fetch = FetchType.LAZY)
    private List<VideoContainsArtist> videoContainsArtists = new ArrayList<>();

    @OneToMany(mappedBy = "artist", fetch = FetchType.LAZY)
    private List<Quote> quotes = new ArrayList<>(); 
    
	public Artist() {
	}
    public Artist (int artist_id, String artist_name, String artist_surname, int instrument_id, Integer  artist_rank){
	   	this.artist_id = artist_id;
	   	this.artist_name = artist_name;
	   	this.artist_surname = artist_surname;
	   	this.instrument_id = instrument_id;	   	
	   	this.artist_rank= artist_rank;		   	
    }
    public Artist (String artist_name, String artist_surname, int instrument_id, Integer  artist_rank){
	   	this.artist_name = artist_name;
	   	this.artist_surname = artist_surname;
	   	this.instrument_id = instrument_id;	   	
	   	this.artist_rank= artist_rank;	   		
	   	
    }    
    public Artist (String artist_name, String artist_surname, int instrument_id){
	   	this.artist_name = artist_name;
	   	this.artist_surname = artist_surname;
	   	this.instrument_id = instrument_id;	   	
	   	this.artist_rank= 0;		
    }      
    
    public int getArtist_id() {
        return artist_id;
    }
    public void setArtist_id(int artist_id) {
        this.artist_id = artist_id;
    }
    public String getArtist_name() {
        return artist_name;
    }
    public void setArtist_name(String artist_name) {
        this.artist_name = artist_name;
    }
    public String getArtist_surname() {
        return artist_surname;
    }
    public void setArtist_surname(String artist_surname) {
        this.artist_surname = artist_surname;
    }
	public Instrument getInstrument() {
		return instrument;
	}
	public void setInstrument(Instrument instrument) {
		this.instrument = instrument;
	}
    // Add getter for instrument_id only
    public int getInstrument_id() {
        return this.instrument != null ? this.instrument.getInstrument_id() : 0;
    }
	public void setInstrument_id(int instrument_id) {
		this.instrument_id = instrument_id;
	}
	//@JsonIgnore 
	public List<VideoContainsArtist> getVideoContainsArtists() {
		return videoContainsArtists;
	}
	public void setVideoContainsArtists(List<VideoContainsArtist> videoContainsArtists) {
		this.videoContainsArtists = videoContainsArtists;
	}
	public Integer  getArtist_rank() {
		return artist_rank;
	}
	public void setArtist_rank(Integer  artist_rank) {
		this.artist_rank = artist_rank;
	}
	// Add a transient field to get videos directly
    @Transient
    @JsonProperty("videos") // This will include videos in the JSON
    public List<Video> getVideos() {
        if (videoContainsArtists == null) {
            return new ArrayList<>();
        }
        return videoContainsArtists.stream()
            .map(VideoContainsArtist::getVideo)
            .collect(Collectors.toList());
    }
	   
	public String toString(){
        return "artist_id:" + artist_id + "#artist_name:" + artist_name + "#artist_surname:" + artist_surname 
          + "#instrument_id:" + instrument_id+ "#artist_rank:" + artist_rank;
    }  
    public String valuesToString(){
        return artist_id + "#" + artist_name + "#" + artist_surname + "#" + instrument_id +"#"+artist_rank;
    }        
    public Artist toObject(){
         return new Artist(this.artist_id, this.artist_name, this.artist_surname, this.instrument_id, this.artist_rank);   
    }  
}

