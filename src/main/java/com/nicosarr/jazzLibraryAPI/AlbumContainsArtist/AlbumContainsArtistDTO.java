package com.nicosarr.jazzLibraryAPI.AlbumContainsArtist;

public class AlbumContainsArtistDTO {

    private int artistId;
    private int discogsReleaseId;
    private boolean isMain;

    public AlbumContainsArtistDTO() {}

    public AlbumContainsArtistDTO(int artistId, int discogsReleaseId, boolean isMain) {
        this.artistId = artistId;
        this.discogsReleaseId = discogsReleaseId;
        this.isMain = isMain;
    }

    // Factory method
    public static AlbumContainsArtistDTO fromEntity(AlbumContainsArtist entity) {
    	AlbumContainsArtistDTO dto = new AlbumContainsArtistDTO(
        		entity.getArtist().getArtist_id(), 
        		entity.getAlbum().getAlbum_id(),
        		entity.isMain()
        		);
        return dto;
    }

    // Getters and Setters
	public int getArtistId() {
		return artistId;
	}

	public void setArtistId(int artistId) {
		this.artistId = artistId;
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

	public void setMain(boolean isMain) {
		this.isMain = isMain;
	}    
}