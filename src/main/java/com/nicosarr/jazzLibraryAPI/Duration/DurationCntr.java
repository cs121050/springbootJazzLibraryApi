package com.nicosarr.jazzLibraryAPI.Duration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.PutMapping;

import org.springframework.http.MediaType;
import java.util.ArrayList;
import java.util.List;

@RestController   //http://localhost:8080
@RequestMapping("durationService")
public class DurationCntr {
	
    private ArrayList<Duration> durationList;   
    private final DurationRep durationRep;
    
    public DurationCntr(DurationRep durationRep) {
    	this.durationRep = durationRep;
    }
    
    @GetMapping(value = "", produces = MediaType.APPLICATION_XML_VALUE)
    public String sayXMLHello() { 
        return "<?xml version=\"1.0\"?>" + "<artistService> duration controler... " + "</artistService>";
    }
    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DurationDTO> retrieveAll() { 
        return durationRep.retrieveAll();    
    }     
    @GetMapping(value = "/allWithVideo", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DurationWithVideoDTO> retrieveAllWithVideo() { 
        return durationRep.retrieveAllWithVideo();    
    }     
    //JUMP: Duration -> video -> videocontainartist -> artist 
    @GetMapping(value = "/allWithArtists", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DurationWithArtistDTO> retrieveAllWithArtists() { 
        return durationRep.retrieveAllWithArtist();    
    }
}
//    
//    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
//    public String create(@RequestParam("duration_name") String durationName,
//                               @RequestParam("duration_video_count") int durationVideoCount) {
//    	Duration duration = new Duration(durationName,durationVideoCount);
//
//        int result = durationRep.create(duration);
////        return ResponseEntity.status(result == 1 ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST)  //beatifull way for Error Handling
////                .body(result == 1 ? "Duration creation Success" : "Duration creation Failed");        
//        return result == 1 ? "Duration creation Success" : "Duration creation Failed";
//    }
//    
//    //update    
//    @PutMapping(value = "/update", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
//    public String update(@RequestParam("duration_id") int durationId,
//	                           @RequestParam("duration_name") String durationName,
//	                           @RequestParam("duration_video_count") int durationVideoCount){
//    	Duration duration = new Duration(durationId, durationName, durationVideoCount);
//
//    	int result = durationRep.update(duration);    	
//	     return result == 1 ?  "Duration updated successfully" : "Duration update Failed";  
//    }  
//    
//    @PutMapping(value = "/updateByName", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
//    public String updateDurationName(@RequestParam("duration_id") int durationId,
//	                           @RequestParam("duration_name") String durationName){
//	     durationRep.updateDurationName(durationId, durationName);
//	     return "Artist updated successfully";  
//    }  
//    
//    //updateDurationVideoCount
//    @PutMapping(value = "/updateByVideoCount", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
//    public String updateDurationVideoCount(@RequestParam("duration_id") int durationId,
//	                           @RequestParam("duration_video_count") int durationVideoCount){
//	     durationRep.updateDurationVideoCount(durationId, durationVideoCount);
//	     return "Artist updated successfully";  
//    }  
//    
//    
//    @GetMapping(value = "/byName/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public Duration retrieveDurationByName(@RequestParam("duration_name") String durationName) {
//        return durationRep.retrieveDurationByName(durationName);
//    }
//        
//    @GetMapping(value = "/byId/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public Duration retrieveDurationById(@RequestParam("duration_id") int durationId) {
//        return durationRep.retrieveDurationById(durationId);
//    }   
//    
//    
//    
//    
//    
//    
//    
//    @GetMapping(value = "/byTypeId/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public List<Duration> retrieveDurationByTypeId(@RequestParam("type_id") int typeId) {
//        return durationRep.retrieveDurationByTypeId(typeId);
//    }  
//    
//    @GetMapping(value = "/byInstrumentId/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public List<Duration> retrieveDurationByInstrumenId(@RequestParam("instrument_id") int instrumentId) {
//        return durationRep.retrieveDurationByInstrumentId(instrumentId);
//    } 
//    
//    @GetMapping(value = "/byTypeIdAndInstrumentId/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public List<Duration> retrieveDurationByTypeIdAndInstrumentId(@RequestParam("type_id") int typeId,
//    														@RequestParam("instrument_id") int instrumentId){
//        return durationRep.retrieveDurationByTypeIdAndInstrumentId(typeId, instrumentId);
//    }    
//
//    @GetMapping(value = "/byArtistId/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public List<Duration> retrieveDurationByArtistId(@RequestParam("artist_id") int artistId) {
//        return durationRep.retrieveDurationByArtistId(artistId);
//    }     
//    
//    @GetMapping(value = "/byTypeIdAndArtistId/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public List<Duration> retrieveTypeIdAndArtistId(@RequestParam("type_id") int typeId,
//												@RequestParam("artist_id") int artistId){
//        return durationRep.retrieveTypeIdAndArtistId(typeId, artistId);
//    }    
/*
 * 


    
    
    //nothing
    @GET
    @Path("/durationAndVideoCount_By_type_id_And_Artist_id/search")
    @Produces(MediaType.APPLICATION_JSON)
    public List<DurationAndVideoCount> retriveDurationAndVideoCount_By_type_id_And_Artist_id(@QueryParam("nothing")int nothing,
                                                                                            @QueryParam("type_id")int type_id,
                                                                                           @QueryParam("artist_id")int artist_id) {
        return jazzLibraryDAO.retriveDurationAndVideoCount_By_type_id_And_Artist_id(nothing,type_id,artist_id);
    }
 *     
 *     */



