package com.nicosarr.jazzLibraryAPI.Quote;


import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Instrument.Instrument;

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
    		foreignKey = @ForeignKey(name = "FK_artist_id")
    )
    @JsonIgnore 
    private Artist artist; 
    @Transient
    private int artist_id; 

	public Quote () {
	}
    public Quote(int quote_id, String quote_text, int artist_id) {
        this.quote_id = quote_id;
        this.quote_text = quote_text;
        this.artist_id = artist_id;
    }
    public Quote(String quote_text, int artist_id) {
        this.quote_text = quote_text;
        this.artist_id = artist_id;
    }
    public Quote(String quote_text) {
        this.quote_text = quote_text;
        this.artist_id = 0;
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
    // Add getter for instrument_id only
    public int getArtist_id() {
        return this.artist != null ? this.artist.getArtist_id() : 0;
    }
	public void setArtist_id(int artist_id) {
		this.artist_id = artist_id;
	}

	public String toString(){
        return "quote_id:" + quote_id + "#quote_text:" + quote_text + "#artist_id:" + artist_id;
    }   
    public String valuesToString(){
        return quote_id + "#" + quote_text + "#" + artist_id + "#";
    }      
    public Quote toObject(){
        return new Quote(this.quote_id, this.quote_text, this.artist_id);          
    }

}
    
	
