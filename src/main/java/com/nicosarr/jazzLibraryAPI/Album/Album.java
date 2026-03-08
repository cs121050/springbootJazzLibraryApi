package com.nicosarr.jazzLibraryAPI.Album;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

import com.nicosarr.jazzLibraryAPI.AlbumContainsArtist.AlbumContainsArtist;

@Entity
@Table(name = "Album")
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int album_id;                     // internal primary key

    @Column(unique = true, nullable = false)
    private int release_id;                    // Discogs release ID

    private Integer master_id;                  // Discogs master ID (if any)
    private String master_url;

    private Double rating_average;               // from community.rating.average
    private Integer rating_count;                 // from community.rating.count

    private Integer year;                          // release year
    private String country;
    private String released;                       // raw release date string
    private String released_formatted;
    @Column(columnDefinition = "TEXT")
    private String notes;                           // release notes

    private String date_added;
    private String date_changed;

    private String title;
    private String uri;
    private String thumb;

    // JSON fields stored as TEXT
    @Column(columnDefinition = "TEXT")
    private String companies;                       // JSON array

    @Column(columnDefinition = "TEXT")
    private String extra_artists;                    // JSON array

    @Column(columnDefinition = "TEXT")
    private String genres;                           // JSON array

    @Column(columnDefinition = "TEXT")
    private String images;                            // JSON array

    @Column(columnDefinition = "TEXT")
    private String labels;                            // JSON array

    @Column(columnDefinition = "TEXT")
    private String styles;                            // JSON array

    @Column(columnDefinition = "TEXT")
    private String tracklist;                         // JSON array

    @Column(columnDefinition = "TEXT")
    private String videos;                             // JSON array

    // One-to-many relationship with the junction table
    @OneToMany(mappedBy = "album", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlbumContainsArtist> albumContainsArtists = new ArrayList<>();

    // Default constructor
    public Album() {}
    
	public Album(int album_id, int release_id, Integer master_id, String master_url, Double rating_average,
			Integer rating_count, Integer year, String country, String released, String released_formatted,
			String notes, String date_added, String date_changed, String title, String uri, String thumb,
			String companies, String extra_artists, String genres, String images, String labels, String styles,
			String tracklist, String videos, List<AlbumContainsArtist> albumContainsArtists) {
		super();
		this.album_id = album_id;
		this.release_id = release_id;
		this.master_id = master_id;
		this.master_url = master_url;
		this.rating_average = rating_average;
		this.rating_count = rating_count;
		this.year = year;
		this.country = country;
		this.released = released;
		this.released_formatted = released_formatted;
		this.notes = notes;
		this.date_added = date_added;
		this.date_changed = date_changed;
		this.title = title;
		this.uri = uri;
		this.thumb = thumb;
		this.companies = companies;
		this.extra_artists = extra_artists;
		this.genres = genres;
		this.images = images;
		this.labels = labels;
		this.styles = styles;
		this.tracklist = tracklist;
		this.videos = videos;
		this.albumContainsArtists = albumContainsArtists;
	}



	public int getAlbum_id() {
		return album_id;
	}

	public void setAlbum_id(int album_id) {
		this.album_id = album_id;
	}

	public int getRelease_id() {
		return release_id;
	}

	public void setRelease_id(int release_id) {
		this.release_id = release_id;
	}

	public Integer getMaster_id() {
		return master_id;
	}

	public void setMaster_id(Integer master_id) {
		this.master_id = master_id;
	}

	public String getMaster_url() {
		return master_url;
	}

	public void setMaster_url(String master_url) {
		this.master_url = master_url;
	}

	public Double getRating_average() {
		return rating_average;
	}

	public void setRating_average(Double rating_average) {
		this.rating_average = rating_average;
	}

	public Integer getRating_count() {
		return rating_count;
	}

	public void setRating_count(Integer rating_count) {
		this.rating_count = rating_count;
	}

	public Integer getYear() {
		return year;
	}

	public void setYear(Integer year) {
		this.year = year;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getReleased() {
		return released;
	}

	public void setReleased(String released) {
		this.released = released;
	}

	public String getReleased_formatted() {
		return released_formatted;
	}

	public void setReleased_formatted(String released_formatted) {
		this.released_formatted = released_formatted;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getDate_added() {
		return date_added;
	}

	public void setDate_added(String date_added) {
		this.date_added = date_added;
	}

	public String getDate_changed() {
		return date_changed;
	}

	public void setDate_changed(String date_changed) {
		this.date_changed = date_changed;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public String getThumb() {
		return thumb;
	}

	public void setThumb(String thumb) {
		this.thumb = thumb;
	}

	public String getCompanies() {
		return companies;
	}

	public void setCompanies(String companies) {
		this.companies = companies;
	}

	public String getExtra_artists() {
		return extra_artists;
	}

	public void setExtra_artists(String extra_artists) {
		this.extra_artists = extra_artists;
	}

	public String getGenres() {
		return genres;
	}

	public void setGenres(String genres) {
		this.genres = genres;
	}

	public String getImages() {
		return images;
	}

	public void setImages(String images) {
		this.images = images;
	}

	public String getLabels() {
		return labels;
	}

	public void setLabels(String labels) {
		this.labels = labels;
	}

	public String getStyles() {
		return styles;
	}

	public void setStyles(String styles) {
		this.styles = styles;
	}

	public String getTracklist() {
		return tracklist;
	}

	public void setTracklist(String tracklist) {
		this.tracklist = tracklist;
	}

	public String getVideos() {
		return videos;
	}

	public void setVideos(String videos) {
		this.videos = videos;
	}

	public List<AlbumContainsArtist> getAlbumContainsArtists() {
		return albumContainsArtists;
	}

	public void setAlbumContainsArtists(List<AlbumContainsArtist> albumContainsArtists) {
		this.albumContainsArtists = albumContainsArtists;
	}

    
}