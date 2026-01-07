package com.nicosarr.jazzLibraryAPI.VideoContainsArtist;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Video.Video;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity       
@Table(name = "videoContainsArtist")  // database tables 
// by default hibernate tracks tables name underscored "_", so you need to place that at application.properties
//  spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
@JacksonXmlRootElement(localName = "videoContainsArtist")
public class VideoContainsArtist {
    
    @EmbeddedId
    private VideoContainsArtistId id;
	
	@ManyToOne
	@JsonBackReference	
    @MapsId("artistId")	
    @JoinColumn(name = "artist_id", insertable = false, updatable = false)
	private  Artist artist;
	@ManyToOne
	@JsonBackReference	
    @MapsId("videoId")	
    @JoinColumn(name = "video_id", insertable = false, updatable = false)
    private Video video;
	
    public VideoContainsArtist() {}

    public VideoContainsArtist(Artist artist, Video video) {
        this.artist = artist;
        this.video = video;
        this.id = new VideoContainsArtistId(artist.getArtist_id(), video.getVideo_id());
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
	
	
	
}
