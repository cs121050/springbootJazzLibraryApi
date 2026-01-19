package com.nicosarr.jazzLibraryAPI.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Video.Video;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "Duration")  // database tables
@JacksonXmlRootElement(localName = "duration")
public class Duration {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY if your DB supports auto-increment
    private int duration_id;
	
    @Column(name = "duration_name")	      
	private String duration_name;
	
    @OneToMany(mappedBy = "duration", fetch = FetchType.LAZY)
    @JsonBackReference
    @JsonIgnore
    private List<Video> video = new ArrayList<>();
    
    public Duration () {}
    public Duration (int duration_id, String duration_name){
	   	this.duration_id = duration_id;
	   	this.duration_name = duration_name;
    }  
    public Duration (String duration_name){
 	   	this.duration_name = duration_name;
    }        
	public String toString(){
        return "duration_id:" + duration_id + "#duration_name:" + duration_name+ "#duration_video_count:";
    }   
    public String valuesToString(){
        return duration_id + "#" + duration_name + "#";
    }      
    public Duration toObject(){ 
        return new Duration(this.duration_id, this.duration_name);          
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
	public List<Video> getVideo() {
		return video;
	}
	public void setVideo(List<Video> video) {
		this.video = video;
	}

}
