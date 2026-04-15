package com.nicosarr.jazzLibraryAPI.Song;

import com.nicosarr.jazzLibraryAPI.Artist.ArtistDTO;

public class SongWithArtistDTO extends SongDTO {

    private ArtistDTO mainArtist;

    public SongWithArtistDTO() { super(); }

    public static SongWithArtistDTO fromEntity(Song song) {
        SongWithArtistDTO dto = new SongWithArtistDTO();
        // Copy base fields
        dto.setSongId(song.getSongId());
        dto.setRelatedArtists(song.getRelatedArtists());
        if (song.getAlbum() != null) {
            dto.setAlbumId(song.getAlbum().getAlbum_id());
            dto.setAlbumTitle(song.getAlbum().getTitle());
        }
        dto.setSongTitle(song.getSongTitle());
        dto.setDuration(song.getDuration());
        dto.setYtVideoId(song.getYtVideoId());

        // Convert main artist
        if (song.getMainArtist() != null) {
            ArtistDTO artistDTO = new ArtistDTO(
                song.getMainArtist().getArtist_id(),
                song.getMainArtist().getArtist_name(),
                song.getMainArtist().getArtist_surname(),
                song.getMainArtist().getArtist_rank(),
                song.getMainArtist().getInstrument_id(),
                song.getMainArtist().getMusicbrainz_uuid(),
                song.getMainArtist().getSpotify_playlist_id(),
                song.getMainArtist().getDiscogs_id(),
                song.getMainArtist().getWikipedia_url(),
                song.getMainArtist().getThumbnail_url(),
                song.getMainArtist().getImage_author(),
                song.getMainArtist().getImage_license(),
                song.getMainArtist().getImage_source_url(),
                song.getMainArtist().getWikipedia_data()
            );
            dto.setMainArtist(artistDTO);
        }
        return dto;
    }

    public ArtistDTO getMainArtist() { return mainArtist; }
    public void setMainArtist(ArtistDTO mainArtist) { this.mainArtist = mainArtist; }
}