package com.nicosarr.jazzLibraryAPI.Duration;

import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Artist.ArtistDTO;

public class DurationDTO {
	private int duration_id;
	private String duration_name;
	
	public DurationDTO() {
	}
	public DurationDTO(int duration_id, String duration_name) {
		this.duration_id = duration_id;
		this.duration_name = duration_name;
	}

	 // Static factory method to convert from Entity
	    public static DurationDTO fromEntity(Duration duration) {
	    	DurationDTO dto = new DurationDTO(
	    		duration.getDuration_id(),
	            duration.getDuration_name()
	        );
	        return dto;
	    }
	
	public int getDuration_id() {
		return duration_id;
	}
	public void setDuration_id(int duration_id) {
		this.duration_id = duration_id;
	}
	public String getDuration_name() {
		return duration_name;
	}
	public void setDuration_name(String duration_name) {
		this.duration_name = duration_name;
	}
		
	
	
}
