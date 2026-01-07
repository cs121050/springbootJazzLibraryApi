package com.nicosarr.jazzLibraryAPI.Video;


import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nicosarr.jazzLibraryAPI.Artist.Artist;




@RestController   //http://localhost:8080
@RequestMapping("videoService")
public class VideoCntr {


    private ArrayList<Video> videoList;   
    private final VideoRep videoRep;

    
    public VideoCntr(VideoRep videoRep) {
    	this.videoRep = videoRep;
    }
    
    @GetMapping(value = "", produces = MediaType.APPLICATION_XML_VALUE)
    public String sayXMLHello() { 
        return "<?xml version=\"1.0\"?>" + "<videoService> video controler... " + "</videoService>";
    }
    
    @Transactional   
    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveAll() { 
        return videoRep.retrieveAll();    
    }
    
    @GetMapping(value = "/byDurationId/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<List<Video>> retrieveAllDurationId() { 
        return videoRep.retrieveAllGroupedByDuration();    
    }   
    
    @GetMapping(value = "/byInstrumentId/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<List<Video>> retrieveAllInstrumentId() { 
        return videoRep.retrieveAllGroupedByInstrument();    
    }   
    
    @GetMapping(value = "/byTypeId/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<List<Video>> retrieveAllTypeId() { 
        return videoRep.retrieveAllGroupedByType();    
    }   
    
    @GetMapping(value = "/byArtistId/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<List<Video>> retrieveAllArtistId() { 
        return videoRep.retrieveAllGroupedByArtist();    
    }   
    
    
    
    
    
    
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String create(@RequestParam("video_name") String videoName,
                        @RequestParam("video_duration") String videoDuration,
    				   @RequestParam("duration_id") int durationId,
						@RequestParam("video_path") String videoPath,
					   @RequestParam("type_id") int typeId,
					  @RequestParam("location_id") String locationId,
					 @RequestParam("video_availability") String videoAvailability) {
    	Video video = new Video(durationId, videoName, videoDuration, videoPath,
    			typeId, locationId, videoAvailability );

        int result = videoRep.create(video);
//        return ResponseEntity.status(result == 1 ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST)  //beatifull way for Error Handling
//                .body(result == 1 ? "Quote creation Success" : "Quote creation Failed");        
        return result == 1 ? "Video creation Success" : "Video creation Failed";
    }
    
