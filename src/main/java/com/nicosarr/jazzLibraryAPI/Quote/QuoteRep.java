package com.nicosarr.jazzLibraryAPI.Quote;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Artist.ArtistDTO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

@Repository
public class QuoteRep {

    @PersistenceContext
    private EntityManager entityManager;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<QuoteDTO> retrieveAll() {
        String jpql = "SELECT q FROM Quote q " +
                "LEFT JOIN FETCH q.artist " +
                "LEFT JOIN FETCH q.video " +
                "ORDER BY q.quote_id ";
        
        TypedQuery<Quote> query = entityManager.createQuery(jpql, Quote.class);
        List<Quote> quote = query.getResultList();
        
        // Convert entities to DTOs
        return quote.stream()
            .map(QuoteDTO::fromEntity)
            .collect(Collectors.toList());
    }
//    
//    // Update the create method to handle nullable video_id
//    public int create(Quote quote) { 
//        String sql = "INSERT INTO Quote (quote_text, artist_id, video_id) VALUES (?, ?, ?)";
//        return jdbcTemplate.update(sql, 
//            quote.getQuote_text(), 
//            quote.getArtist_id(),
//            quote.getVideo_id()  // This can be null
//        );
//    } 
//     
//    // Update the update method to handle nullable video_id
//    public int update(Quote quote) { 
//        String sql = "UPDATE Quote SET quote_text = ?, artist_id = ?, video_id = ? WHERE quote_id = ?";
//        return jdbcTemplate.update(sql, 
//            quote.getQuote_text(), 
//            quote.getArtist_id(),
//            quote.getVideo_id(), // This can be null
//            quote.getQuote_id()
//        );	
//    }
//    
//    // Add a method to retrieve quotes by video_id
//    public List<Quote> retrieveQuoteByVideoId(Integer videoId) {
//        if (videoId == null) {
//            // Retrieve quotes without video association
//            String jpql = "SELECT q FROM Quote q WHERE q.video IS NULL";
//            Query query = entityManager.createQuery(jpql, Quote.class);
//            return query.getResultList();
//        } else {
//            String jpql = "SELECT q FROM Quote q WHERE q.video.video_id = :videoId";
//            Query query = entityManager.createQuery(jpql, Quote.class);
//            query.setParameter("videoId", videoId);
//            return query.getResultList();
//        }
//    }
    
    // Note: You'll need to update other methods that use the Quote constructor
    // to include the video_id parameter
}