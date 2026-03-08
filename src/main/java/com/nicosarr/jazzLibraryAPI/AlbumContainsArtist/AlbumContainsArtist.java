package com.nicosarr.jazzLibraryAPI.AlbumContainsArtist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nicosarr.jazzLibraryAPI.Album.Album;
import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import jakarta.persistence.*;

@Entity
@Table(name = "AlbumContainsArtist")
public class AlbumContainsArtist {

    @EmbeddedId
    private AlbumContainsArtistId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("discogsArtistId")   // maps the discogsArtistId part of the composite key
    @JoinColumn(name = "discogs_artist_id", referencedColumnName = "discogs_id",
                insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "FK_aca_artist"))
    @JsonIgnore
    private Artist artist;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("discogsReleaseId")  // maps the discogsReleaseId part of the composite key
    @JoinColumn(name = "discogs_release_id", referencedColumnName = "release_id",
                insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "FK_aca_album"))
    @JsonIgnore
    private Album album;

    @Column(name = "is_main", nullable = false)
    private boolean isMain;

    public AlbumContainsArtist() {}

    public AlbumContainsArtist(AlbumContainsArtistId id, Artist artist, Album album, boolean isMain) {
        this.id = id;
        this.artist = artist;
        this.album = album;
        this.isMain = isMain;
    }

    // Getters and setters
    public AlbumContainsArtistId getId() {
        return id;
    }

    public void setId(AlbumContainsArtistId id) {
        this.id = id;
    }

    public Artist getArtist() {
        return artist;
    }

    public void setArtist(Artist artist) {
        this.artist = artist;
    }

    public Album getAlbum() {
        return album;
    }

    public void setAlbum(Album album) {
        this.album = album;
    }

    public boolean isMain() {
        return isMain;
    }

    public void setMain(boolean main) {
        isMain = main;
    }

    // Convenience methods to get Discogs IDs from the embedded id
    public int getDiscogsArtistId() {
        return id != null ? id.getDiscogsArtistId() : 0;
    }

    public int getDiscogsReleaseId() {
        return id != null ? id.getDiscogsReleaseId() : 0;
    }
}