package com.nicosarr.jazzLibraryAPI.Song;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nicosarr.jazzLibraryAPI.Album.Album;
import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import jakarta.persistence.*;

@Entity
@Table(name = "song")
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "song_id")
    private int songId;

    // Many-to-one relationship to Artist (main artist) – hidden from JSON
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_artist_id", nullable = false)
    @JsonIgnore
    private Artist mainArtist;

    // Transient field to hold the main artist ID for API usage
    @Transient
    private int main_artist_id;

    // Many-to-one relationship to Album – hidden from JSON
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    @JsonIgnore
    private Album album;

    // Transient field to hold the album ID for API usage
    @Transient
    private int album_id;

    @Column(name = "related_artists")
    private String relatedArtists;   // comma-separated artist IDs or names

    @Column(name = "song_title", nullable = false)
    private String songTitle;

    @Column(name = "duration")
    private String duration;         // e.g., "3:45" or seconds as string

    @Column(name = "yt_videoid")
    private String ytVideoId;

    @Column(name = "video_availability")
    private String video_availability;

    // Constructors following the Artist pattern (using transient IDs)
    public Song() {}

    public Song(int songId, int main_artist_id, int album_id, String relatedArtists,
                String songTitle, String duration, String ytVideoId, String video_availability) {
        this.songId = songId;
        this.main_artist_id = main_artist_id;
        this.album_id = album_id;
        this.relatedArtists = relatedArtists;
        this.songTitle = songTitle;
        this.duration = duration;
        this.ytVideoId = ytVideoId;
        this.video_availability = video_availability;
    }

    public Song(int main_artist_id, int album_id, String relatedArtists,
                String songTitle, String duration, String ytVideoId, String video_availability) {
        this.main_artist_id = main_artist_id;
        this.album_id = album_id;
        this.relatedArtists = relatedArtists;
        this.songTitle = songTitle;
        this.duration = duration;
        this.ytVideoId = ytVideoId;
        this.video_availability = video_availability;
    }

    // Getters and Setters

    public int getSongId() {
        return songId;
    }

    public void setSongId(int songId) {
        this.songId = songId;
    }

    // MainArtist relationship – used internally by JPA
    public Artist getMainArtist() {
        return mainArtist;
    }

    public void setMainArtist(Artist mainArtist) {
        this.mainArtist = mainArtist;
    }

    // Transient main_artist_id – used for JSON and temporary storage
    public int getMain_artist_id() {
        return this.mainArtist != null ? this.mainArtist.getArtist_id() : main_artist_id;
    }

    public void setMain_artist_id(int main_artist_id) {
        this.main_artist_id = main_artist_id;
    }

    // Album relationship – used internally by JPA
    public Album getAlbum() {
        return album;
    }

    public void setAlbum(Album album) {
        this.album = album;
    }

    // Transient album_id – used for JSON and temporary storage
    public int getAlbum_id() {
        return this.album != null ? this.album.getAlbum_id() : album_id;
    }

    public void setAlbum_id(int album_id) {
        this.album_id = album_id;
    }

    public String getRelatedArtists() {
        return relatedArtists;
    }

    public void setRelatedArtists(String relatedArtists) {
        this.relatedArtists = relatedArtists;
    }

    public String getSongTitle() {
        return songTitle;
    }

    public void setSongTitle(String songTitle) {
        this.songTitle = songTitle;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getYtVideoId() {
        return ytVideoId;
    }

    public void setYtVideoId(String ytVideoId) {
        this.ytVideoId = ytVideoId;
    }

    public String getVideo_availability() {
        return video_availability;
    }

    public void setVideo_availability(String video_availability) {
        this.video_availability = video_availability;
    }
}