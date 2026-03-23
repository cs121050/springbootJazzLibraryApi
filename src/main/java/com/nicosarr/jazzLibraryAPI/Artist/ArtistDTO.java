package com.nicosarr.jazzLibraryAPI.Artist;

public class ArtistDTO {
    private int artist_id;
    private String artist_name;
    private String artist_surname;
    private Integer artist_rank;
    private int instrument_id;
    private String musicbrainz_uuid;
    private String spotify_playlist_id;
    private Integer discogs_id;
    private String wikipedia_url;
    // New fields
    private String thumbnail_url;
    private String image_author;
    private String image_license;
    private String image_source_url;
    private String wikipedia_data;

    public ArtistDTO() {}

    public ArtistDTO(int artist_id, String artist_name, String artist_surname, 
                     Integer artist_rank, int instrument_id, String musicbrainz_uuid,
                     String spotify_playlist_id, Integer discogs_id, String wikipedia_url,
                     String thumbnail_url, String image_author, String image_license,
                     String image_source_url, String wikipedia_data) {
        this.artist_id = artist_id;
        this.artist_name = artist_name;
        this.artist_surname = artist_surname;
        this.artist_rank = artist_rank;
        this.instrument_id = instrument_id;
        this.musicbrainz_uuid = musicbrainz_uuid;
        this.spotify_playlist_id = spotify_playlist_id;
        this.discogs_id = discogs_id;
        this.wikipedia_url = wikipedia_url;
        this.thumbnail_url = thumbnail_url;
        this.image_author = image_author;
        this.image_license = image_license;
        this.image_source_url = image_source_url;
        this.wikipedia_data = wikipedia_data;
    }

    public static ArtistDTO fromEntity(Artist artist) {
        return new ArtistDTO(
            artist.getArtist_id(),
            artist.getArtist_name(),
            artist.getArtist_surname(),
            artist.getArtist_rank(),
            artist.getInstrument_id(),
            artist.getMusicbrainz_uuid(),
            artist.getSpotify_playlist_id(),
            artist.getDiscogs_id(),
            artist.getWikipedia_url(),
            artist.getThumbnail_url(),
            artist.getImage_author(),
            artist.getImage_license(),
            artist.getImage_source_url(),
            artist.getWikipedia_data()
        );
    }

    // Getters and setters (existing + new ones)
    public int getArtist_id() { return artist_id; }
    public void setArtist_id(int artist_id) { this.artist_id = artist_id; }

    public String getArtist_name() { return artist_name; }
    public void setArtist_name(String artist_name) { this.artist_name = artist_name; }

    public String getArtist_surname() { return artist_surname; }
    public void setArtist_surname(String artist_surname) { this.artist_surname = artist_surname; }

    public Integer getArtist_rank() { return artist_rank; }
    public void setArtist_rank(Integer artist_rank) { this.artist_rank = artist_rank; }

    public int getInstrument_id() { return instrument_id; }
    public void setInstrument_id(int instrument_id) { this.instrument_id = instrument_id; }

    public String getMusicbrainz_uuid() { return musicbrainz_uuid; }
    public void setMusicbrainz_uuid(String musicbrainz_uuid) { this.musicbrainz_uuid = musicbrainz_uuid; }

    public String getSpotify_playlist_id() { return spotify_playlist_id; }
    public void setSpotify_playlist_id(String spotify_playlist_id) { this.spotify_playlist_id = spotify_playlist_id; }

    public Integer getDiscogs_id() { return discogs_id; }
    public void setDiscogs_id(Integer discogs_id) { this.discogs_id = discogs_id; }

    public String getWikipedia_url() { return wikipedia_url; }
    public void setWikipedia_url(String wikipedia_url) { this.wikipedia_url = wikipedia_url; }

    public String getThumbnail_url() { return thumbnail_url; }
    public void setThumbnail_url(String thumbnail_url) { this.thumbnail_url = thumbnail_url; }

    public String getImage_author() { return image_author; }
    public void setImage_author(String image_author) { this.image_author = image_author; }

    public String getImage_license() { return image_license; }
    public void setImage_license(String image_license) { this.image_license = image_license; }

    public String getImage_source_url() { return image_source_url; }
    public void setImage_source_url(String image_source_url) { this.image_source_url = image_source_url; }

	public String getWikipedia_data() {
		return wikipedia_data;
	}

	public void setWikipedia_data(String wikipedia_data) {
		this.wikipedia_data = wikipedia_data;
	}
    
    
}