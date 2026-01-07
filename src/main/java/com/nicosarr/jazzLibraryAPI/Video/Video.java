package com.nicosarr.jazzLibraryAPI.Video;
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

import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtist;

@Entity
@Table(name = "Video")  // database tables
@JacksonXmlRootElement(localName = "video")
public class Video {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY if your DB supports auto-increment	
	private int video_id;
	
    @Column(name = "duration_id")
    private int duration_id;
    
    @Column(name = "video_name")    
    private String video_name;
    
    @Column(name = "video_duration")    
    private String video_duration;
    
    @Column(name = "video_path")    
    private String video_path;
    
    @Column(name = "type_id")    
    private int type_id;
    
    @Column(name = "location_id")    
    private String location_id;
    
    @Column(name = "video_availability")    
    private String video_availability;

    
    @OneToMany(mappedBy = "video", fetch = FetchType.LAZY)
    @JsonManagedReference 
    @JsonIgnore     
    private List<VideoContainsArtist> videoContainsArtists = new ArrayList<>();    
    
    @Transient  // This field is not stored in the database
	 //@JsonManagedReference    
     private List<Artist> artistList = new ArrayList<>();
	
	  // Getters and setters for the videos field
    @JsonIgnore 
	  public List<Artist> getArtistList() {
	      return artistList;
	  }
	
	
	    public void setArtistList(List<Artist> artistList) {
		this.artistList = artistList;
	}
	    @JsonIgnore 

	public List<VideoContainsArtist> getVideoContainsArtists() {
		return videoContainsArtists;
	}

	public void setVideoContainsArtists(List<VideoContainsArtist> videoContainsArtists) {
		this.videoContainsArtists = videoContainsArtists;
	}

    public Video () {}
    public Video (int video_id, int duration_id, String video_name, String video_duration, String video_path,
    		 int type_id, String location_id, String video_availability/*, String containedArtistsToObjectString*/){
	   	
    	this.video_id = video_id;
	   	this.duration_id = duration_id;
	   	this.video_name = video_name;
	   	this.video_duration = video_duration;
	   	this.video_path = video_path;
	   	this.type_id = type_id;
	   	this.location_id = location_id;
	   	this.video_availability = video_availability;
	   	//this.containedArtistsToObjectString = containedArtistsToObjectString;
	   	
    }
    public Video (int duration_id, String video_name, String video_duration, String video_path,
   		 int type_id, String location_id, String video_availability/*, String containedArtistsToObjectString*/){
    	
	   	this.duration_id = duration_id;
	   	this.video_name = video_name;
	   	this.video_duration = video_duration;
	   	this.video_path = video_path;
	   	this.type_id = type_id;
	   	this.location_id = location_id;
	   	this.video_availability = video_availability;
	   	//this.containedArtistsToObjectString = containedArtistsToObjectString;
    }    
	public String toString(){
        return "duration_id:" + duration_id + "#video_name:" + video_name + "#video_duration:" + video_duration
        	+ "#video_path:" + video_path	 + "#type_id:" + type_id + "#location_id:" + location_id
        	+ "#video_availability:" + video_availability;//  + "#containedArtistsToObjectString:" + containedArtistsToObjectString ;
    }   
    public String valuesToString(){
        return duration_id + "#" + video_name + "#" + video_duration
            	+ "#" + video_path	 + "#" + type_id + "#" + location_id
            	+ "#" + video_availability  ;//+ "#" + containedArtistsToObjectString ;
    }  
    public Video toObject(){
        return new Video(this.duration_id, this.video_name, this.video_duration,
	   	         this.video_path, this.type_id, this.location_id, this.video_availability);//, this.containedArtistsToObjectString);          
    }
    
    
	public int getVideo_id() {
		return video_id;
	}
	public void setVideo_id(int video_id) {
		this.video_id = video_id;
	}
	public int getDuration_id() {
		return duration_id;
	}
	public void setDuration_id(int duration_id) {
		this.duration_id = duration_id;
	}
	public String getVideo_name() {
		return video_name;
	}
	public void setVideo_name(String video_name) {
		this.video_name = video_name;
	}
	public String getVideo_duration() {
		return video_duration;
	}
	public void setVideo_duration(String video_duration) {
		this.video_duration = video_duration;
	}
	public String getVideo_path() {
		return video_path;
	}
	public void setVideo_path(String video_path) {
		this.video_path = video_path;
	}
	public int getType_id() {
		return type_id;
	}
	public void setType_id(int type_id) {
		this.type_id = type_id;
	}
	public String getLocation_id() {
		return location_id;
	}
	public void setLocation_id(String location_id) {
		this.location_id = location_id;
	}
	public String getVideo_availability() {
		return video_availability;
	}
	public void setVideo_availability(String video_availability) {
		this.video_availability = video_availability;
	}

}

@JacksonXmlRootElement(localName = "videoArrayListManager")
class VideoArrayListManager {
	
	private ArrayList<Video> videoList;

    public VideoArrayListManager() {
    	videoList = new ArrayList<>();
    }

    public void addVideo(Video video) {
    	videoList.add(video);
    }

    public ArrayList<Video> getVideo() {
        return videoList;			
    }  		
	
}


