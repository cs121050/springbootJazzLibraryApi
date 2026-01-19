package com.nicosarr.jazzLibraryAPI.Instrument;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;

import com.nicosarr.jazzLibraryAPI.Artist.Artist;

@Entity
@Table(name = "Instrument")  // database tables
@JacksonXmlRootElement(localName = "instrument")
public class Instrument {
    
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY if your DB supports auto-increment	
    @Column(name = "instrument_id")
	private int instrument_id;
    
    @Column(name = "instrument_name")
	private String instrument_name;

    @OneToMany(mappedBy = "instrument", fetch = FetchType.LAZY)
    @JsonBackReference
    @JsonIgnore
    private List<Artist> artists = new ArrayList<>();
    
	public Instrument () {
	}	
	public Instrument (int instrument_id, String instrument_name){
	   	this.instrument_id = instrument_id;
	   	this.instrument_name = instrument_name;   	
    }
    public Instrument (String instrument_name){
 	   	this.instrument_name = instrument_name;   	
    }     

	public int getInstrument_id() {
		return instrument_id;
	}
	public void setInstrument_id(int instrument_id) {
		this.instrument_id = instrument_id;
	}
	public String getInstrument_name() {
		return instrument_name;
	}
	public void setInstrument_name(String instrument_name) {
		this.instrument_name = instrument_name;
	}
    public List<Artist> getArtists() {
        return artists;
    }
    public void setArtists(List<Artist> artists) {
        this.artists = artists;
    }
}

