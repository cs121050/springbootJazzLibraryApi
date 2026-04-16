package com.nicosarr.jazzLibraryAPI.Song;

import com.nicosarr.jazzLibraryAPI.Artist.ArtistDTO;

public class SongWithArtistDTO extends SongDTO {

    private ArtistDTO main_artist;   // full artist details, snake_case field

    public static SongWithArtistDTO fromEntity(Song song) {
        SongWithArtistDTO dto = new SongWithArtistDTO();
        // Copy base fields (using snake_case setters)
        dto.setSong_id(song.getSongId());
        dto.setMain_artist_id(song.getMainArtist().getArtist_id());
        dto.setRelated_artists(song.getRelatedArtists());
        if (song.getAlbum() != null) {
            dto.setAlbum_id(song.getAlbum().getAlbum_id());
        }
        dto.setSong_title(song.getSongTitle());
        dto.setDuration(song.getDuration());
        dto.setYt_videoid(song.getYtVideoId());
        dto.setVideo_availability(song.getVideo_availability());

        // Add full artist object (ensure ArtistDTO also uses snake_case if needed)
        if (song.getMainArtist() != null) {
            dto.setMain_artist(ArtistDTO.fromEntity(song.getMainArtist()));
        }
        return dto;
    }

    public ArtistDTO getMain_artist() { return main_artist; }
    public void setMain_artist(ArtistDTO main_artist) { this.main_artist = main_artist; }
}