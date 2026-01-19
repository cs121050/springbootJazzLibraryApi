package com.nicosarr.jazzLibraryAPI.Duration;
import com.nicosarr.jazzLibraryAPI.Artist.ArtistDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DurationWithArtistDTO {
    private int duration_id;
    private String duration_name;
    private List<ArtistDTO> artists = new ArrayList<>();
    
    
	public DurationWithArtistDTO() {
	}
	public DurationWithArtistDTO(int duration_id, String duration_name) {
		this.duration_id = duration_id;
		this.duration_name = duration_name;
	}
	// Static factory method to convert from Entity
    public static DurationWithArtistDTO fromEntity(Duration duration) {
        DurationWithArtistDTO dto = new DurationWithArtistDTO(
            duration.getDuration_id(),
            duration.getDuration_name()
        );
        
        // Collect unique artists from all videos
        if (duration.getVideo() != null) {
            List<ArtistDTO> allArtists = new ArrayList<>();
            
            duration.getVideo().forEach(video -> {
                if (video.getVideoContainsArtists() != null) {
                    video.getVideoContainsArtists().forEach(vca -> {
                        if (vca.getArtist() != null) {
                            // Create ArtistDTO without videos to avoid circular reference
                            ArtistDTO artistDTO = new ArtistDTO(
                                vca.getArtist().getArtist_id(),
                                vca.getArtist().getArtist_name(),
                                vca.getArtist().getArtist_surname(),
                                vca.getArtist().getArtist_rank(),
                                vca.getArtist().getInstrument_id()
                            );
                            allArtists.add(artistDTO);
                        }
                    });
                }
            });
            
            // Remove duplicates (artists appearing in multiple videos)
            dto.setArtists(allArtists.stream()
                .distinct()
                .collect(Collectors.toList()));
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
	public List<ArtistDTO> getArtists() {
		return artists;
	}
	public void setArtists(List<ArtistDTO> artists) {
		this.artists = artists;
	}
    
    
}