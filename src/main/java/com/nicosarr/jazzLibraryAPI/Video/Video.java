package com.nicosarr.jazzLibraryAPI.Video;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Type.Type;
import com.nicosarr.jazzLibraryAPI.Duration.Duration;
import com.nicosarr.jazzLibraryAPI.Quote.Quote;
import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtist;

@Entity
@Table(name = "Video")  // database tables
@JacksonXmlRootElement(localName = "video")
public class Video {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY if your DB supports auto-increment	
	private int video_id;
    
    @Column(name = "video_name")    
    private String video_name;
    
    @Column(name = "video_duration")    
    private String video_duration;
    
    @Column(name = "video_path")    
    private String video_path;
    
    @Column(name = "location_id")    
    private String location_id;
    
    @Column(name = "video_availability")    
    private String video_availability;

	@ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
    		name = "duration_id", 
    		referencedColumnName = "duration_id", 
    		foreignKey = @ForeignKey(name = "FK_duration_id")
    )
    @JsonIgnore
    private Duration duration; 
    @Transient
    private int duration_id;
    
	@ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
    		name = "type_id", 
    		referencedColumnName = "type_id", 
    		foreignKey = @ForeignKey(name = "FK_type_id")
    )
    @JsonIgnore
    private Type type; 
    @Transient    
    private int type_id;
    
 // Add this new relationship for quotes
    @OneToMany(mappedBy = "video", fetch = FetchType.LAZY)
    @JsonIgnore // To prevent circular references in JSON
    private List<Quote> quotes = new ArrayList<>();
    
    @OneToMany(mappedBy = "video", fetch = FetchType.LAZY)
    private List<VideoContainsArtist> videoContainsArtists = new ArrayList<>();

    public Video () {
    }

	public Video(int video_id, String video_name, String video_duration, String video_path, String location_id,
			String video_availability, Duration duration, int duration_id, Type type, int type_id,
			List<VideoContainsArtist> videoContainsArtists) {
		super();
		this.video_id = video_id;
		this.video_name = video_name;
		this.video_duration = video_duration;
		this.video_path = video_path;
		this.location_id = location_id;
		this.video_availability = video_availability;
		this.duration = duration;
		this.duration_id = duration_id;
		this.type = type;
		this.type_id = type_id;
		this.videoContainsArtists = videoContainsArtists;
	}
	
	public Video(int video_id, String video_name, String video_duration, String video_path, String location_id,
			String video_availability, Duration duration, int duration_id, Type type, int type_id,
			List<VideoContainsArtist> videoContainsArtists, List<Quote> quotes) {
		this(video_id, video_name, video_duration, video_path, location_id, video_availability, 
			 duration, duration_id, type, type_id, videoContainsArtists);
		this.quotes = quotes != null ? quotes : new ArrayList<>();
	}
	
	public String toString(){
        return "duration_id:" + duration_id + "#video_name:" + video_name + "#video_duration:" + video_duration
        	+ "#video_path:" + video_path	 + "#type_id:" + type_id + "#location_id:" + location_id
        	+ "#video_availability:" + video_availability;//  + "#containedArtistsToObjectString:" + containedArtistsToObjectString ;
    }   
    public String valuesToString(){
        return duration_id + "#" + video_name + "#" + video_duration
            	+ "#" + video_path	 + "#" + type_id + "#" + location_id
            	+ "#" + video_availability  ;//+ "#" + containedArtistsToObjectString ;
    }  
    
    
	public int getVideo_id() {
		return video_id;
	}
	public void setVideo_id(int video_id) {
		this.video_id = video_id;
	}
    // Add getter for DURATION_id only
    public int getDuration_id() {
        return this.duration != null ? this.duration.getDuration_id() : 0;
    }	
	public void setDuration_id(int duration_id) {
		this.duration_id = duration_id;
	}
	public String getVideo_name() {
		return video_name;
	}
	public void setVideo_name(String video_name) {
		this.video_name = video_name;
	}
	public String getVideo_duration() {
		return video_duration;
	}
	public void setVideo_duration(String video_duration) {
		this.video_duration = video_duration;
	}
	public String getVideo_path() {
		return video_path;
	}
	public void setVideo_path(String video_path) {
		this.video_path = video_path;
	}
	// Add getter for DURATION_id only
    public int getType_id() {
        return this.type != null ? this.type.getType_id() : 0;
    }	
	
	public void setType_id(int type_id) {
		this.type_id = type_id;
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
	public List<VideoContainsArtist> getVideoContainsArtists() {
		return videoContainsArtists;
	}
	public void setVideoContainsArtists(List<VideoContainsArtist> videoContainsArtists) {
		this.videoContainsArtists = videoContainsArtists;
	}
	// Add a transient field to get artists directly
    @Transient
    @JsonProperty("artists") // This will include artists in the JSON
    public List<Artist> getArtists() {
        if (videoContainsArtists == null) {
            return new ArrayList<>();
        }
        return videoContainsArtists.stream()
            .map(VideoContainsArtist::getArtist)
            .collect(Collectors.toList());
    }

	public Duration getDuration() {
		return duration;
	}

	public void setDuration(Duration duration) {
		this.duration = duration;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public List<Quote> getQuotes() {
		return quotes;
	}

	public void setQuotes(List<Quote> quotes) {
		this.quotes = quotes;
	}
    
}



