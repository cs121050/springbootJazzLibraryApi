package com.nicosarr.jazzLibraryAPI.Album;

import com.nicosarr.jazzLibraryAPI.Artist.ArtistDTO;   // you must have a simple ArtistDTO
import java.util.ArrayList;
import java.util.List;

public class AlbumWithArtistDTO extends AlbumDTO {

    private List<ArtistDTO> artists = new ArrayList<>();

    public AlbumWithArtistDTO() {
        super();
    }

    public static AlbumWithArtistDTO fromEntity(Album album) {
        AlbumWithArtistDTO dto = new AlbumWithArtistDTO();
        // copy fields from AlbumDTO (manually or via a helper)
        dto.setAlbum_id(album.getAlbum_id());
        dto.setRelease_id(album.getRelease_id());
        dto.setMaster_id(album.getMaster_id());
        dto.setMaster_url(album.getMaster_url());
        dto.setRating_average(album.getRating_average());
        dto.setRating_count(album.getRating_count());
        dto.setYear(album.getYear());
        dto.setCountry(album.getCountry());
        dto.setReleased(album.getReleased());
        dto.setReleased_formatted(album.getReleased_formatted());
        dto.setNotes(album.getNotes());
        dto.setDate_added(album.getDate_added());
        dto.setDate_changed(album.getDate_changed());
        dto.setTitle(album.getTitle());
        dto.setUri(album.getUri());
        dto.setThumb(album.getThumb());
        dto.setCompanies(album.getCompanies());
        dto.setExtra_artists(album.getExtra_artists());
        dto.setGenres(album.getGenres());
        dto.setImages(album.getImages());
        dto.setLabels(album.getLabels());
        dto.setStyles(album.getStyles());
        dto.setTracklist(album.getTracklist());
        dto.setVideos(album.getVideos());

        // fill artists from the junction table
        if (album.getAlbumContainsArtists() != null) {
            album.getAlbumContainsArtists().forEach(aca -> {
                if (aca.getArtist() != null) {
                    ArtistDTO artistDTO = new ArtistDTO(
                        aca.getArtist().getArtist_id(),
                        aca.getArtist().getArtist_name(),
                        aca.getArtist().getArtist_surname(),
                        aca.getArtist().getArtist_rank(),
                        aca.getArtist().getInstrument_id(),
                        aca.getArtist().getMusicbrainz_uuid(),
                        aca.getArtist().getSpotify_playlist_id(),
                        aca.getArtist().getDiscogs_id(),
                        aca.getArtist().getWikipedia_url()
                    );
                    dto.getArtists().add(artistDTO);
                }
            });
        }
        return dto;
    }

    // getters and setters for artists
    public List<ArtistDTO> getArtists() {
        return artists;
    }

    public void setArtists(List<ArtistDTO> artists) {
        this.artists = artists;
    }
}