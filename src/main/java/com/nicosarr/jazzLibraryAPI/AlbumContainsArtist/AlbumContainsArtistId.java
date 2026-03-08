package com.nicosarr.jazzLibraryAPI.AlbumContainsArtist;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class AlbumContainsArtistId implements Serializable {

    @Column(name = "discogs_artist_id", nullable = false)
    private int discogsArtistId;

    @Column(name = "discogs_release_id", nullable = false)
    private int discogsReleaseId;

    public AlbumContainsArtistId() {}

    public AlbumContainsArtistId(int discogsArtistId, int discogsReleaseId) {
        this.discogsArtistId = discogsArtistId;
        this.discogsReleaseId = discogsReleaseId;
    }

    // Getters and setters
    public int getDiscogsArtistId() {
        return discogsArtistId;
    }

    public void setDiscogsArtistId(int discogsArtistId) {
        this.discogsArtistId = discogsArtistId;
    }

    public int getDiscogsReleaseId() {
        return discogsReleaseId;
    }

    public void setDiscogsReleaseId(int discogsReleaseId) {
        this.discogsReleaseId = discogsReleaseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlbumContainsArtistId that = (AlbumContainsArtistId) o;
        return discogsArtistId == that.discogsArtistId &&
               discogsReleaseId == that.discogsReleaseId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(discogsArtistId, discogsReleaseId);
    }
}