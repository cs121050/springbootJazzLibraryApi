package com.nicosarr.jazzLibraryAPI.Duration;

import java.util.ArrayList;
import java.util.List;

import com.nicosarr.jazzLibraryAPI.Video.VideoDTO;
import com.nicosarr.jazzLibraryAPI.Video.VideoWithArtistDTO;

public class DurationWithVideoDTO {
	private int duration_id;
	private String duration_name;
    private List<VideoDTO> video = new ArrayList<>();
	
	public DurationWithVideoDTO() {
	}
	public DurationWithVideoDTO(int duration_id, String duration_name) {
		this.duration_id = duration_id;
		this.duration_name = duration_name;
	}

	 // Static factory method to convert from Entity
	    public static DurationWithVideoDTO fromEntity(Duration duration) {
	    	DurationWithVideoDTO dto = new DurationWithVideoDTO(
	    		duration.getDuration_id(),
	            duration.getDuration_name()
	        );

	        // Convert artists if they exist
	        if (duration.getVideo() != null) {
	            // Actually populate the video list
	            duration.getVideo().forEach(video -> {
	                dto.getVideo().add(VideoDTO.fromEntity(video));
	            });
	        }

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
	public List<VideoDTO> getVideo() {
		return video;
	}
	public void setVideo(List<VideoDTO> video) {
		this.video = video;
	}
		
	
	
}

