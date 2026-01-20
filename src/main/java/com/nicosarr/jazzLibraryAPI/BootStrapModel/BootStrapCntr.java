package com.nicosarr.jazzLibraryAPI.BootStrapModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Artist.ArtistDTO;
import com.nicosarr.jazzLibraryAPI.Artist.ArtistRep;
import com.nicosarr.jazzLibraryAPI.Duration.Duration;
import com.nicosarr.jazzLibraryAPI.Duration.DurationDTO;
import com.nicosarr.jazzLibraryAPI.Duration.DurationRep;
import com.nicosarr.jazzLibraryAPI.Instrument.Instrument;
import com.nicosarr.jazzLibraryAPI.Instrument.InstrumentDTO;
import com.nicosarr.jazzLibraryAPI.Instrument.InstrumentRep;
import com.nicosarr.jazzLibraryAPI.Quote.Quote;
import com.nicosarr.jazzLibraryAPI.Quote.QuoteDTO;
import com.nicosarr.jazzLibraryAPI.Quote.QuoteRep;
import com.nicosarr.jazzLibraryAPI.Type.Type;
import com.nicosarr.jazzLibraryAPI.Type.TypeDTO;
import com.nicosarr.jazzLibraryAPI.Type.TypeRep;
import com.nicosarr.jazzLibraryAPI.Video.Video;
import com.nicosarr.jazzLibraryAPI.Video.VideoDTO;
import com.nicosarr.jazzLibraryAPI.Video.VideoRep;
import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtistDTO;
import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtistRep;

import jakarta.annotation.PostConstruct;

@RestController   //http://localhost:8080
@RequestMapping("bootStrapService")
public class BootStrapCntr {
    
    @Autowired
    private ArtistRep artistRepository;
    
    @Autowired
    private InstrumentRep instrumentRepository;
    
    @Autowired
    private TypeRep typeRepository;
    
    @Autowired
    private DurationRep durationRepository;
    
    @Autowired
    private VideoRep videoRepository;
    
    @Autowired
    private QuoteRep quoteRepository;
    
    @Autowired
    private VideoContainsArtistRep videoContainsArtistRepository;
	
	@GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public BootStrapDTO getAllData() {
        // Fetch all data from repositories
        List<InstrumentDTO> instrumentDTOs = instrumentRepository.retrieveAll();
        List<TypeDTO> typeDTOs = typeRepository.retrieveAll();
        List<DurationDTO> durationDTOs = durationRepository.retrieveAll();
        List<VideoDTO> videoDTOs = videoRepository.retrieveAll();
        List<ArtistDTO> artistDTOs = artistRepository.retrieveAll();
        List<QuoteDTO> quoteDTOs = quoteRepository.retrieveAll();
        List<VideoContainsArtistDTO> videoContainsArtistDTOs = videoContainsArtistRepository.retrieveAll();
        
        // Create and return bootstrap DTO
        return new BootStrapDTO(
            instrumentDTOs,
            typeDTOs,
            durationDTOs,
            videoDTOs,
            artistDTOs,
            quoteDTOs,
            videoContainsArtistDTOs
        );
    }
	
}
