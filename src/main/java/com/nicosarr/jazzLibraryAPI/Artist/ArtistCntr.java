package com.nicosarr.jazzLibraryAPI.Artist;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import java.sql.SQLException;

import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;

@RestController   //http://localhost:8080
@RequestMapping("artistService")
public class ArtistCntr {

    private ArrayList<Artist> artistList;   
    private final ArtistRep artistRep;
    
    public ArtistCntr(ArtistRep artistRep) {
    	this.artistRep = artistRep;
    }
    
    
    @GetMapping(value = "", produces = MediaType.APPLICATION_XML_VALUE)
    public String sayXMLHello() { 
        return "<?xml version=\"1.0\"?>" + "<artistService> artist controler... " + "</artistService>";
    }
  
    //@Transactional    
    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Artist> retrieveAll() { 
        return artistRep.retrieveAll();    
    }   
    
    @GetMapping(value = "/byTypeId/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<List<Artist>> retrieveAllByTypeId() { 
        return artistRep.retrieveAllGroupedByType();    
    }   
    
    @GetMapping(value = "/byInstrumentId/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<List<Artist>> retrieveAllInstrumentId() { 
        return artistRep.retrieveAllGroupedByInstrument();    
    }   
    
    @GetMapping(value = "/byDurationId/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<List<Artist>> retrieveAllDurationId() { 
        return artistRep.retrieveAllGroupedByDuration();    
    }   
    
    @GetMapping(value = "/byVideoId/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<List<Artist>> retrieveAllVideoId() { 
        return artistRep.retrieveAllGroupedByVideo();    
    }   
    
    
    
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String create(@RequestParam("artist_name") String name,
                               @RequestParam("artist_surname") String surname,
                               @RequestParam("instrument_id") int instrumentId,
                               @RequestParam("artist_video_count") int artistVideoCount,
                               @RequestParam("artist_rank") int artistRank) {
        Artist artist = new Artist(name,surname,instrumentId,artistVideoCount,artistRank);

        int result = artistRep.create(artist);
//        return ResponseEntity.status(result == 1 ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST)  //beatifull way for Error Handling
//                .body(result == 1 ? "Artist creation Success" : "Artist creation Failed");        
        return result == 1 ? "Artist creation Success" : "Artist creation Failed";
    }
    
	@PutMapping(value = "/updateInstrumentId", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String updateInstrumentId(@RequestParam("artist_id") int artistId,
	                           @RequestParam("instrument_id") int instrumentId){
	     artistRep.updateInstrumentId(artistId, instrumentId);
	     return "Artist updated successfully";  
    }  
	
	@PutMapping(value = "/updateVideoCount", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String updateVideoCount(@RequestParam("artist_id") int artistId,
							   @RequestParam("artist_video_count") int artistVideocount){
	     artistRep.updateVideoCount(artistId, artistVideocount); 
	     return "Artist updated successfully"; 
    }  	
	@PutMapping(value = "/update", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String updateArtistInstrumentId(@RequestParam("artist_id") int artistId,
    		                                @RequestParam("artist_name") String artistName,
                                            @RequestParam("artist_surname") String artistSurname,
                                            @RequestParam("instrument_id") int instrumentId,
											@RequestParam("artist_video_count") int artistVideocount,
				                            @RequestParam("artist_rank") int artistRank){
        Artist artist = new Artist(artistId, artistName, artistSurname, instrumentId, artistVideocount, artistRank);
        int result = artistRep.update(artist);  	
	    return result == 1 ?  "Artist updated successfully" : "Artist update Failed"; 
    }

    @GetMapping(value = "/byName/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Artist> retrieveArtistByName(@RequestParam("artist_name") String artistName,
                                       @RequestParam("artist_surname") String artistSurname) {
        return artistRep.retrieveArtistByName(artistName, artistSurname);
    }
    
    @GetMapping(value = "/byNameAndInstrumentId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public Artist retrieveArtistByNameAndInstrumentId(@RequestParam("artist_name") String artistName,
                                       @RequestParam("artist_surname") String artistSurname,
									   @RequestParam("instrument_id") int instrument_id){
        return artistRep.retrieveArtistByNameAndInstrumentId(artistName, artistSurname, instrument_id);
    }     
    

    @GetMapping(value = "/byId/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public Artist retrieveArtistById(@RequestParam("artist_id") int artistId) throws SQLException {
        return artistRep.retrieveArtistById(artistId);
    } 

    @GetMapping(value = "/byInstrumentId/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Artist> retrieveArtistAndVideoCountByInstrumentId(@RequestParam("instrument_id") int instrumentId) {
        return artistRep.retrieveArtistByInstrumentId(instrumentId);
    }

    @GetMapping(value = "/byDurationId/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Artist> retrieveArtistAndVideoCountByDurationId(@RequestParam("duration_id") int durationId) {
        return artistRep.retrieveByDurationId(durationId);
    }
    
    @GetMapping(value = "/byTypeId/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Artist> retrieveArtistAndVideoCountByTypeId(@RequestParam("type_id") int typeId) {
        return artistRep.retrieveByDurationId(typeId);
    }
         
//    @GetMapping(value = "/byQuoteId/search", produces = MediaType.APPLICATION_JSON_VALUE)
//    public Artist retrieveArtistByQuoteId(@RequestParam("quote_id") int quoteId) {
//        return artistRep.retrieveArtistByQuoteId(quoteId);
//    }
    
    @GetMapping(value = "/byInstrumentIdAndTypeId/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Artist> getArtistAndVideoCountByInstrumentAndType(@RequestParam("instrument_id") int instrumentId,
                                                                       @RequestParam("type_id") int typeId) {
        return artistRep.retrieveByInstrumentIdAndTypeId(instrumentId, typeId);
    }

    @GetMapping(value = "/byInstrumentIdAndDurationId/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Artist> getArtistAndVideoCountByInstrumentAndDuration(@RequestParam("instrument_id") int instrumentId,
                                                                                   @RequestParam("duration_id") int durationId) {
        return artistRep.retrieveByInstrumentIdAndDurationId(instrumentId, durationId);
    }
    
    
    @GetMapping(value = "/byInstrumentIdTypeIdAndDurationId/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Artist> retrieveArtistAndVideoCountByInstrumentTypeAndDuration(@RequestParam("instrument_id") int instrumentId,
																            @RequestParam("type_id") int typeId,
																            @RequestParam("duration_id") int durationId) {   
        return artistRep.retriveByInstrumentIdTypeIdAndDurationId(instrumentId, typeId, durationId);
    }
   
}
    


    
    
//    
//    @DELETE
//    @Path("/artist/{userid}")
//    @Produces(MediaType.TEXT_HTML)
//    public String deleteUser(@PathParam("artistId") int id) {
//        int result = jazzLibraryDAO.deleteArtist(id);
//        if (result == 1) {
//            return "artist deletion Success";
//        }
//        return "artist deletion Faild";
//    }
//    
//    @PUT
//    @Path("/artist")
//    @Produces(MediaType.TEXT_HTML)
//    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
//    public String updateArtist(@FormParam("id") int id,
//                               @FormParam("name") String name,
//                               @FormParam("surname") String surname,
//                               @FormParam("instrument_id") int instrument_id,
//                               @Context HttpServletResponse servletResponse) {
//        Artist artist = new Artist();
//        artist.setArtist_id(id);
//        artist.setArtist_name(name);
//        artist.setArtist_surname(surname);
//        artist.setInstrument_id(instrument_id);
//        int result = jazzLibraryDAO.updateArtist(artist);
//        if (result == 1) {
//            return "artist deletion success";
//        }
//        return "artist update Faild";
//    }


