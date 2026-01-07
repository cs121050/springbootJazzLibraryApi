package com.nicosarr.jazzLibraryAPI.Artist;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import com.nicosarr.jazzLibraryAPI.Video.Video;
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
    
    @Column(name = "instrument_id")    
    private int instrument_id;
    
    @Column(name = "artist_video_count")    
    private int artist_video_count; 
    
    @Column(name = "artist_rank")    
    private int artist_rank;    
    
    @OneToMany(mappedBy = "artist", fetch = FetchType.LAZY)
    @JsonManagedReference
    @JsonIgnore 
    private List<VideoContainsArtist> videoContainsArtists = new ArrayList<>();

    @Transient  // This field is not stored in the database
    //@JsonManagedReference    
    private List<Video> videoList = new ArrayList<>();

    // Getters and setters for the videos field
    @JsonIgnore 
    public List<Video> getVideoList() {
        return videoList;
    }

    public void setVideoList(List<Video> videoList) {
        this.videoList = videoList;
    }
    
    
    
    
	public Artist() {
		// TODO Auto-generated constructor stub
	}
	
    public Artist (int artist_id, String artist_name, String artist_surname, int instrument_id, int artist_video_count, int artist_rank){
	   	this.artist_id = artist_id;
	   	this.artist_name = artist_name;
	   	this.artist_surname = artist_surname;
	   	this.instrument_id = instrument_id;	   	
	   	this.artist_video_count = artist_video_count;
	   	this.artist_rank= artist_rank;		   	
    }
    public Artist (String artist_name, String artist_surname, int instrument_id, int artist_video_count, int artist_rank){
	   	this.artist_name = artist_name;
	   	this.artist_surname = artist_surname;
	   	this.instrument_id = instrument_id;	   	
	   	this.artist_video_count = artist_video_count;
	   	this.artist_rank= artist_rank;		   	
    }
    public Artist (String artist_name, String artist_surname, int instrument_id, int artist_rank){
	   	this.artist_name = artist_name;
	   	this.artist_surname = artist_surname;
	   	this.instrument_id = instrument_id;
	   	this.artist_video_count = 0;	
	   	this.artist_rank= artist_rank;	   		
	   	
    }    
    public Artist (String artist_name, String artist_surname, int instrument_id){
	   	this.artist_name = artist_name;
	   	this.artist_surname = artist_surname;
	   	this.instrument_id = instrument_id;   	
	   	this.artist_video_count = 0;	
	   	this.artist_rank= 0;		
    }      


	public String toString(){
        return "artist_id:" + artist_id + "#artist_name:" + artist_name + "#artist_surname:" + artist_surname 
          + "#instrument_id:" + instrument_id+ "#artist_video_count:" + artist_video_count+ "#artist_rank:" + artist_rank;
    }  
    
    public String valuesToString(){
        return artist_id + "#" + artist_name + "#" + artist_surname + "#" + instrument_id+ "#" + artist_video_count+"#"+artist_rank;
    }        
    
    public Artist toObject(){
         return new Artist(this.artist_id, this.artist_name, this.artist_surname, this.instrument_id, this.artist_video_count, this.artist_rank);
           
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

	public int getInstrument_id() {
		return instrument_id;
	}

	public void setInstrument_id(int instrument_id) {
		this.instrument_id = instrument_id;
	}
	public int getArtist_video_count() {
		return artist_video_count;
	}
	public void setArtist_video_count(int artist_video_count) {
		this.artist_video_count = artist_video_count;
	}
    @JsonIgnore 
	public List<VideoContainsArtist> getVideoContainsArtists() {
		return videoContainsArtists;
	}

	public void setVideoContainsArtists(List<VideoContainsArtist> videoContainsArtists) {
		this.videoContainsArtists = videoContainsArtists;
	}

//	}
	public int getArtist_rank() {
		return artist_rank;
	}

	public void setArtist_rank(int artist_rank) {
		this.artist_rank = artist_rank;
	}

        
}

@JacksonXmlRootElement(localName = "artistArrayListManager")
class ArtistArrayListManager {

    private ArrayList<Artist> artistList;

    public ArtistArrayListManager() {
        artistList = new ArrayList<>();
    }

    public void addArtist(Artist artist) {
        artistList.add(artist);
    }

    public ArrayList<Artist> getArtists() {
        return artistList;
    }
}
