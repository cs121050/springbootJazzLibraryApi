package com.nicosarr.jazzLibraryAPI.AlbumContainsArtist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.nicosarr.jazzLibraryAPI.Album.Album;
import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Video.Video;

import jakarta.persistence.*;

@Entity
@Table(name = "AlbumContainsArtist")
@JacksonXmlRootElement(localName = "albumContainsArtist")
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
    @MapsId("albumId")
    @JoinColumn(name = "album_id", insertable = false, updatable = false)
    @JsonIgnore
    private Album album;
    private int album_id; 

    
    @Column(name = "is_main", nullable = false)
    private int is_main;

    public AlbumContainsArtist() {}

    

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



	public int getIs_main() {
		return is_main;
	}



	public void setIs_main(int is_main) {
		this.is_main = is_main;
	}



	public void setAlbum_id(int album_id) {
		this.album_id = album_id;
	}



	// Getters and setters for the transient fields
    public int getArtist_id() {
        return this.artist != null ? this.artist.getArtist_id() : 0;
    }
	public void setArtist_id(int artist_id) {
		this.artist_id = artist_id;
	}
    public int getAlbum_id() {
        return this.album != null ? this.album.getAlbum_id() : 0;
    }
	public void setVideo_id(int album_id) {
		this.album_id = album_id;
	}
}