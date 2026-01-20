package com.nicosarr.jazzLibraryAPI.VideoContainsArtist;

public class VideoContainsArtistDTO {
    private int artist_id;
    private int video_id;

    // Constructors
    public VideoContainsArtistDTO() {}

    public VideoContainsArtistDTO(int artist_id, int video_id) {
        this.video_id = video_id;
        this.artist_id = artist_id;
    }
    public static VideoContainsArtistDTO fromEntity(VideoContainsArtist entity) {
        VideoContainsArtistDTO dto = new VideoContainsArtistDTO(
        		entity.getArtist().getArtist_id(), 
        		entity.getVideo().getVideo_id()
        		);
        return dto;
    }

    // Getters and Setters
    public int getVideo_id() {
        return video_id;
    }

    public void setVideo_id(int video_id) {
        this.video_id = video_id;
    }

    public int getArtist_id() {
        return artist_id;
    }

    public void setArtist_id(int artist_id) {
        this.artist_id = artist_id;
    }

}
