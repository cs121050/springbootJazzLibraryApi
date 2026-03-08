package com.nicosarr.jazzLibraryAPI.AlbumContainsArtist;

public class AlbumContainsArtistDTO {

    private int discogsArtistId;
    private int discogsReleaseId;
    private boolean isMain;

    public AlbumContainsArtistDTO() {}

    public AlbumContainsArtistDTO(int discogsArtistId, int discogsReleaseId, boolean isMain) {
        this.discogsArtistId = discogsArtistId;
        this.discogsReleaseId = discogsReleaseId;
        this.isMain = isMain;
    }

    // Factory method
    public static AlbumContainsArtistDTO fromEntity(AlbumContainsArtist entity) {
        return new AlbumContainsArtistDTO(
            entity.getDiscogsArtistId(),
            entity.getDiscogsReleaseId(),
            entity.isMain()
        );
    }

    // Getters and setters
    public int getDiscogsArtistId() {
        return discogsArtistId;
    }

    public void setDiscogsArtistId(int discogsArtistId) {
        this.discogsArtistId = discogsArtistId;
    }

    public int getDiscogsReleaseId() {
        return discogsReleaseId;
    }

    public void setDiscogsReleaseId(int discogsReleaseId) {
        this.discogsReleaseId = discogsReleaseId;
    }

    public boolean isMain() {
        return isMain;
    }

    public void setMain(boolean main) {
        isMain = main;
    }
}