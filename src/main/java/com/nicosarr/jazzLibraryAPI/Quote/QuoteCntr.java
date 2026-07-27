package com.nicosarr.jazzLibraryAPI.Quote;

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
@RequestMapping("quoteService")
public class QuoteCntr {


    private ArrayList<Quote> quoteList;   
    private final QuoteRep quoteRep;

    
    public QuoteCntr(QuoteRep quoteRep) {
    	this.quoteRep = quoteRep;
    }
    
    @GetMapping(value = "", produces = MediaType.APPLICATION_XML_VALUE)
    public String sayXMLHello() { 
        return "<?xml version=\"1.0\"?>" + "<quoteService> quote controler... " + "</quoteService>";
    }

    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<QuoteDTO> retrieveAll() { 
        return quoteRep.retrieveAll();    
    }    
//	
//    
//    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
//    public String create(@RequestParam("quote_text") String quoteText,
//                       @RequestParam("artist_id") int artistId) {
//    	Quote quote = new Quote(quoteText,artistId);
//
//        int result = quoteRep.create(quote);
////        return ResponseEntity.status(result == 1 ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST)  //beatifull way for Error Handling
////                .body(result == 1 ? "Quote creation Success" : "Quote creation Failed");        
//        return result == 1 ? "Quote creation Success" : "Quote creation Failed";
//    }
//    
//      
//    @PutMapping(value = "/update", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
//    public String update(@RequestParam("quote_id") int quoteId,
//					@RequestParam("quote_text") String quoteText,
//					@RequestParam("artist_id") int artistId){
//    	Quote quote = new Quote(quoteId, quoteText, artistId);
//    	int result = quoteRep.update(quote);    	
//	     return result == 1 ?  "Quote updated successfully" : "Quote update Failed";  
//    }  
//	
//    @GetMapping(value = "/byText/search",produces = MediaType.APPLICATION_JSON_VALUE)  //CONTAINED ... WHERE LIKE
//    public List<Quote> retrieveQuoteByText(@RequestParam("quote_text") String quoteText) {
//        return quoteRep.retrieveQuoteByText(quoteText);
//    }    
//    
//    @GetMapping(value = "/byId/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public Quote retrieveQuoteById(@RequestParam("quote_id") int quoteId) {
//        return quoteRep.retrieveQuoteById(quoteId);
//    }   
//    
//    @GetMapping(value = "/byArtistId/search",produces = MediaType.APPLICATION_JSON_VALUE)
//    public List<Quote> retrieveQuoteByArtistId(@RequestParam("artist_id") int artistId) {
//        return quoteRep.retrieveQuoteByArtistId(artistId);
//    }     
//    
//  
//
//    @GetMapping(value = "/randomQuote",produces = MediaType.APPLICATION_JSON_VALUE)
//    public Quote retriveRandomQuote() {
//        return quoteRep.retriveRandomQuote();
//    }
//    
//    
//        
//    
///*
//    @POST
//    @Path("/databaseStructureReset")
//    @Produces(MediaType.TEXT_HTML)
//    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
//    public String dropDatabaseTablesAndCreateNew(@FormParam("databaseStructureSqlFile") String databaseStructureSqlFile) {
//        
//        int result = jazzLibraryDAO.dropDatabaseTablesAndCreateNew(databaseStructureSqlFile);
//        if (result == 1) 
//            return "Database Structure Reset Successfully";
//        else
//            return "Database Structure Reset failed";
//    }
// */
//	
//	
	
}
