package com.nicosarr.jazzLibraryAPI.VideoContainsArtist;


import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

//you need that for having an "id " into the many to many object
@Embeddable
public class VideoContainsArtistId implements Serializable {

    @Column(name = "artist_id")
    private int artistId;

    @Column(name = "video_id")
    private int videoId;

    public VideoContainsArtistId() {}   
    
    public VideoContainsArtistId(int artistId, int videoId) {
        this.artistId = artistId;
        this.videoId = videoId;
    }    
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoContainsArtistId that = (VideoContainsArtistId) o;
        return Objects.equals(artistId, that.artistId) && Objects.equals(videoId, that.videoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artistId, videoId);
    }

	public int getArtistId() {
		return artistId;
	}

	public void setArtistId(int artistId) {
		this.artistId = artistId;
	}

	public int getVideoId() {
		return videoId;
	}

	public void setVideoId(int videoId) {
		this.videoId = videoId;
	}
    
    

}
