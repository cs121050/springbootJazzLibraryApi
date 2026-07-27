package com.nicosarr.jazzLibraryAPI.Song;

public class SongDTO {

    private int song_id;
    private int main_artist_id;          // required FK
    private String related_artists;
    private Integer album_id;            // optional FK
    private String song_title;
    private String duration;
    private String yt_videoid;
    private String video_availability;

    public SongDTO() {}

    // Factory method from entity – now sets snake_case fields
    public static SongDTO fromEntity(Song song) {
        SongDTO dto = new SongDTO();
        dto.setSong_id(song.getSongId());
        // mainArtist is required, fetched eagerly
        dto.setMain_artist_id(song.getMainArtist().getArtist_id());
        dto.setRelated_artists(song.getRelatedArtists());
        if (song.getAlbum() != null) {
            dto.setAlbum_id(song.getAlbum().getAlbum_id());
        }
        dto.setSong_title(song.getSongTitle());
        dto.setDuration(song.getDuration());
        dto.setYt_videoid(song.getYtVideoId());
        dto.setVideo_availability(song.getVideo_availability());
        return dto;
    }

    
    // Getters and Setters (snake_case names)
    public int getSong_id() { return song_id; }
    public void setSong_id(int song_id) { this.song_id = song_id; }

    public int getMain_artist_id() { return main_artist_id; }
    public void setMain_artist_id(int main_artist_id) { this.main_artist_id = main_artist_id; }

    public String getRelated_artists() { return related_artists; }
    public void setRelated_artists(String related_artists) { this.related_artists = related_artists; }

    public Integer getAlbum_id() { return album_id; }
    public void setAlbum_id(Integer album_id) { this.album_id = album_id; }

    public String getSong_title() { return song_title; }
    public void setSong_title(String song_title) { this.song_title = song_title; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getYt_videoid() { return yt_videoid; }
    public void setYt_videoid(String yt_videoid) { this.yt_videoid = yt_videoid; }

    public String getVideo_availability() { return video_availability; }
    public void setVideo_availability(String video_availability) { this.video_availability = video_availability; }
}