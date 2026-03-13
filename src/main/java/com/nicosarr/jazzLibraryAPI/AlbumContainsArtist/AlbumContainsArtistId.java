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

    @Column(name = "discogs_release_id", nullable = false)
    private int discogsReleaseId;

    public AlbumContainsArtistId() {}

    public AlbumContainsArtistId(int artistId, int discogsReleaseId) {
        this.artistId = artistId;
        this.discogsReleaseId = discogsReleaseId;
    }

    // Override equals and hashCode for proper comparison in collections
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlbumContainsArtistId that = (AlbumContainsArtistId) o;
        return Objects.equals(artistId, that.artistId) && Objects.equals(discogsReleaseId, that.discogsReleaseId);
    }

    // Getters and setters
	public int getArtistId() {
		return artistId;
	}

	public void setArtistId(int artistId) {
		this.artistId = artistId;
	}

	public int getDiscogsReleaseId() {
		return discogsReleaseId;
	}

	public void setDiscogsReleaseId(int discogsReleaseId) {
		this.discogsReleaseId = discogsReleaseId;
	}
}