    @PutMapping(value = "/update", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String update(@RequestParam("video_id") int videoId,
    		            @RequestParam("video_name") String videoName,
			            @RequestParam("video_duration") String videoDuration,
					   @RequestParam("duration_id") int durationId,
						@RequestParam("video_path") String videoPath,
					   @RequestParam("type_id") int typeId,
					  @RequestParam("location_id") String locationId,
					 @RequestParam("video_availability") String videoAvailability){
    	Video video = new Video(videoId, durationId, videoName, videoDuration, videoPath, typeId, locationId, videoAvailability);
    	int result = videoRep.update(video);    	
	     return result == 1 ?  "Video updated successfully" : "Video update Failed";  
    }  
    
    @GetMapping(value = "/allVideoNames", produces = MediaType.APPLICATION_JSON_VALUE)
    public String[] retrieveAllVideoNames() { 
        return videoRep.retrieveAllVideoNames();    
    } 

    @GetMapping(value = "/byId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public Video retrieveVideoById(@RequestParam("video_id") int videoId) {
        return videoRep.retrieveVideoById(videoId);
    }         
    
    @GetMapping(value = "/byName/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByName(@RequestParam("video_name") String videoName) {
        return videoRep.retrieveVideoByName(videoName);
    } 
 
    
    @GetMapping(value = "/byArtistId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByArtistId(@RequestParam("artist_id") int artistId) {
        return videoRep.retrieveVideoByArtistId(artistId);
    } 
    
    @GetMapping(value = "/byInstrumentId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByInstrumentId(@RequestParam("instrument_id") int instrumentId) {
        return videoRep.retrieveVideoByInstrumentId(instrumentId);
    }     

    @GetMapping(value = "/byTypeId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByTypeId(@RequestParam("type_id") int typeId) {
        return videoRep.retrieveVideoByTypeId(typeId);
    }  
    
    @GetMapping(value = "/byDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByDurationId(@RequestParam("duration_id") int durationId) {
        return videoRep.retrieveVideoByDurationId(durationId);
    }      

    @GetMapping(value = "/byInstrumentIdAndTypeId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByInstrumentIdAndTypeId(@RequestParam("instrument_id") int instrumentId,
    		                                              @RequestParam("type_id") int typeId ) {
        return videoRep.retrieveVideoByInstrumentIdAndTypeId(instrumentId, typeId);
    }    
    
    @GetMapping(value = "/byInstrumentIdAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByInstrumentIdAndDurationId(@RequestParam("instrument_id") int instrumentId,
    		                                              @RequestParam("duration_id") int durationId ) {
        return videoRep.retrieveVideoByInstrumentIdAndDurationId(instrumentId, durationId);
    }      
    
    @GetMapping(value = "/byTypeIdAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByTypeIdAndDurationId(@RequestParam("type_id") int typeId,
    		                                              @RequestParam("duration_id") int durationId ) {
        return videoRep.retrieveVideoByTypeIdAndDurationId(typeId, durationId);
    }        
    
    @GetMapping(value = "/byInstrumentIdAndTypeIdAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByInstrumentIdAndTypeIdAndDurationId(@RequestParam("instrument_id") int InstrumentId,
    													  @RequestParam("type_id") int typeId,
    		                                              @RequestParam("duration_id") int durationId ) {
        return videoRep.retrieveVideoByInstrumentIdAndTypeIdAndDurationId(InstrumentId, typeId, durationId);
    }        
        
    @GetMapping(value = "/byArtistIdAndTypeId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByArtistIdAndTypeId( @RequestParam("artist_id") int artistId,
    		                                              @RequestParam("type_id") int typeId ) {
        return videoRep.retrieveVideoByArtistIdAndTypeId(artistId, typeId);
    }     
    
    @GetMapping(value = "/byArtistIdAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByArtistIdAndDurationId( @RequestParam("artist_id") int artistId,
    		                                              @RequestParam("duration_id") int durationId ) {
        return videoRep.retrieveVideoByArtistIdAndDurationId(artistId, durationId);
    } 
    
    @GetMapping(value = "/byArtistIdAndTypeIdAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByArtistIdAndTypeIdAndDurationId( @RequestParam("artist_id") int artistId,
    		                                              @RequestParam("type_id") int typeId,
    		                                              @RequestParam("duration_id") int durationId ) {
        return videoRep.retrieveVideoByArtistIdAndTypeIdAndDurationId(artistId, typeId, durationId);
    }    
    
    @GetMapping(value = "/byArtistNameAndArtistSurname/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByArtistNameAndArtistSurname( @RequestParam("artist_name") String artistName,
    		                                              @RequestParam("artist_surname") String artistSurname ) {
        return videoRep.retrieveVideoByArtistNameAndArtistSurname(artistName, artistSurname);
    }   
    
    @GetMapping(value = "/byVideoPath/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByVideoPath( @RequestParam("video_path") String videoPath ) {
        return videoRep.retrieveVideoByVideoPath(videoPath);
    }      
    
    @GetMapping(value = "/random5Video/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retriveRandomVideos( @RequestParam("count") int countOfRandVideos ) {
        return videoRep.retriveRandomVideos(countOfRandVideos);
    }   
    
    
    
    
    
    @GetMapping(value = "/byNameAndTypeId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByNameAndTypeId(@RequestParam("video_name") String videoName,
												@RequestParam("type_id") int typeId) {
        return videoRep.retrieveVideoByNameAndTypeId(videoName, typeId);
    } 
    
    @GetMapping(value = "/byNameAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByNameAndDurationId(@RequestParam("video_name") String videoName,
										@RequestParam("duration_id") int durationId) {
        return videoRep.retrieveVideoByNameAndDurationId(videoName, durationId);
    } 
    
    @GetMapping(value = "/byNameAndInstrumentId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByNameAndInstrumentId(@RequestParam("video_name") String videoName,
										@RequestParam("instrument_id") int instrumentId) {
        return videoRep.retrieveVideoByNameAndInstrumentId(videoName, instrumentId);
    }  
    
    @GetMapping(value = "/byNameAndArtistId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByNameAndArtistId(@RequestParam("video_name") String videoName,
										@RequestParam("artist_id") int artistId) {
        return videoRep.retrieveVideoByNameAndArtistId(videoName, artistId);
    }   
    
    
    
    @GetMapping(value = "/byNameAndInstrumentIdAndTypeIdAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByNameAndInstrumentIdAndTypeIdAndDurationId(@RequestParam("video_name") String videoName,
										@RequestParam("instrument_id") int instrumentId,
										@RequestParam("type_id") int typeId,
										@RequestParam("duration_id") int durationId) {
        return videoRep.retrieveVideoByNameAndInstrumentIdAndTypeIdAndDurationId(videoName, instrumentId, typeId, durationId);
    } 
    
    @GetMapping(value = "/byNameAndInstrumentIdAndTypeId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByNameAndInstrumentIdAndTypeId(@RequestParam("video_name") String videoName,
										@RequestParam("instrument_id") int instrumentId,
										@RequestParam("type_id") int typeId) {
        return videoRep.retrieveVideoByNameAndInstrumentIdAndTypeId(videoName, instrumentId ,typeId);
    }  
    @GetMapping(value = "/byNameAndInstrumentIdAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByNameAndInstrumentIdAndDurationId(@RequestParam("video_name") String videoName,
										@RequestParam("instrument_id") int instrumentId,
										@RequestParam("duration_id") int durationId) {
        return videoRep.retrieveVideoByNameAndInstrumentIdAndDurationId(videoName, instrumentId ,durationId);
    }     
    
    
    
    @GetMapping(value = "/byNameAndArtistIdAndTypeIdAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByNameAndArtistIdAndTypeIdAndDurationId(@RequestParam("video_name") String videoName,
										@RequestParam("artist_id") int artistId,
										@RequestParam("type_id") int typeId,
										@RequestParam("duration_id") int durationId) {
        return videoRep.retrieveVideoByNameAndArtistIdAndTypeIdAndDurationId(videoName, artistId, typeId, durationId);
    } 
    
    @GetMapping(value = "/byNameAndArtistIdAndTypeId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByNameAndArtistIdAndTypeId(@RequestParam("video_name") String videoName,
															@RequestParam("artist_id") int artistId,
															@RequestParam("type_id") int typeId) {
        return videoRep.retrieveVideoByNameAndArtistIdAndTypeId(videoName,artistId,typeId);
    }  
    @GetMapping(value = "/byNameAndArtistIdAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Video> retrieveVideoByNameAndArtistIdAndDurationId(@RequestParam("video_name") String videoName,
															@RequestParam("artist_id") int artistId,
															@RequestParam("duration_id") int durationId) {
        return videoRep.retrieveVideoByNameAndArtistIdAndDurationId(videoName, artistId, durationId);
    }      
    
   
    
    
    
}

/*

    
    
    //notning
    @GET
    @Path("/video_By_ContainsInVideo/search")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Video> retriveVideo_By_ContainsInVideo(@QueryParam("nothing1") int nothing1,
                                       @QueryParam("nothing2") int nothing2,
                                       @QueryParam("containsInVideo")String containsInVideo) throws SQLException{
        return jazzLibraryDAO.retriveVideo_By_ContainsInVideo(nothing1,nothing2,containsInVideo);
    }
*/
 

