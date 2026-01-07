package com.nicosarr.jazzLibraryAPI.Duration;
import java.util.ArrayList;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Duration")  // database tables
@JacksonXmlRootElement(localName = "duration")
public class Duration {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY if your DB supports auto-increment
    private int duration_id;
    private String duration_name;
    private int duration_video_count;    
	
    public Duration () {}
    public Duration (int duration_id, String duration_name, int duration_video_count){
	   	this.duration_id = duration_id;
	   	this.duration_name = duration_name;
	   	this.duration_video_count = duration_video_count;
    }
    public Duration (String duration_name, int duration_video_count){
 	   	this.duration_name = duration_name;
	   	this.duration_video_count = duration_video_count; 	   	
    }    
    public Duration (String duration_name){
 	   	this.duration_name = duration_name;
	   	this.duration_video_count = 0; 	   	
    }        
	public String toString(){
        return "duration_id:" + duration_id + "#duration_name:" + duration_name+ "#duration_video_count:" + duration_video_count;
    }   
    public String valuesToString(){
        return duration_id + "#" + duration_name + "#" + duration_video_count;
    }      
    public Duration toObject(){ 
        return new Duration(this.duration_id, this.duration_name, this.duration_video_count);          
    }
	public int getDuration_id() {
		return duration_id;
	}
	public void setDuration_id(int duration_id) {
		this.duration_id = duration_id;
	}
	public String getDuration_name() {
		return duration_name;
	}
	public void setDuration_name(String duration_name) {
		this.duration_name = duration_name;
	}
	public int getDuration_video_count() {
		return duration_video_count;
	}
	public void setDuration_video_count(int duration_video_count) {
		this.duration_video_count = duration_video_count;
	}

	
}
    
@JacksonXmlRootElement(localName = "durationArrayListManager")
class DurationArrayListManager {

    private ArrayList<Duration> durationList;

    public DurationArrayListManager() {
    	durationList = new ArrayList<>();
    }

    public void addDuration(Duration duration) {
    	durationList.add(duration);
    }

    public ArrayList<Duration> getDuration() {
        return durationList;
    }  
   	
}
