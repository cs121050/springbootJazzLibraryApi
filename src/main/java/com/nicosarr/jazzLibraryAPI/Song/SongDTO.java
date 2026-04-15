package com.nicosarr.jazzLibraryAPI.Song;


public class SongDTO {

    private int songId;
    private Integer mainArtistId;           // store only the FK ID
    private String mainArtistName;          // optional: denormalized for display
    private String relatedArtists;
    private Integer albumId;                // FK ID
    private String albumTitle;              // optional: denormalized
    private String songTitle;
    private String duration;
    private String ytVideoId;
    private String video_availability;

    public SongDTO() {}

    // Factory method from entity
    public static SongDTO fromEntity(Song song) {
        SongDTO dto = new SongDTO();
        dto.setSongId(song.getSongId());
        if (song.getMainArtist() != null) {
            dto.setMainArtistId(song.getMainArtist().getArtist_id());
            dto.setMainArtistName(song.getMainArtist().getArtist_name() + " " +
                                  song.getMainArtist().getArtist_surname());
        }
        dto.setRelatedArtists(song.getRelatedArtists());
        if (song.getAlbum() != null) {
            dto.setAlbumId(song.getAlbum().getAlbum_id());
            dto.setAlbumTitle(song.getAlbum().getTitle());
        }
        dto.setSongTitle(song.getSongTitle());
        dto.setDuration(song.getDuration());
        dto.setYtVideoId(song.getYtVideoId());
        dto.setVideo_availability(song.getVideo_availability());
        return dto;
    }

    // Getters and Setters (generate all)
    public int getSongId() { return songId; }
    public void setSongId(int songId) { this.songId = songId; }

    public Integer getMainArtistId() { return mainArtistId; }
    public void setMainArtistId(Integer mainArtistId) { this.mainArtistId = mainArtistId; }

    public String getMainArtistName() { return mainArtistName; }
    public void setMainArtistName(String mainArtistName) { this.mainArtistName = mainArtistName; }

    public String getRelatedArtists() { return relatedArtists; }
    public void setRelatedArtists(String relatedArtists) { this.relatedArtists = relatedArtists; }

    public Integer getAlbumId() { return albumId; }
    public void setAlbumId(Integer albumId) { this.albumId = albumId; }

    public String getAlbumTitle() { return albumTitle; }
    public void setAlbumTitle(String albumTitle) { this.albumTitle = albumTitle; }

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