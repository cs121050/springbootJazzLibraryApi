package com.nicosarr.jazzLibraryAPI.AlbumContainsArtist;

public class AlbumContainsArtistDTO {

    private int artistId;
    private int albumId;
    private int isMain;

    public AlbumContainsArtistDTO() {}

    public AlbumContainsArtistDTO(int artistId, int albumId, int isMain) {
        this.artistId = artistId;
        this.albumId = albumId;
        this.isMain = isMain;
    }

    // Factory method
    public static AlbumContainsArtistDTO fromEntity(AlbumContainsArtist entity) {
    	AlbumContainsArtistDTO dto = new AlbumContainsArtistDTO(
        		entity.getArtist().getArtist_id(), 
        		entity.getAlbum().getAlbum_id(),
        		entity.getIsMain()
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

	public int getAlbumId() {
		return albumId;
	}

	public void setAlbumId(int albumId) {
		this.albumId = albumId;
	}

	public int getIsMain() {
		return isMain;
	}

	public void setIsMain(int isMain) {
		this.isMain = isMain;
	}

 
}