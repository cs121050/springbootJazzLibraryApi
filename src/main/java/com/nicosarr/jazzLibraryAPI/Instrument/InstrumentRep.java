package com.nicosarr.jazzLibraryAPI.Instrument;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;



@Repository
public class InstrumentRep {

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Instrument> retrieveAll() {
        String jpql = "SELECT i FROM Instrument i"; 
        Query query = entityManager.createQuery(jpql, Instrument.class);
        List<Instrument> instrumentList = query.getResultList();
        return instrumentList; 
    }
    
    public int create(Instrument instrument) { 
    	String sql = "INSERT INTO Instrument (instrument_name, instrument_artist_count, instrument_video_count) VALUES (?, ?, ?)";
    	return jdbcTemplate.update(sql, instrument.getInstrument_name(), instrument.getInstrument_artist_count(), instrument.getInstrument_video_count());
    } 
     
    public int update(Instrument instrument) { 
    	String sql = "UPDATE Instrument SET instrument_name = ?, instrument_artist_count = ?, instrument_video_count = ? WHERE instrument_id = ?";
    	return jdbcTemplate.update(sql, instrument.getInstrument_name(), instrument.getInstrument_artist_count(), instrument.getInstrument_video_count(), instrument.getInstrument_id());	
    } 
    
    
    
    
    
    
    public Instrument retrieveInstrumentById(int instrumentId) {
        String jpql = "SELECT i FROM Instrument i WHERE i.instrument_id= :instrumentId"; 
        Query query = entityManager.createQuery(jpql, Instrument.class);
        query.setParameter("instrumentId", instrumentId);
        Instrument instrument = (Instrument) query.getSingleResult();
        
        return instrument;
    }  
    
    public List<Instrument> retrieveInstrumentByName(String instrumentName) {
        String jpql = "SELECT i FROM Instrument i WHERE i.instrument_name= :instrumentName"; 
        Query query = entityManager.createQuery(jpql, Instrument.class);
        query.setParameter("instrumentName", instrumentName);

        return query.getResultList();  
    }
    
    @Transactional                           // Initialize the lazy hibernate collection  
    public List<Instrument> retrieveInstrumentByTypeId(int typeId) {
        String jpql = "SELECT  i.instrument_id, i.instrument_name, count(DISTINCT a.artist_id) as instrument_artist_count, count(DISTINCT v.video_id) as instrument_video_count  " +
	                "FROM Instrument i " +
	                "INNER JOIN Artist a ON a.instrument_id = i.instrument_id " +
	                "INNER JOIN VideoContainsArtist vca ON a.artist_id = vca.artist.artist_id " +
	                "INNER JOIN Video v ON v.video_id = vca.video.video_id " +
	                "where v.type_id= :typetId " +
	                "group by i.instrument_id, i.instrument_name "+
	                "order by i.instrument_id ";
        
        Query query = entityManager.createQuery(jpql);
        query.setParameter("typetId", typeId);
        
        //return query.getResultList();  //i would use that , but Hibernate want to set the object manualy when im SELECTing individual fields and not *	
 	    List<Instrument> resultList = new ArrayList<>();
 	    List<Object[]> results = query.getResultList();	
 		for (Object[] row : results) {
 			Instrument instrument = new Instrument();
 			instrument.setInstrument_id((Integer) row[0]);
 			instrument.setInstrument_name((String) row[1]);
 			instrument.setInstrument_artist_count(((Number) row[2]).intValue());   			
 			instrument.setInstrument_video_count(((Number) row[3]).intValue());    
 		    resultList.add(instrument);
 		}
 		return resultList;
    }  
    
    @Transactional                           // Initialize the lazy hibernate collection  
    public List<Instrument> retrieveInstrumentByDurationId(int durationId) {
        String jpql = "SELECT  i.instrument_id, i.instrument_name, count(DISTINCT a.artist_id) as instrument_artist_count, count(DISTINCT v.video_id) as instrument_video_count  " +
	                "FROM Instrument i " +
	                "INNER JOIN Artist a ON a.instrument_id = i.instrument_id " +
	                "INNER JOIN VideoContainsArtist vca ON a.artist_id = vca.artist.artist_id " +
	                "INNER JOIN Video v ON v.video_id = vca.video.video_id " +
	                "where v.duration_id= :durationId " +
	                "group by i.instrument_id, i.instrument_name "+
	                "order by i.instrument_id ";
        
        Query query = entityManager.createQuery(jpql);
        query.setParameter("durationId", durationId);
        
        //return query.getResultList();  //i would use that , but Hibernate want to set the object manualy when im SELECTing individual fields and not *	
 	    List<Instrument> resultList = new ArrayList<>();
 	    List<Object[]> results = query.getResultList();	
 		for (Object[] row : results) {
 			Instrument instrument = new Instrument();
 			instrument.setInstrument_id((Integer) row[0]);
 			instrument.setInstrument_name
 			((String) row[1]);
 			instrument.setInstrument_artist_count(((Number) row[2]).intValue());   			
 			instrument.setInstrument_video_count(((Number) row[3]).intValue());    
 		    resultList.add(instrument);
 		}
 		return resultList;
    }     

    @Transactional                           // Initialize the lazy hibernate collection  
    public List<Instrument> retrieveInstrumentByTypeIdAndDurationId(int typeId, int durationId) {
        String jpql = "SELECT  i.instrument_id, i.instrument_name, count(DISTINCT a.artist_id) as instrument_artist_count, count(DISTINCT v.video_id) as instrument_video_count  " +
	                "FROM Instrument i " +
	                "INNER JOIN Artist a ON a.instrument_id = i.instrument_id " +
	                "INNER JOIN VideoContainsArtist vca ON a.artist_id = vca.artist.artist_id " +
	                "INNER JOIN Video v ON v.video_id = vca.video.video_id " +
	                "where v.duration_id= :durationId AND v.type_id = :typeId " +
	                "group by i.instrument_id, i.instrument_name "+
	                "order by i.instrument_id ";
        
        Query query = entityManager.createQuery(jpql);
        query.setParameter("durationId", durationId);
        query.setParameter("typeId", typeId);        
        
        //return query.getResultList();  //i would use that , but Hibernate want to set the object manualy when im SELECTing individual fields and not *	
 	    List<Instrument> resultList = new ArrayList<>();
 	    List<Object[]> results = query.getResultList();	
 		for (Object[] row : results) {
 			Instrument instrument = new Instrument();
 			instrument.setInstrument_id((Integer) row[0]);
 			instrument.setInstrument_name
 			((String) row[1]);
 			instrument.setInstrument_artist_count(((Number) row[2]).intValue());   			
 			instrument.setInstrument_video_count(((Number) row[3]).intValue());    
 		    resultList.add(instrument);
 		}
 		return resultList;
    }     

    
    
    
    
    
}
