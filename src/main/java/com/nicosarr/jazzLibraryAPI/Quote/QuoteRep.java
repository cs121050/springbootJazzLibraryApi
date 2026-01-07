package com.nicosarr.jazzLibraryAPI.Quote;


import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;


import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;



@Repository
public class QuoteRep {

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Quote> retrieveAll() {
 		return entityManager.createQuery("SELECT q FROM Quote q", Quote.class)
 					.getResultList();
    }	 
    

        
	
    public int create(Quote quote) { 
    	String sql = "INSERT INTO Quote (quote_text, artist_id) VALUES (?, ?)";
    	return jdbcTemplate.update(sql, quote.getQuote_text(), quote.getArtist_id());
    } 
     
    public int update(Quote quote) { 
    	String sql = "UPDATE Quote SET quote_text = ?, artist_id = ? WHERE quote_id = ?";
    	return jdbcTemplate.update(sql, quote.getQuote_text(), quote.getArtist_id(), quote.getQuote_id());	
    } 
        
    public Quote retrieveQuoteById(int quoteId) {
        String jpql = "SELECT q FROM Quote q WHERE q.quote_id= :quoteId"; 
        Query query = entityManager.createQuery(jpql, Quote.class);
        query.setParameter("quoteId", quoteId);
        Quote quote = (Quote) query.getSingleResult();
        
        return quote;
    }       
    
    public List<Quote> retrieveQuoteByText(String quoteText) {
        String sql = "SELECT * FROM Quote WHERE quote_text LIKE ?";
        return jdbcTemplate.query(sql, new QuoteRowMapper(), "%" + quoteText + "%");
    }
    
    public List<Quote> retrieveQuoteByArtistId(int artistId) {
        String jpql = "SELECT q FROM Quote q WHERE q.artist_id= :artistId"; 
        Query query = entityManager.createQuery(jpql, Quote.class);
        query.setParameter("artistId", artistId);
        List<Quote> quoteList = query.getResultList();
        
        return quoteList;
    }    
    
    public Quote retriveRandomQuote() {
    	
    	int numberOfQuotes =  retriveQuoteCount();
    	int randomQuoteId = randomIdGenerator(numberOfQuotes);

        return retrieveQuoteById(randomQuoteId); 
    }
      
    
    public int retriveQuoteCount() { 
    	
        String jpql = "SELECT t.video_count FROM TableRowCount t WHERE t.table_id = 4"; 
        Query query = entityManager.createQuery(jpql);
        Integer result = (Integer) query.getSingleResult();
        
        return result != null ? result : 0; 
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
   //usefull methods 
    
    public static int randomIdGenerator(int maxID){

        Random r = new Random();
        int low = 1;
        int high = maxID;
        int randomID = r.nextInt(high-low) + low;

        return randomID;
    }
    
    public static int randomIdGenerator(int maxID, List<Integer> listOfUsedVideoIds){
    //thelo enan tixaio arithmo pou na mhn iparxei mesa sthn list 
        Random r = new Random();
        int randomID;
        
        Boolean thisIdAlreadyUsed;
        do{
            
            thisIdAlreadyUsed = false;
            int low = 1;
            int high = maxID;
            randomID = r.nextInt(high-low) + low;
            
            for(int i=0; i<listOfUsedVideoIds.size(); i++)
                if(listOfUsedVideoIds.get(i) == randomID){
                    thisIdAlreadyUsed = true;
                    break;
                }
        }while(thisIdAlreadyUsed == true);
        
        
        return randomID;
    }
    
}


