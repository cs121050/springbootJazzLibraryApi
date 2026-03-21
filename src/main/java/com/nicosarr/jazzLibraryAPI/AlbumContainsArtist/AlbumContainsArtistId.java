package com.nicosarr.jazzLibraryAPI.AlbumContainsArtist;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtistId;

@Embeddable
public class AlbumContainsArtistId implements Serializable {

    @Column(name = "artist_id")
    private int artistId;

    @Column(name = "album_id")
    private int albumId;

    public AlbumContainsArtistId() {}

    public AlbumContainsArtistId(int artistId, int albumId) {
        this.artistId = artistId;
        this.albumId = albumId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlbumContainsArtistId that = (AlbumContainsArtistId) o;
        return Objects.equals(artistId, that.artistId) && Objects.equals(albumId, that.albumId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artistId, albumId);
    }
    
	public int getArtistId() {
		return artistId;
	}

	public void setArtistId(int artistId) {
		this.artistId = artistId;
	}

	public int getAlbumId() {
		return albumId;
	}

	public void setAlbumId(int albumId) {
		this.albumId = albumId;
	}
}