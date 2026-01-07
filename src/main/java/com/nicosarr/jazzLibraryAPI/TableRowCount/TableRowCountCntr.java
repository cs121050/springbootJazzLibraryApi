package com.nicosarr.jazzLibraryAPI.TableRowCount;


import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nicosarr.jazzLibraryAPI.Quote.Quote;
import com.nicosarr.jazzLibraryAPI.Quote.QuoteRep;

@RestController   //http://localhost:8080
@RequestMapping("tableRowCountService")
public class TableRowCountCntr {
    private ArrayList<TableRowCountCntr> tableRowCountList;   
    private final TableRowCountRep tableRowCountRep;

    
    public TableRowCountCntr(TableRowCountRep tableRowCountRep) {
    	this.tableRowCountRep = tableRowCountRep;
    }
    

	@GetMapping(value = "", produces = MediaType.APPLICATION_XML_VALUE)
    public String sayXMLHello() { 
        return "<?xml version=\"1.0\"?>" + "<tableRowCountService> tableRowCount controler... " + "</tableRowCountService>";
    }

    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TableRowCount> retrieveAll() { 
        return tableRowCountRep.retrieveAll();    
    }    
	 
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String create(@RequestParam("table_id") int tableId,
                       @RequestParam("table_name") String tableName,
                       @RequestParam("count") int count) {
    	TableRowCount tableRowCount = new TableRowCount(tableId,tableName,count);

        int result = tableRowCountRep.create(tableRowCount);
//        return ResponseEntity.status(result == 1 ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST)  //beatifull way for Error Handling
//                .body(result == 1 ? "Quote creation Success" : "Quote creation Failed");        
        return result == 1 ? "TableRowCount creation Success" : "TableRowCount creation Failed";
    }
    
    
    
    @PutMapping(value = "/refreshCount", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String refreshCount(){
    	int result = tableRowCountRep.refreshCount();    	
	     return result == 1 ?  "TableRowCount count refreshing was successfull" : "TableRowCount count refreshing Failed";  
    }  
    
    @PutMapping(value = "/update", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String update(@RequestParam("table_row_count_id") int tableRowCountId,
    		        @RequestParam("table_id") int tableId,
					@RequestParam("table_name") String tableName,
					@RequestParam("count") int count){
    	TableRowCount tableRowCount = new TableRowCount(tableRowCountId, tableId, tableName, count);
    	int result = tableRowCountRep.update(tableRowCount);    	
	     return result == 1 ?  "TableRowCount updated successfully" : "TableRowCount update Failed";  
    }  
    
    @PutMapping(value = "/updateCount", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String updateCount(@RequestParam("table_id") int tableId,
					@RequestParam("count") int count){
    	int result = tableRowCountRep.updateCount(tableId, count);    	
	     return result == 1 ?  "TableRowCount updated successfully" : "TableRowCount update Failed";  
    }  
    
//    @PutMapping(value = "/updateTableId", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
//    public String updateTableId(@RequestParam("table_id") int tableId,
//					@RequestParam("newTable_id") int newTableId){
//    	int result = tableRowCountRep.updateTableId(tableId, newTableId);    	
//	     return result == 1 ?  "TableRowCount updated successfully" : "TableRowCount update Failed";  
//    }      

//    @PutMapping(value = "/updateTableName", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
//    public String update(@RequestParam("table_id") int tableId,
//					@RequestParam("table_name") int newtableName){
//    	int result = tableRowCountRep.updateTableName(tableId, newtableName);    	
//	     return result == 1 ?  "TableRowCount updated successfully" : "TableRowCount update Failed";  
//    }      
    
     
    @GetMapping(value = "/byTableId/search",produces = MediaType.APPLICATION_JSON_VALUE)
    public TableRowCount retrieveQuoteById(@RequestParam("table_id") int tableId) {
        return tableRowCountRep.retrieveTableRowCountByTableId(tableId);
    }   
    
    
    
	
    
    
    
    
    
    
    
    
    
    
}
