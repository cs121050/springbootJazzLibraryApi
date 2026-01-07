package com.nicosarr.jazzLibraryAPI.BootStrapModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Instrument.Instrument;
import com.nicosarr.jazzLibraryAPI.Video.Video;
import com.nicosarr.jazzLibraryAPI.Type.Type;
import com.nicosarr.jazzLibraryAPI.Duration.Duration;
import com.nicosarr.jazzLibraryAPI.Quote.Quote;

@JacksonXmlRootElement(localName = "bootStrapModel")
public class BootStrapModel {

	private List<Quote> quoteList;
	private List<Artist> artistList;
	private List<Instrument> instrumentList;
	private List<Video> videoList;
	private List<Type> typeList;
	private List<Duration> durationList;
    private Map<String, Map<Integer, List<Artist>>> artistFilters;
    private Map<String, Map<Integer, List<Video>>> videoFilters;
    
    
    private Map<String, Integer> counts;
	

    
	public BootStrapModel(List<Quote>  quoteList, List<Artist> artistList, List<Instrument> instrumentList, List<Video> videoList,
			List<Type> typeList, List<Duration> durationList, Map<String, Map<Integer, List<Artist>>> artistFilters,
			Map<String, Map<Integer, List<Video>>> videoFilterst,  Map<String, Integer> counts) {
		super();
		this.quoteList = quoteList;
		this.artistList = artistList;
		this.instrumentList = instrumentList;
		this.videoList = videoList;
		this.typeList = typeList;
		this.durationList = durationList;
		this.artistFilters = artistFilters;
		this.videoFilters = videoFilters;
		this.counts = counts;
	}
	public BootStrapModel() {
		// TODO Auto-generated constructor stub
	}

	public List<Quote> getQuoteList() {
		return quoteList;
	}
	public void setQuoteList(List<Quote> quoteList) {
		this.quoteList = quoteList;
	}
	public List<Artist> getArtistList() {
		return artistList;
	}
	public void setArtistList(List<Artist> artistList) {
		this.artistList = artistList;
	}
	public List<Instrument> getInstrumentList() {
		return instrumentList;
	}
	public void setInstrumentList(List<Instrument> instrumentList) {
		this.instrumentList = instrumentList;
	}
	public List<Video> getVideoList() {
		return videoList;
	}
	public void setVideoList(List<Video> videoList) {
		this.videoList = videoList;
	}
	public List<Type> getTypeList() {
		return typeList;
	}
	public void setTypeList(List<Type> typeList) {
		this.typeList = typeList;
	}
	public List<Duration> getDurationList() {
		return durationList;
	}
	public void setDurationList(List<Duration> durationList) {
		this.durationList = durationList;
	}
	public Map<String, Map<Integer, List<Artist>>> getArtistFilters() {
		return artistFilters;
	}
	public void setArtistFilters(Map<String, Map<Integer, List<Artist>>> artistFilters) {
		this.artistFilters = artistFilters;
	}
	public Map<String, Map<Integer, List<Video>>> getVideoFilters() {
		return videoFilters;
	}
	public void setVideoFilters(Map<String, Map<Integer, List<Video>>> videoFilters) {
		this.videoFilters = videoFilters;
	}
	public Map<String, Integer> getCounts() {
		return counts;
	}
	public void setCounts(Map<String, Integer> counts) {
		this.counts = counts;
	}
	@Override
	public String toString() {
		return "BootStrapModel [quoteList=" + quoteList + ", artistList=" + artistList + ", instrumentList="
				+ instrumentList + ", videoList=" + videoList + ", typeList=" + typeList + ", durationList="
				+ durationList + ", artistFilters=" + artistFilters + ", videoFilters=" + videoFilters + ", counts="
				+ counts + "]";
	}



	


/*
 * {
  "quoteList": [
    {
      "quote_id": 1,
      "quote_text": "Jazz is the big brother of the blues...",
      "artist_id": 1
    }
  ],
  "artistList": [
    {
      "artist_id": 1,
      "artist_name": "Louis",
      "artist_surname": "Armstrong",
      "instrument_id": 3,
      "artist_video_count": 5,
      "artist_rank": 9
    }
  ],
  "instrumentList": [
    {
      "instrument_id": 3,
      "instrument_name": "Trumpet",
      "instrument_video_count": 150,
      "instrument_artist_count": 25
    }
  ],
  "videoList": [
    {
      "video_id": 123,
      "video_name": "Louis Armstrong Live in Paris",
      "duration_id": 2,
      "type_id": 1,
      "artistList": [
        {
          "artist_id": 1,
          "artist_name": "Louis",
          "artist_surname": "Armstrong"
        }
      ]
    }
  ],
  "typeList": [
    {
      "type_id": 1,
      "type_name": "Live Performance",
      "type_video_count": 450
    }
  ],
  "durationList": [
    {
      "duration_id": 2,
      "duration_name": "Medium (5-10 mins)",
      "duration_video_count": 1200
    }
  ],
  "artistFilters": {
    "byVideo": {
      "123": [
        {
          "artist_id": 1,
          "artist_name": "Louis",
          "artist_surname": "Armstrong"
        }
      ]
    },
    "byInstrument": {
      "3": [
        {
          "artist_id": 1,
          "artist_name": "Louis",
          "artist_surname": "Armstrong"
        }
      ]
    },
    "byType": {
      "1": [
        {
          "artist_id": 1,
          "artist_name": "Louis",
          "artist_surname": "Armstrong"
        }
      ]
    },
    "byDuration": {
      "2": [
        {
          "artist_id": 1,
          "artist_name": "Louis",
          "artist_surname": "Armstrong"
        }
      ]
    }
  },
  "videoFilters": {
    "byArtist": {
      "1": [
        {
          "video_id": 123,
          "video_name": "Louis Armstrong Live in Paris"
        }
      ]
    },
    "byInstrument": {
      "3": [
        {
          "video_id": 123,
          "video_name": "Louis Armstrong Live in Paris"
        }
      ]
    },
    "byType": {
      "1": [
        {
          "video_id": 123,
          "video_name": "Louis Armstrong Live in Paris"
        }
      ]
    },
    "byDuration": {
      "2": [
        {
          "video_id": 123,
          "video_name": "Louis Armstrong Live in Paris"
        }
      ]
    }
  },
  "counts": {
    "artists": 1500,
    "videos": 3500,
    "instruments": 14,
    "types": 4,
    "durations": 5,
    "quotes": 200
  }
}
 */


	

	
}
