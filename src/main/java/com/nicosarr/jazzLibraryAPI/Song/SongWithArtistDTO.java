package com.nicosarr.jazzLibraryAPI.Song;

import com.nicosarr.jazzLibraryAPI.Artist.ArtistDTO;

public class SongWithArtistDTO extends SongDTO {

    private ArtistDTO mainArtist;   // full artist details

    public SongWithArtistDTO() {
        super();
    }

    public static SongWithArtistDTO fromEntity(Song song) {
        SongWithArtistDTO dto = new SongWithArtistDTO();
        
        // Set base fields from parent SongDTO
        dto.setSongId(song.getSongId());
        dto.setMainArtistId(song.getMainArtist().getArtist_id());   // required
        dto.setRelatedArtists(song.getRelatedArtists());
        if (song.getAlbum() != null) {
            dto.setAlbumId(song.getAlbum().getAlbum_id());
        }
        dto.setSongTitle(song.getSongTitle());
        dto.setDuration(song.getDuration());
        dto.setYtVideoId(song.getYtVideoId());
        dto.setVideo_availability(song.getVideo_availability());

        // Add full artist object
        if (song.getMainArtist() != null) {
            ArtistDTO artistDTO = ArtistDTO.fromEntity(song.getMainArtist());
            dto.setMainArtist(artistDTO);
        }
        
        return dto;
    }

    // Getter and setter for the nested artist
    public ArtistDTO getMainArtist() {
        return mainArtist;
    }

    public void setMainArtist(ArtistDTO mainArtist) {
        this.mainArtist = mainArtist;
    }
}