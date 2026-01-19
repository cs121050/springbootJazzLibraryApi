package com.nicosarr.jazzLibraryAPI.Type;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController   //http://localhost:8080
@RequestMapping("typeService")
public class TypeCntr {

    private ArrayList<Type> typeList;   
    private final TypeRep typeRep;
    
    public TypeCntr(TypeRep typeRep) {
    	this.typeRep = typeRep; 
    }
    
    @GetMapping(value = "", produces = MediaType.APPLICATION_XML_VALUE)
    public String sayXMLHello() { 
        return "<?xml version=\"1.0\"?>" + "<typeService> type controler... " + "</typeService>";
    }

    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TypeDTO> retrieveAll() { 
        return typeRep.retrieveAll();    
    }  
//    
//    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
//    public String create(@RequestParam("type_name") String typeName,
//                       @RequestParam("type_video_count") int typeVideoCount) {
//    	Type type = new Type(typeName,typeVideoCount);
//
//        int result = typeRep.create(type);
////        return ResponseEntity.status(result == 1 ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST)  //beatifull way for Error Handling
////                .body(result == 1 ? "Quote creation Success" : "Quote creation Failed");        
//        return result == 1 ? "Type creation Success" : "Type creation Failed";
//    }
//    
//      
//    @PutMapping(value = "/update", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
//    public String update(@RequestParam("type_id") int typeId,
//					@RequestParam("type_name") String typeName,
//					@RequestParam("type_video_count") int typeVideoCount){
//    	Type type = new Type(typeId,typeName,typeVideoCount);
//    	int result = typeRep.update(type);    	
//	     return result == 1 ?  "Type updated successfully" : "Type update Failed";  
//    }  
//	   
//    @GetMapping(value = "/byName/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public List<Type> retrieveByName(@RequestParam("type_name") String typeName) {
//        return typeRep.retrieveByName(typeName);
//    }   
//    
//    @GetMapping(value = "/byId/search",produces = MediaType.APPLICATION_JSON_VALUE) 
//    public Type retrieveById(@RequestParam("type_id") int typeId) {
//        return typeRep.retrieveById(typeId);
//    }  
//    
//    @GetMapping(value = "/byDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE) 
//    public List<Type> retrieveByDurationId(@RequestParam("duration_id") int durationtId) {
//        return typeRep.retrieveByDurationId(durationtId);
//    }   
//    
//    @GetMapping(value = "/byInstrumentId/search",produces = MediaType.APPLICATION_JSON_VALUE) 
//    public List<Type> retrieveByInstrumentId(@RequestParam("instrument_id") int instrumentId) {
//        return typeRep.retrieveByInstrumentId(instrumentId);
//    }        
//    
//    @GetMapping(value = "/byArtistId/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public List<Type> retrieveByArtistId(@RequestParam("artist_id") int artistId) {
//        return typeRep.retrieveByArtistId(artistId);
//    }      
//    
//    @GetMapping(value = "/byArtistIdAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public List<Type> retrieveByArtistIdAndDurationId(@RequestParam("artist_id") int artistId,
//    		                                        @RequestParam("duration_id") int durationId) {
//        return typeRep.retrieveByArtistIdAndDurationId(artistId, durationId);
//    }       
//  
//    @GetMapping(value = "/byTypeIdAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public List<Type> retrieveByTypeIdAndDurationId(@RequestParam("type_id") int typeId,
//    		                                        @RequestParam("duration_id") int durationId) {
//        return typeRep.retrieveByTypeIdAndDurationId(typeId, durationId);
//    }     
//    
//    @GetMapping(value = "/byInstrumentIdAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public List<Type> retrieveByInstrumentIdAndDurationId(@RequestParam("instrument_id") int instrumentId,
//    		                                        @RequestParam("duration_id") int durationId) {
//        return typeRep.retrieveByInstrumentIdAndDurationId(instrumentId, durationId);
//    }     
//    
}


/*
 * 
@Path("typeService")
public class TypeService {

    JazzLibraryDAO jazzLibraryDAO = new JazzLibraryDAO();



    

    



 * 
 * 
 */

