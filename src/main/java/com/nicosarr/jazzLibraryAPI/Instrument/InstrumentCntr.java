package com.nicosarr.jazzLibraryAPI.Instrument;
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
@RequestMapping("instrumentService")
public class InstrumentCntr {
	
    private ArrayList<Instrument> instrumentList;   
    private final InstrumentRep instrumentRep;
    
    public InstrumentCntr(InstrumentRep instrumentRep) {
    	this.instrumentRep = instrumentRep;
    }
    
    @GetMapping(value = "", produces = MediaType.APPLICATION_XML_VALUE)
    public String sayXMLHello() { 
        return "<?xml version=\"1.0\"?>" + "<instrumentService> instrument controler... " + "</instrumentService>";
    }
    
    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Instrument> retrieveAll() { 
        return instrumentRep.retrieveAll();    
    }    
    
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String create(@RequestParam("instrument_name") String instrumentName,
                               @RequestParam("instrument_artist_count") int instrumentArtistCount,
     						   @RequestParam("instrument_video_count") int instrumentVideoCount) {
    	Instrument instrument = new Instrument(instrumentName,instrumentArtistCount,instrumentVideoCount);

        int result = instrumentRep.create(instrument);
//        return ResponseEntity.status(result == 1 ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST)  //beatifull way for Error Handling
//                .body(result == 1 ? "Duration creation Success" : "Duration creation Failed");        
        return result == 1 ? "Instrument creation Success" : "Instrument creation Failed";
    }
    
      
    @PutMapping(value = "/update", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String update(@RequestParam("instrument_id") int instrumentId,
	                           @RequestParam("instrument_name") String instrumentName,
	                           @RequestParam("instrument_artist_count") int instrumentArtistCount,
	                           @RequestParam("instrument_video_count") int instrumentVideoCount){
    	Instrument instrument = new Instrument(instrumentId, instrumentName, instrumentArtistCount, instrumentVideoCount);	
    	int result = instrumentRep.update(instrument);    	
	     return result == 1 ?  "Instrument updated successfully" : "Instrument update Failed";  
    }  
    
    @GetMapping(value = "/byId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public Instrument retrieveInstrumentById(@RequestParam("instrument_id") int instrumentId) {
        return instrumentRep.retrieveInstrumentById(instrumentId);
    }     
    
    @GetMapping(value = "/byName/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Instrument> retrieveInstrumentByName(@RequestParam("instrument_name") String instrumentName) {
        return instrumentRep.retrieveInstrumentByName(instrumentName);
    }    
    
    @GetMapping(value = "/byTypeId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Instrument> retrieveInstrumentByTypeId(@RequestParam("type_id") int typeId) {
        return instrumentRep.retrieveInstrumentByTypeId(typeId);
    }      
    
    @GetMapping(value = "/byDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Instrument> retrieveInstrumentByDurationId(@RequestParam("duration_id") int durationId) {
        return instrumentRep.retrieveInstrumentByDurationId(durationId); 
    }   
    
    @GetMapping(value = "/byTypeIdAndDurationId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Instrument> retrieveInstrumentByTypeIdAndDurationId(@RequestParam("type_id") int typeId,
    												@RequestParam("duration_id") int durationId) {
        return instrumentRep.retrieveInstrumentByTypeIdAndDurationId(typeId, durationId); 
    }     
    
    

    
}
