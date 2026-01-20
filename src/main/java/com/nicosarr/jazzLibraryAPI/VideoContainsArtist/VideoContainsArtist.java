package com.nicosarr.jazzLibraryAPI.VideoContainsArtist;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Video.Video;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity       
@Table(name = "VideoContainsArtist")  // database tables 
// by default hibernate tracks tables name underscored "_", so you need to place that at application.properties
//  spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
@JacksonXmlRootElement(localName = "videoContainsArtist")
public class VideoContainsArtist {
    
    @EmbeddedId
    private VideoContainsArtistId id;
	
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("artistId")
    @JoinColumn(name = "artist_id", insertable = false, updatable = false)
    @JsonIgnore  // This will exclude instrument from JSON serialization
    private Artist artist;
    @Transient
    private int artist_id; 
    
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("videoId")
    @JoinColumn(name = "video_id", insertable = false, updatable = false)
    @JsonIgnore  // This will exclude instrument from JSON serialization
    private Video video;
    @Transient
    private int video_id; 
    
    public VideoContainsArtist() {}

    public VideoContainsArtist(int artist_id, int video_id) {
		this.artist_id = artist_id;
        this.video_id = video_id;
    }	
	public VideoContainsArtistId getId() {
		return id;
	}
	public void setId(VideoContainsArtistId id) {
		this.id = id;
	}
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
    //
    public int getArtist_id() {
        return this.artist != null ? this.artist.getArtist_id() : 0;
    }
	public void setArtist_id(int artist_id) {
		this.artist_id = artist_id;
	}
    public int getVideo_id() {
        return this.video != null ? this.video.getVideo_id() : 0;
    }
	public void setVideo_id(int video_id) {
		this.video_id = video_id;
	}
	
	
}
