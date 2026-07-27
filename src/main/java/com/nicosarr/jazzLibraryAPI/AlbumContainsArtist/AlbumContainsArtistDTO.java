package com.nicosarr.jazzLibraryAPI.AlbumContainsArtist;

public class AlbumContainsArtistDTO {

    private int artist_id;
    private int album_id;
    private int is_main;

    public AlbumContainsArtistDTO() {}

    public AlbumContainsArtistDTO(int artistId, int albumId, int isMain) {
        this.artist_id = artistId;
        this.album_id = albumId;
        this.is_main = isMain;
    }

    // Factory method
    public static AlbumContainsArtistDTO fromEntity(AlbumContainsArtist entity) {
    	AlbumContainsArtistDTO dto = new AlbumContainsArtistDTO(
        		entity.getArtist().getArtist_id(), 
        		entity.getAlbum().getAlbum_id(),
        		entity.getIs_main()
        		);
        return dto;
    }

	public int getArtist_id() {
		return artist_id;
	}

	public void setArtist_id(int artist_id) {
		this.artist_id = artist_id;
	}

	public int getAlbum_id() {
		return album_id;
	}

	public void setAlbum_id(int album_id) {
		this.album_id = album_id;
	}

	public int getIs_main() {
		return is_main;
	}

	public void setIs_main(int is_main) {
		this.is_main = is_main;
	}

    // Getters and Setters

 
}