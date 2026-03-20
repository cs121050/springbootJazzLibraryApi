package com.nicosarr.jazzLibraryAPI.Artist;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import jakarta.persistence.*;

import com.nicosarr.jazzLibraryAPI.Video.Video;
import com.nicosarr.jazzLibraryAPI.Instrument.Instrument;
import com.nicosarr.jazzLibraryAPI.Quote.Quote;
import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtist;

@Entity
@Table(name = "Artist")
@JacksonXmlRootElement(localName = "artist")
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artist_id")	
    private int artist_id;

    @Column(name = "discogs_id")	
    private Integer discogs_id;

    @Column(name = "artist_name")
    private String artist_name;

    @Column(name = "artist_surname")    
    private String artist_surname;

    @Column(name = "artist_rank")    
    private Integer artist_rank;    

    @Column(name = "musicbrainz_uuid")    
    private String musicbrainz_uuid; 

    @Column(name = "spotify_playlist_id")    
    private String spotify_playlist_id; 

    @Column(name = "wikipedia_url")
    private String wikipedia_url;

    // New fields for thumbnail and attribution
    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnail_url;

    @Column(name = "image_author")
    private String image_author;

    @Column(name = "image_license")
    private String image_license;

    @Column(name = "image_source_url", length = 1000)
    private String image_source_url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "instrument_id", 
        referencedColumnName = "instrument_id", 
        foreignKey = @ForeignKey(name = "FK_instrument_id")
    )
    @JsonIgnore
    private Instrument instrument;

    @Transient
    private int instrument_id; 

    @OneToMany(mappedBy = "artist", fetch = FetchType.LAZY)
    private List<VideoContainsArtist> videoContainsArtists = new ArrayList<>();

    @OneToMany(mappedBy = "artist", fetch = FetchType.LAZY)
    private List<Quote> quotes = new ArrayList<>(); 

    // Constructors
    public Artist() {}

    public Artist(int artist_id, String artist_name, String artist_surname, int instrument_id, Integer artist_rank,
                  String musicbrainz_uuid, String spotify_playlist_id, Integer discogs_id, String wikipedia_url,
                  String thumbnail_url, String image_author, String image_license, String image_source_url) {
        this.artist_id = artist_id;
        this.artist_name = artist_name;
        this.artist_surname = artist_surname;
        this.instrument_id = instrument_id;	   	
        this.artist_rank = artist_rank;	
        this.musicbrainz_uuid = musicbrainz_uuid;
        this.spotify_playlist_id = spotify_playlist_id;
        this.discogs_id = discogs_id;	 
        this.wikipedia_url = wikipedia_url;
        this.thumbnail_url = thumbnail_url;
        this.image_author = image_author;
        this.image_license = image_license;
        this.image_source_url = image_source_url;
    }

    public Artist(String artist_name, String artist_surname, int instrument_id, Integer artist_rank,
                  String musicbrainz_uuid, String spotify_playlist_id, Integer discogs_id, String wikipedia_url,
                  String thumbnail_url, String image_author, String image_license, String image_source_url) {
        this.artist_name = artist_name;
        this.artist_surname = artist_surname;
        this.instrument_id = instrument_id;	   	
        this.artist_rank = artist_rank;	   
        this.musicbrainz_uuid = musicbrainz_uuid;
        this.spotify_playlist_id = spotify_playlist_id;
        this.discogs_id = discogs_id;
        this.wikipedia_url = wikipedia_url;
        this.thumbnail_url = thumbnail_url;
        this.image_author = image_author;
        this.image_license = image_license;
        this.image_source_url = image_source_url;
    }

    // Keep the simpler constructor for cases where you don't have image data
    public Artist(String artist_name, String artist_surname, int instrument_id) {
        this.artist_name = artist_name;
        this.artist_surname = artist_surname;
        this.instrument_id = instrument_id;	   	
        this.artist_rank = 0;		
    }

    // Getters and setters (existing + new ones)
    public int getArtist_id() { return artist_id; }
    public void setArtist_id(int artist_id) { this.artist_id = artist_id; }

    public String getArtist_name() { return artist_name; }
    public void setArtist_name(String artist_name) { this.artist_name = artist_name; }

    public String getArtist_surname() { return artist_surname; }
    public void setArtist_surname(String artist_surname) { this.artist_surname = artist_surname; }

    public Instrument getInstrument() { return instrument; }
    public void setInstrument(Instrument instrument) { this.instrument = instrument; }

    public int getInstrument_id() {
        return this.instrument != null ? this.instrument.getInstrument_id() : instrument_id;
    }
    public void setInstrument_id(int instrument_id) { this.instrument_id = instrument_id; }

    public List<VideoContainsArtist> getVideoContainsArtists() { return videoContainsArtists; }
    public void setVideoContainsArtists(List<VideoContainsArtist> videoContainsArtists) { this.videoContainsArtists = videoContainsArtists; }

    public Integer getArtist_rank() { return artist_rank; }
    public void setArtist_rank(Integer artist_rank) { this.artist_rank = artist_rank; }

    @Transient
    @JsonProperty("videos")
    public List<Video> getVideos() {
        if (videoContainsArtists == null) return new ArrayList<>();
        return videoContainsArtists.stream()
            .map(VideoContainsArtist::getVideo)
            .collect(Collectors.toList());
    }

    public String getMusicbrainz_uuid() { return musicbrainz_uuid; }
    public void setMusicbrainz_uuid(String musicbrainz_uuid) { this.musicbrainz_uuid = musicbrainz_uuid; }

    public String getSpotify_playlist_id() { return spotify_playlist_id; }
    public void setSpotify_playlist_id(String spotify_playlist_id) { this.spotify_playlist_id = spotify_playlist_id; }

    public List<Quote> getQuotes() { return quotes; }
    public void setQuotes(List<Quote> quotes) { this.quotes = quotes; }

    public Integer getDiscogs_id() { return discogs_id; }
    public void setDiscogs_id(Integer discogs_id) { this.discogs_id = discogs_id; }

    public String getWikipedia_url() { return wikipedia_url; }
    public void setWikipedia_url(String wikipedia_url) { this.wikipedia_url = wikipedia_url; }

    // New getters/setters
    public String getThumbnail_url() { return thumbnail_url; }
    public void setThumbnail_url(String thumbnail_url) { this.thumbnail_url = thumbnail_url; }

    public String getImage_author() { return image_author; }
    public void setImage_author(String image_author) { this.image_author = image_author; }

    public String getImage_license() { return image_license; }
    public void setImage_license(String image_license) { this.image_license = image_license; }

    public String getImage_source_url() { return image_source_url; }
    public void setImage_source_url(String image_source_url) { this.image_source_url = image_source_url; }

    @Override
    public String toString() {
        return "Artist [artist_id=" + artist_id + ", discogs_id=" + discogs_id + ", artist_name=" + artist_name
                + ", artist_surname=" + artist_surname + ", artist_rank=" + artist_rank + ", musicbrainz_uuid="
                + musicbrainz_uuid + ", spotify_playlist_id=" + spotify_playlist_id + ", wikipedia_url=" + wikipedia_url
                + ", thumbnail_url=" + thumbnail_url + ", image_author=" + image_author + ", image_license=" + image_license
                + ", image_source_url=" + image_source_url + ", instrument=" + instrument + ", instrument_id=" + instrument_id
                + ", videoContainsArtists=" + videoContainsArtists + ", quotes=" + quotes + "]";
    }
}