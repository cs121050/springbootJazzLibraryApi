package com.nicosarr.jazzLibraryAPI.Song;

public class SongDTO {

    private int songId;
    private int mainArtistId;          // required FK (matches DB not null)
    private String relatedArtists;
    private Integer albumId;           // optional FK (can be null)
    private String songTitle;
    private String duration;
    private String ytVideoId;
    private String video_availability;

    public SongDTO() {}

    // Optional: constructor for convenience
    public SongDTO(int songId, int mainArtistId, String relatedArtists, Integer albumId,
                   String songTitle, String duration, String ytVideoId, String video_availability) {
        this.songId = songId;
        this.mainArtistId = mainArtistId;
        this.relatedArtists = relatedArtists;
        this.albumId = albumId;
        this.songTitle = songTitle;
        this.duration = duration;
        this.ytVideoId = ytVideoId;
        this.video_availability = video_availability;
    }

    // Factory method from entity – now only sets IDs, no names
    public static SongDTO fromEntity(Song song) {
        SongDTO dto = new SongDTO();
        dto.setSongId(song.getSongId());
        // mainArtistId is required – safe to call getMainArtist().getArtist_id()
        dto.setMainArtistId(song.getMainArtist().getArtist_id());
        dto.setRelatedArtists(song.getRelatedArtists());
        // albumId may be null
        if (song.getAlbum() != null) {
            dto.setAlbumId(song.getAlbum().getAlbum_id());
        }
        dto.setSongTitle(song.getSongTitle());
        dto.setDuration(song.getDuration());
        dto.setYtVideoId(song.getYtVideoId());
        dto.setVideo_availability(song.getVideo_availability());
        return dto;
    }

    // Getters and setters
    public int getSongId() {
        return songId;
    }

    public void setSongId(int songId) {
        this.songId = songId;
    }

    public int getMainArtistId() {
        return mainArtistId;
    }

    public void setMainArtistId(int mainArtistId) {
        this.mainArtistId = mainArtistId;
    }

    public String getRelatedArtists() {
        return relatedArtists;
    }

    public void setRelatedArtists(String relatedArtists) {
        this.relatedArtists = relatedArtists;
    }

    public Integer getAlbumId() {
        return albumId;
    }

    public void setAlbumId(Integer albumId) {
        this.albumId = albumId;
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