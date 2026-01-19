package com.nicosarr.jazzLibraryAPI.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Artist.ArtistDTO;
import com.nicosarr.jazzLibraryAPI.Video.Video;
import com.nicosarr.jazzLibraryAPI.Video.VideoWithArtistDTO;

@Repository
public class DurationRep {

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<DurationDTO> retrieveAll() {
        String jpql = "SELECT d FROM Duration d " +
                "ORDER BY d.duration_id ";

        TypedQuery<Duration> query = entityManager.createQuery(jpql, Duration.class);
        List<Duration> duration = query.getResultList();
        
        // Convert entities to DTOs
        return duration.stream()
            .map(DurationDTO::fromEntity)
            .collect(Collectors.toList());
    }
    public List<DurationWithVideoDTO> retrieveAllWithVideo() {
        String jpql = "SELECT d FROM Duration d " +
                "LEFT JOIN FETCH d.video v " +
                "ORDER BY d.duration_id";
        
        TypedQuery<Duration> query = entityManager.createQuery(jpql, Duration.class);
        List<Duration> duration = query.getResultList();
        
        // Convert entities to DTOs
        return duration.stream()
            .map(DurationWithVideoDTO::fromEntity)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public List<DurationWithArtistDTO> retrieveAllWithArtist() {
    	 // First query: get durations
        String jpql = "SELECT DISTINCT d FROM Duration d " +
                      "ORDER BY d.duration_id";
        
        TypedQuery<Duration> query = entityManager.createQuery(jpql, Duration.class);
        List<Duration> durations = query.getResultList();
        
        // Second query: get videos for durations (batched)
        String videosJpql = "SELECT d FROM Duration d " +
                           "LEFT JOIN FETCH d.video v " +
                           "WHERE d IN :durations " +
                           "ORDER BY d.duration_id";
        
        TypedQuery<Duration> videosQuery = entityManager.createQuery(videosJpql, Duration.class);
        videosQuery.setParameter("durations", durations);
        durations = videosQuery.getResultList();
        
        // Third query: get videoContainsArtists for videos (batched)
        if (!durations.isEmpty()) {
            List<Video> allVideos = new ArrayList<>();
            durations.forEach(d -> allVideos.addAll(d.getVideo()));
            
            if (!allVideos.isEmpty()) {
                String vcaJpql = "SELECT DISTINCT v FROM Video v " +
                                "LEFT JOIN FETCH v.videoContainsArtists vca " +
                                "WHERE v IN :videos " +
                                "ORDER BY v.video_id";
                
                TypedQuery<Video> vcaQuery = entityManager.createQuery(vcaJpql, Video.class);
                vcaQuery.setParameter("videos", allVideos);
                vcaQuery.getResultList(); // This loads the data into persistence context
            }
        }
        
        // Now all data is loaded, convert to DTOs
        return durations.stream()
            .map(DurationWithArtistDTO::fromEntity)
            .collect(Collectors.toList());
    }
//    
//    public int create(Duration duration) { 
//    	String sql = "INSERT INTO Duration (duration_name, duration_video_count) VALUES (?, ?)";
//    	return jdbcTemplate.update(sql, duration.getDuration_name(), duration.getDuration_video_count());
//    } 
//    
//    public int update(Duration duration) {
//    	String sql = "UPDATE duration SET duration_name = ?, duration_video_count = ? WHERE duration_id = ?";
//    	return jdbcTemplate.update(sql, duration.getDuration_name(), duration.getDuration_video_count(), duration.getDuration_id());	
//    } 
//    
//    public int updateDurationVideoCount(int duration_id, int duration_video_count) {
//    	String sql = "UPDATE duration SET duration_video_count = ? WHERE duration_id = ?";
//    	return jdbcTemplate.update(sql, duration_video_count, duration_id);
//    	
//    }   
//    
//    public int updateDurationName(int duration_id, String duration_name) {
//    	String sql = "UPDATE duration SET duration_name = ? WHERE duration_id = ?";
//    	return jdbcTemplate.update(sql, duration_name, duration_id);
//    	
//    }       
//    
//    
//    
//    
//
//    public Duration retrieveDurationByName(String durationName) {
//            String jpql = "SELECT d FROM Duration d WHERE d.duration_name= :durationName"; 
//            Query query = entityManager.createQuery(jpql, Duration.class);
//            query.setParameter("durationName", durationName);
//            Duration duration = (Duration) query.getSingleResult();
//            
//            return duration;
//    } 
//
//    public Duration retrieveDurationById(int durationId) {
//        String jpql = "SELECT d FROM Duration d WHERE d.duration_id= :durationId"; 
//        Query query = entityManager.createQuery(jpql, Duration.class);
//        query.setParameter("durationId", durationId);
//        Duration duration = (Duration) query.getSingleResult();
//        
//        return duration;
//    }     
//    
//    @Transactional                           // Initialize the lazy hibernate collection      
//    public List<Duration> retrieveDurationByTypeId(int typeId) {
//        String jpql = "SELECT d.duration_id, d.duration_name,  COUNT(v) as duration_video_count " +
//	                 "FROM Duration d " +
//	                 "INNER JOIN Video v ON v.video.duration_id = d.duration_id " + 
//	                 "WHERE v.type_id= :typeId " +
//	                 "GROUP BY d.duration_id ,d.duration_name";
//
// 		Query query = entityManager.createQuery(jpql);
// 		query.setParameter("typeId", typeId);	
//        //return query.getResultList();  //i would use that , but Hibernate want to set the object manualy when im SELECTing individual fields and not *
// 		
// 	    List<Duration> resultList = new ArrayList<>();
// 	    List<Object[]> results = query.getResultList();	
// 		for (Object[] row : results) {
// 			Duration duration = new Duration();
// 			duration.setDuration_id((Integer) row[0]);
// 			duration.setDuration_name((String) row[1]);
// 			duration.setDuration_video_count(((Number) row[2]).intValue());    
// 		    resultList.add(duration);
// 		}
//
// 	    return resultList;
//    }   
//    
//    @Transactional                           // Initialize the lazy hibernate collection  
//    public List<Duration> retrieveDurationByInstrumentId(int instrumentId) {
//        String jpql = "SELECT d.duration_id, d.duration_name,  COUNT(v) as duration_video_count " +
//	                 "FROM Duration d " +
//	                 "INNER JOIN Video v ON v.duration_id = d.duration_id " +
//	                 "INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id " + // SOS : you have to  do vca.video.video_id , in order to inform vca that the its video_id is referse to video.video_id
//                     "INNER JOIN Artist a ON a.artist_id = vca.artist.artist_id " +
//	                 "WHERE a.instrument_id= :instrumentId " +
//	                 "GROUP BY d.duration_id ,d.duration_name ";
//
// 		Query query = entityManager.createQuery(jpql);
// 		query.setParameter("instrumentId", instrumentId);	
//        //return query.getResultList();  //or the following
// 	    List<Duration> resultList = new ArrayList<>();
// 	    List<Object[]> results = query.getResultList();	
// 		for (Object[] row : results) {
// 			Duration duration = new Duration();
// 			duration.setDuration_id((Integer) row[0]);
// 			duration.setDuration_name((String) row[1]);
// 			duration.setDuration_video_count(((Number) row[2]).intValue());    
// 		    resultList.add(duration);
// 		}
//
// 	    return resultList;
//    }   
//    
//    @Transactional                           // Initialize the lazy hibernate collection  
//    public List<Duration> retrieveDurationByArtistId(int artistId) {
//        String jpql = "SELECT d.duration_id, d.duration_name,  COUNT(v) as duration_video_count " +
//	                 "FROM Duration d " +
//	                 "INNER JOIN Video v ON v.duration_id = d.duration_id " +
//	                 "INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id " + // SOS : you have to  do vca.video.video_id , in order to inform vca that the its video_id is referse to video.video_id
//	                 "WHERE vca.artist.artist_id= :artistId " +
//	                 "GROUP BY d.duration_id ,d.duration_name ";
//
// 		Query query = entityManager.createQuery(jpql);
// 		query.setParameter("artistId", artistId);	
//        //return query.getResultList();  //i would use that , but Hibernate want to set the object manualy when im SELECTing individual fields and not *
// 	    List<Duration> resultList = new ArrayList<>();
// 	    List<Object[]> results = query.getResultList();	
// 		for (Object[] row : results) {
// 			Duration duration = new Duration();
// 			duration.setDuration_id((Integer) row[0]);
// 			duration.setDuration_name((String) row[1]);
// 			duration.setDuration_video_count(((Number) row[2]).intValue());    
// 		    resultList.add(duration);
// 		}
//
// 	    return resultList;
//    }   
//    
//    
//    
//    @Transactional                           // Initialize the lazy hibernate collection    
//    public List<Duration> retrieveDurationByTypeIdAndInstrumentId(int typeId, int instrumentId) {
//        String jpql = "SELECT d.duration_id, d.duration_name,  COUNT(v) as duration_video_count " +
//	                 "FROM Duration d " +
//	                 "INNER JOIN Video v ON v.duration_id = d.duration_id " +
//	                 "INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id " +
//                     "INNER JOIN Artist a ON a.artist_id = vca.artist.artist_id " +
//	                 "WHERE v.type_id = :typeId AND a.instrument_id = :instrumentId " +
//	                 "GROUP BY d.duration_id ,d.duration_name";
//
// 		Query query = entityManager.createQuery(jpql);
// 		query.setParameter("typeId", typeId);	 		
// 		query.setParameter("instrumentId", instrumentId);
// 		
//        //return query.getResultList();  //i would use that , but Hibernate want to set the object manualy when im SELECTing individual fields and not *
// 	    List<Duration> resultList = new ArrayList<>();
// 	    List<Object[]> results = query.getResultList();	
// 		for (Object[] row : results) {
// 			Duration duration = new Duration();
// 			duration.setDuration_id((Integer) row[0]);
// 			duration.setDuration_name((String) row[1]);
// 			duration.setDuration_video_count(((Number) row[2]).intValue());    
// 		    resultList.add(duration);
// 		}
//
// 	    return resultList;
//    }   
//    
//    @Transactional                           // Initialize the lazy hibernate collection    
//    public List<Duration> retrieveTypeIdAndArtistId(int typeId, int artistId) {
//        String jpql = "SELECT d.duration_id, d.duration_name,  COUNT(v) as duration_video_count " +
//	                 "FROM Duration d " +
//	                 "INNER JOIN Video v ON v.duration_id = d.duration_id " +
//	                 "INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id " +
//	                 "WHERE v.type_id = :typeId AND vca.artist.artist_id = :artistId " +
//	                 "GROUP BY d.duration_id ,d.duration_name";
//
// 		Query query = entityManager.createQuery(jpql);
// 		query.setParameter("typeId", typeId);	 		
// 		query.setParameter("artistId", artistId);
// 		
//        //return query.getResultList();  //i would use that , but Hibernate want to set the object manualy when im SELECTing individual fields and not *
// 		
// 	    List<Duration> resultList = new ArrayList<>();
// 	    List<Object[]> results = query.getResultList();	
// 		for (Object[] row : results) {
// 			Duration duration = new Duration();
// 			duration.setDuration_id((Integer) row[0]);
// 			duration.setDuration_name((String) row[1]);
// 			duration.setDuration_video_count(((Number) row[2]).intValue());    
// 		    resultList.add(duration);
// 		}
//
// 	    return resultList;
//    }     
//    
    
    
    
}
