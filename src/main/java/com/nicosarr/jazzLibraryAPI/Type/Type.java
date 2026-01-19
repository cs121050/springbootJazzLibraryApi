package com.nicosarr.jazzLibraryAPI.Type;

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
@Table(name = "Type")  // database tables
@JacksonXmlRootElement(localName = "type")
public class Type {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY if your DB supports auto-increment	
	private int type_id;
    @Column(name = "type_name")
	private String type_name; 
    
    @OneToMany(mappedBy = "type", fetch = FetchType.LAZY)
    @JsonBackReference
    @JsonIgnore
    private List<Video> videos = new ArrayList<>();
    
    
    public Type () {
    }	
	public Type(int type_id, String type_name) {
		this.type_id = type_id;
		this.type_name = type_name;
	}

	public int getType_id() {
		return type_id;
	}
	public void setType_id(int type_id) {
		this.type_id = type_id;
	}
	public String getType_name() {
		return type_name;
	}
	public void setType_name(String type_name) {
		this.type_name = type_name;
	}
	public List<Video> getVideos() {
		return videos;
	}
	public void setVideos(List<Video> videos) {
		this.videos = videos;
	}

	@Override
	public String toString() {
		return "Type [type_id=" + type_id + ", type_name=" + type_name + ", videos=" + videos + "]";
	}
	
	

}
