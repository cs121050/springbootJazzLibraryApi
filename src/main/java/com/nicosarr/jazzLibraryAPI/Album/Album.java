package com.nicosarr.jazzLibraryAPI.Album;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import com.nicosarr.jazzLibraryAPI.Song.Song;

import com.nicosarr.jazzLibraryAPI.AlbumContainsArtist.AlbumContainsArtist;

@Entity
@Table(name = "Album")
public class Album {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int album_id; // internal primary key

	@Column(unique = true, nullable = false)
	private int release_id; // Discogs release ID

	private Integer master_id; // Discogs master ID (if any)

	@Column(name = "youtube_video_id_for_thumbnail")
	private String youtube_video_id_for_thumbnail;

	private Double rating_average; // from community.rating.average
	private Integer rating_count; // from community.rating.count

	private Integer year; // release year

	@Column(name = "release_format_description")
	private String release_format_description;

	private String released; // raw release date string
	private String released_formatted;

	@Column(name = "release_type")
	private String release_type; // derived from format_display

	private String date_added;
	private String date_changed;

	private String title;

	@Column(name = "wikipedia_url")
	private String wikipedia_url;
	
	@Column(name = "wikidata_id")
	private String wikidata_id;

	@Column(name = "coverartarchive_thumb")
	private String coverartarchive_thumb;

	// JSON fields stored as TEXT
	@Column(columnDefinition = "TEXT")
	private String companies; // JSON array

	@Column(columnDefinition = "TEXT")
	private String extra_artists; // JSON array

	@Column(columnDefinition = "TEXT")
	private String genres; // JSON array

	@Column(columnDefinition = "TEXT")
	private String images; // JSON array

	@Column(columnDefinition = "TEXT")
	private String labels; // JSON array

	@Column(columnDefinition = "TEXT")
	private String styles; // JSON array

	@Column(columnDefinition = "TEXT")
	private String tracklist; // JSON array

	@Column(columnDefinition = "TEXT")
	private String videos; // JSON array
	
	@Column(columnDefinition = "TEXT")
	private String wikipedia_data; // JSON array
	
    @Column(name = "musicbrainz_uuid")    
    private String musicbrainz_uuid; 

	// One-to-many relationship with the junction table
	@OneToMany(mappedBy = "album", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<AlbumContainsArtist> albumContainsArtists = new ArrayList<>();

	// Add inside Album class:
	@OneToMany(mappedBy = "album", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Song> song = new ArrayList<>();

	// Default constructor
	public Album() {
	}

	public Album(int album_id, int release_id, Integer master_id, String youtube_video_id_for_thumbnail,
			Double rating_average, Integer rating_count, Integer year, String release_format_description, String released,
			String released_formatted, String release_type, String date_added, String date_changed, String title,
			String wikipedia_url, String coverartarchive_thumb, String companies, String extra_artists, String genres,
			String images, String labels, String styles, String tracklist, String videos,
			List<AlbumContainsArtist> albumContainsArtists, String wikipedia_data, String musicbrainz_uuid, String wikidata_id) {
		this.album_id = album_id;
		this.release_id = release_id;
		this.master_id = master_id;
		this.youtube_video_id_for_thumbnail = youtube_video_id_for_thumbnail;
		this.rating_average = rating_average;
		this.rating_count = rating_count;
		this.year = year;
		this.release_format_description = release_format_description;
		this.released = released;
		this.released_formatted = released_formatted;
		this.release_type = release_type;
		this.date_added = date_added;
		this.date_changed = date_changed;
		this.title = title;
		this.wikipedia_url = wikipedia_url;
		this.coverartarchive_thumb = coverartarchive_thumb;
		this.companies = companies;
		this.extra_artists = extra_artists;
		this.genres = genres;
		this.images = images;
		this.labels = labels;
		this.styles = styles;
		this.tracklist = tracklist;
		this.videos = videos;
		this.albumContainsArtists = albumContainsArtists;
		this.wikipedia_data = wikipedia_data;
		this.musicbrainz_uuid = musicbrainz_uuid;
		this.wikidata_id = wikidata_id;
		
	}

	// Getters and setters
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

	public String getYoutube_video_id_for_thumbnail() {
		return youtube_video_id_for_thumbnail;
	}

	public void setYoutube_video_id_for_thumbnail(String youtube_video_id_for_thumbnail) {
		this.youtube_video_id_for_thumbnail = youtube_video_id_for_thumbnail;
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

	public String getRelease_format_description() {
		return release_format_description;
	}

	public void setRelease_format_description(String release_format_description) {
		this.release_format_description = release_format_description;
	}

	public String getReleased() {
		return released;
	}

	public String getWikidata_id() {
	    return wikidata_id;
	}

	public void setWikidata_id(String wikidata_id) {
	    this.wikidata_id = wikidata_id;
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

	public String getRelease_type() {
		return release_type;
	}

	public void setRelease_type(String release_type) {
		this.release_type = release_type;
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

	public String getWikipedia_url() {
		return wikipedia_url;
	}

	public void setWikipedia_url(String wikipedia_url) {
		this.wikipedia_url = wikipedia_url;
	}

	public String getCoverartarchive_thumb() {
		return coverartarchive_thumb;
	}

	public void setCoverartarchive_thumb(String coverartarchive_thumb) {
		this.coverartarchive_thumb = coverartarchive_thumb;
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

    // Getter and setter
    public String getMusicbrainz_uuid() {
        return musicbrainz_uuid;
    }

    public void setMusicbrainz_uuid(String musicbrainz_uuid) {
        this.musicbrainz_uuid = musicbrainz_uuid;
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

	public List<Song> getSong() {
		return song;
	}

	public void setSong(List<Song> song) {
		this.song = song;
	}

	public String getWikipedia_data() {
		return wikipedia_data;
	}

	public void setWikipedia_data(String wikipedia_data) {
		this.wikipedia_data = wikipedia_data;
	}
	
	
}