package com.nicosarr.jazzLibraryAPI.Instrument;
import java.util.ArrayList;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Instrument")  // database tables
@JacksonXmlRootElement(localName = "instrument")
public class Instrument {
    
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY if your DB supports auto-increment	
	private int instrument_id;
    private String instrument_name;
    private int instrument_artist_count;
    private int instrument_video_count;        

    public int getInstrument_video_count() {
		return instrument_video_count;
	}
	public void setInstrument_video_count(int instrument_video_count) {
		this.instrument_video_count = instrument_video_count;
	}
	public Instrument () {}	
	
	public Instrument (int instrument_id, String instrument_name, int instrument_artist_count, int instrument_video_count){
	   	this.instrument_id = instrument_id;
	   	this.instrument_name = instrument_name;
	   	this.instrument_artist_count = instrument_artist_count;
	   	this.instrument_video_count = instrument_video_count;	   	
    }
    public Instrument (int instrument_id, String instrument_name, int instrument_artist_count){
	   	this.instrument_id = instrument_id;
	   	this.instrument_name = instrument_name;
	   	this.instrument_artist_count = instrument_artist_count;
	   	this.instrument_video_count = 0;	   	
    }
    public Instrument (String instrument_name, int instrument_artist_count, int instrument_video_count){
 	   	this.instrument_name = instrument_name;
	   	this.instrument_artist_count = instrument_artist_count; 	
	   	this.instrument_video_count = instrument_video_count;		   	
    }   
    public Instrument (String instrument_name, int instrument_artist_count){
 	   	this.instrument_name = instrument_name;
	   	this.instrument_artist_count = instrument_artist_count;
	   	this.instrument_video_count = 0;	   	
    }       
    public Instrument (String instrument_name){
 	   	this.instrument_name = instrument_name;
	   	this.instrument_artist_count = 0; 	
	   	this.instrument_video_count = 0;	   	
    }      
	public String toString(){
        return "instrument_id:" + instrument_id + "#instrument_name:" + instrument_name+ "#instrument_artist_count:" + instrument_artist_count+ "#instrument_video_count:" + instrument_video_count;
    }   
    public String valuesToString(){
        return instrument_id + "#" + instrument_name + "#" + instrument_artist_count+ "#" + instrument_video_count;
    }      
    public Instrument toObject(){
        return new Instrument(this.instrument_id, this.instrument_name, this.instrument_artist_count, this.instrument_video_count);          
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
	public int getInstrument_artist_count() {
		return instrument_artist_count;
	}
	public void setInstrument_artist_count(int instrument_artist_count) {
		this.instrument_artist_count = instrument_artist_count;
	}

	
	
	
}

@JacksonXmlRootElement(localName = "instrumentArrayListManager")
class InstrumentArrayListManager {

    private ArrayList<Instrument> instrumentList;

    public InstrumentArrayListManager() {
    	instrumentList = new ArrayList<>();
    }

    public void addInstrument(Instrument instrument) {
    	instrumentList.add(instrument);
    }

    public ArrayList<Instrument> getInstrument() {
        return instrumentList;
    }  
   	
}

