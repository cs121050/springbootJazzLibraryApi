package com.nicosarr.jazzLibraryAPI.Artist;

import com.nicosarr.jazzLibraryAPI.Video.Video;
import com.nicosarr.jazzLibraryAPI.Video.VideoWithArtistDTO;
import java.util.ArrayList;
import java.util.List;

public class ArtistWithVideoDTO {
    private int artist_id;
    private String artist_name;
    private String artist_surname;
    private Integer artist_rank;
    private int instrument_id;
    private List<VideoWithArtistDTO> videos = new ArrayList<>();

    // Constructors
    public ArtistWithVideoDTO() {}

    public ArtistWithVideoDTO(int artist_id, String artist_name, String artist_surname, 
                     Integer artist_rank, int instrument_id) {
        this.artist_id = artist_id;
        this.artist_name = artist_name;
        this.artist_surname = artist_surname;
        this.artist_rank = artist_rank;
        this.instrument_id = instrument_id;
    }

 // Static factory method to convert from Entity
    public static ArtistWithVideoDTO fromEntity(Artist artist) {
    	ArtistWithVideoDTO dto = new ArtistWithVideoDTO(
            artist.getArtist_id(),
            artist.getArtist_name(),
            artist.getArtist_surname(),
            artist.getArtist_rank(),
            artist.getInstrument_id()
        );
        
        // Convert artists if they exist
        if (artist.getVideoContainsArtists() != null) {
            artist.getVideoContainsArtists().forEach(vca -> {
                if (vca.getVideo() != null) {
                    // Create a simplified Video DTO without artist to avoid circular reference
                    VideoWithArtistDTO videoDTO = new VideoWithArtistDTO(
                        vca.getVideo().getVideo_id(),
                        vca.getVideo().getVideo_name(),
                        vca.getVideo().getVideo_path(),
                        vca.getVideo().getLocation_id(),
                        vca.getVideo().getVideo_availability(),
                        vca.getVideo().getType_id(),
                        vca.getVideo().getDuration_id()
                    );
                    dto.getVideos().add(videoDTO);
                }
            });
        }

        return dto;
    }

    // Getters and Setters
    public int getArtist_id() {
        return artist_id;
    }

    public void setArtist_id(int artist_id) {
        this.artist_id = artist_id;
    }

    public String getArtist_name() {
        return artist_name;
    }

    public void setArtist_name(String artist_name) {
        this.artist_name = artist_name;
    }

    public String getArtist_surname() {
        return artist_surname;
    }

    public void setArtist_surname(String artist_surname) {
        this.artist_surname = artist_surname;
    }

    public Integer getArtist_rank() {
        return artist_rank;
    }

    public void setArtist_rank(Integer artist_rank) {
        this.artist_rank = artist_rank;
    }

    public int getInstrument_id() {
        return instrument_id;
    }

    public void setInstrument_id(int instrument_id) {
        this.instrument_id = instrument_id;
    }

    public List<VideoWithArtistDTO> getVideos() {
        return videos;
    }

    public void setVideos(List<VideoWithArtistDTO> videos) {
        this.videos = videos;
    }
}