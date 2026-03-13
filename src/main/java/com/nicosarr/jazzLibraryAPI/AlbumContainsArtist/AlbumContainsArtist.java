package com.nicosarr.jazzLibraryAPI.AlbumContainsArtist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nicosarr.jazzLibraryAPI.Album.Album;
import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Video.Video;

import jakarta.persistence.*;

@Entity
@Table(name = "AlbumContainsArtist")
public class AlbumContainsArtist {

    @EmbeddedId
    private AlbumContainsArtistId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("artistId")
    @JoinColumn(name = "artist_id", insertable = false, updatable = false)
    @JsonIgnore  // This will exclude instrument from JSON serialization
    private Artist artist;
    @Transient
    private int artist_id; 

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("discogsReleaseId")  // maps the discogsReleaseId part of the composite key
    @JoinColumn(name = "discogs_release_id", referencedColumnName = "release_id",
                insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "FK_aca_album"))
    @JsonIgnore
    private Album album;
    @Transient
    private int video_id; 

    
    @Column(name = "is_main", nullable = false)
    private boolean isMain;

    public AlbumContainsArtist() {}

    public AlbumContainsArtist(int artist_id, int discogsReleaseId, boolean isMain) {
		this.artist_id = artist_id;
		this.id = new AlbumContainsArtistId(artist_id, discogsReleaseId);
		this.isMain = isMain;
	}

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

	public int getArtist_id() {
		return artist_id;
	}

	public void setArtist_id(int artist_id) {
		this.artist_id = artist_id;
	}

	public Album getAlbum() {
		return album;
	}

	public void setAlbum(Album album) {
		this.album = album;
	}

	public int getVideo_id() {
		return video_id;
	}

	public void setVideo_id(int video_id) {
		this.video_id = video_id;
	}

	public boolean isMain() {
		return isMain;
	}

	public void setMain(boolean isMain) {
		this.isMain = isMain;
	}

    
}