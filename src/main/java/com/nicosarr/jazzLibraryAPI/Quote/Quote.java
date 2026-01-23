package com.nicosarr.jazzLibraryAPI.Quote;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Video.Video;
import com.nicosarr.jazzLibraryAPI.Instrument.Instrument;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "Quote")  // database tables
@JacksonXmlRootElement(localName = "quote")
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY if your DB supports auto-increment	
    private int quote_id;
    
    @Column(name = "quote_text")	
    private String quote_text;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "artist_id", 
        referencedColumnName = "artist_id", 
        foreignKey = @ForeignKey(name = "FK_artist_id"),
        nullable = true  // Make artist_id nullable
    )
    @JsonIgnore 
    private Artist artist; 
    
    @Transient
    private Integer artist_id; // Changed to Integer to support null
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "video_id", 
        referencedColumnName = "video_id", 
        foreignKey = @ForeignKey(name = "FK_video_id"),
        nullable = true  // Make video_id nullable
    )
    @JsonIgnore 
    private Video video; 
    
    @Transient
    private Integer video_id; // Changed to Integer to support null

    public Quote() {
    }
    
    public Quote(int quote_id, String quote_text, Integer artist_id, Integer video_id) {
        this.quote_id = quote_id;
        this.quote_text = quote_text;
        this.artist_id = artist_id;
        this.video_id = video_id;
    }
    
    public Quote(String quote_text, Integer artist_id, Integer video_id) {
        this.quote_text = quote_text;
        this.artist_id = artist_id;
        this.video_id = video_id;
    }
    
    public Quote(String quote_text) {
        this.quote_text = quote_text;
        this.artist_id = null;
        this.video_id = null;
    }
    
    public String getQuote_text() {
        return quote_text;
    }
    
    public void setQuote_text(String quote_text) {
        this.quote_text = quote_text;
    }
    
    public int getQuote_id() {
        return quote_id;
    }
    
    public void setQuote_id(int quote_id) {
        this.quote_id = quote_id;
    }
    
    // Fixed: Return null if both artist and artist_id are null
    public Integer getArtist_id() {
        if (this.artist != null) {
            return this.artist.getArtist_id();
        }
        return this.artist_id; // Could be null
    }
    
    public void setArtist_id(Integer artist_id) {
        this.artist_id = artist_id;
    }
    
    // Fixed: Return null if both video and video_id are null
    public Integer getVideo_id() {
        if (this.video != null) {
            return this.video.getVideo_id();
        }
        return this.video_id; // Could be null
    }
    
    public void setVideo_id(Integer video_id) {
        this.video_id = video_id;
    }
    
    // Getters and setters for the relationship entities
    public Artist getArtist() {
        return artist;
    }
    
    public void setArtist(Artist artist) {
        this.artist = artist;
    }
    
    public Video getVideo() {
        return video;
    }
    
    public void setVideo(Video video) {
        this.video = video;
    }
    
    public String toString(){
        return "quote_id:" + quote_id + 
               "#quote_text:" + quote_text + 
               "#artist_id:" + (artist_id != null ? artist_id : "null") + 
               "#video_id:" + (video_id != null ? video_id : "null");
    }   
    
    public String valuesToString(){
        return quote_id + "#" + 
               quote_text + "#" + 
               (artist_id != null ? artist_id : "null") + "#" + 
               (video_id != null ? video_id : "null") + "#";
    }      
    
    public Quote toObject(){
        return new Quote(this.quote_id, this.quote_text, this.artist_id, this.video_id);          
    }
}