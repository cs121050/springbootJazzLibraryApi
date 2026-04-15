package com.nicosarr.jazzLibraryAPI.Song;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_artist_id", nullable = false)
    private Artist mainArtist;

    @Column(name = "related_artists")
    private String relatedArtists;   // comma-separated artist IDs or names

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    @Column(name = "song_title", nullable = false)
    private String songTitle;

    @Column(name = "duration")
    private String duration;         // e.g., "3:45" or seconds as string

    @Column(name = "yt_videoid")
    private String ytVideoId;
    
    @Column(name = "video_availability")    
    private String video_availability;

    // Constructors
    public Song() {}

    public Song(int songId, Artist mainArtist, String relatedArtists, Album album,
                String songTitle, String duration, String ytVideoId, String video_availability) {
        this.songId = songId;
        this.mainArtist = mainArtist;
        this.relatedArtists = relatedArtists;
        this.album = album;
        this.songTitle = songTitle;
        this.duration = duration;
        this.ytVideoId = ytVideoId;
        this.video_availability = video_availability;
    }

    // Getters and Setters
    public int getSongId() { return songId; }
    public void setSongId(int songId) { this.songId = songId; }

    public Artist getMainArtist() { return mainArtist; }
    public void setMainArtist(Artist mainArtist) { this.mainArtist = mainArtist; }

    public String getRelatedArtists() { return relatedArtists; }
    public void setRelatedArtists(String relatedArtists) { this.relatedArtists = relatedArtists; }

    public Album getAlbum() { return album; }
    public void setAlbum(Album album) { this.album = album; }

    public String getSongTitle() { return songTitle; }
    public void setSongTitle(String songTitle) { this.songTitle = songTitle; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getYtVideoId() { return ytVideoId; }
    public void setYtVideoId(String ytVideoId) { this.ytVideoId = ytVideoId; }

	public String getVideo_availability() {
		return video_availability;
	}

	public void setVideo_availability(String video_availability) {
		this.video_availability = video_availability;
	}
    
    
}