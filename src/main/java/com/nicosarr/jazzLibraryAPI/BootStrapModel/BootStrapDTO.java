package com.nicosarr.jazzLibraryAPI.BootStrapModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Artist.ArtistDTO;
import com.nicosarr.jazzLibraryAPI.Instrument.Instrument;
import com.nicosarr.jazzLibraryAPI.Instrument.InstrumentDTO;
import com.nicosarr.jazzLibraryAPI.Video.Video;
import com.nicosarr.jazzLibraryAPI.Video.VideoDTO;
import com.nicosarr.jazzLibraryAPI.Type.Type;
import com.nicosarr.jazzLibraryAPI.Type.TypeDTO;
import com.nicosarr.jazzLibraryAPI.Duration.Duration;
import com.nicosarr.jazzLibraryAPI.Duration.DurationDTO;
import com.nicosarr.jazzLibraryAPI.Quote.Quote;
import com.nicosarr.jazzLibraryAPI.Quote.QuoteDTO;

@JacksonXmlRootElement(localName = "bootStrapModel")
public class BootStrapDTO {

	private List<InstrumentDTO> instrumentList;
	private List<TypeDTO> typeList;
	private List<DurationDTO> durationList;
	private List<VideoDTO> videoList;
	private List<ArtistDTO> artistList;	
	private List<QuoteDTO> quoteList;
	
	public BootStrapDTO() {
	}
	public BootStrapDTO(List<InstrumentDTO> instrumentList, List<TypeDTO> typeList, List<DurationDTO> durationList,
			List<VideoDTO> videoList, List<ArtistDTO> artistList, List<QuoteDTO> quoteList) {
		this.instrumentList = instrumentList;
		this.typeList = typeList;
		this.durationList = durationList;
		this.videoList = videoList;
		this.artistList = artistList;
		this.quoteList = quoteList;
	}
	public List<InstrumentDTO> getInstrumentList() {
		return instrumentList;
	}
	public void setInstrumentList(List<InstrumentDTO> instrumentList) {
		this.instrumentList = instrumentList;
	}
	public List<TypeDTO> getTypeList() {
		return typeList;
	}
	public void setTypeList(List<TypeDTO> typeList) {
		this.typeList = typeList;
	}
	public List<DurationDTO> getDurationList() {
		return durationList;
	}
	public void setDurationList(List<DurationDTO> durationList) {
		this.durationList = durationList;
	}
	public List<VideoDTO> getVideoList() {
		return videoList;
	}
	public void setVideoList(List<VideoDTO> videoList) {
		this.videoList = videoList;
	}
	public List<ArtistDTO> getArtistList() {
		return artistList;
	}
	public void setArtistList(List<ArtistDTO> artistList) {
		this.artistList = artistList;
	}
	public List<QuoteDTO> getQuoteList() {
		return quoteList;
	}
	public void setQuoteList(List<QuoteDTO> quoteList) {
		this.quoteList = quoteList;
	}
	
}
