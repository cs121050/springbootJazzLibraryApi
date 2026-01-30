package com.nicosarr.jazzLibraryAPI.Video;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.nicosarr.jazzLibraryAPI.Artist.ArtistWithVideoDTO;

public class VideoDTO {
    private int video_id;
    private String video_name;
    private String video_duration;
    private String video_path;
    private String location_id;
    private String video_availability;
    private int duration_id;
    private int type_id;
    
    // Constructors
    public VideoDTO() {}

	public VideoDTO(int video_id, String video_name, String video_duration, String video_path, String location_id,
					String video_availability, int type_id,  int duration_id) {
		this.video_id = video_id;
		this.video_name = video_name;
		this.video_duration = video_duration;
		this.video_path = video_path;
		this.location_id = location_id;
		this.video_availability = video_availability;
		this.type_id = type_id;
		this.duration_id = duration_id;
	}
    // Static factory method to convert from Entity
    public static VideoDTO fromEntity(Video video) {
        VideoDTO dto = new VideoDTO(
            video.getVideo_id(),
            video.getVideo_name(),
            video.getVideo_duration(),
            video.getVideo_path(),
            video.getLocation_id(),
            video.getVideo_availability(),
            video.getType_id(),
            video.getDuration_id()
        );

        return dto;
    }

    //
	public int getVideo_id() {
		return video_id;
	}

	public void setVideo_id(int video_id) {
		this.video_id = video_id;
	}

	public String getVideo_name() {
		return video_name;
	}

	public void setVideo_name(String video_name) {
		this.video_name = video_name;
	}

	public String getVideo_path() {
		return video_path;
	}

	public void setVideo_path(String video_path) {
		this.video_path = video_path;
	}

	public String getLocation_id() {
		return location_id;
	}

	public void setLocation_id(String location_id) {
		this.location_id = location_id;
	}

	public String getVideo_availability() {
		return video_availability;
	}

	public void setVideo_availability(String video_availability) {
		this.video_availability = video_availability;
	}

	public int getDuration_id() {
		return duration_id;
	}

	public void setDuration_id(int duration_id) {
		this.duration_id = duration_id;
	}

	public int getType_id() {
		return type_id;
	}

	public void setType_id(int type_id) {
		this.type_id = type_id;
	}

	public String getVideo_duration() {
		return video_duration;
	}

	public void setVideo_duration(String video_Duration) {
		this.video_duration = video_Duration;
	}
    
	

}