package com.nicosarr.jazzLibraryAPI.Type;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;



@Repository
public class TypeRep {

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Type> retrieveAll() {
        String jpql = "SELECT t FROM Type t"; 
        Query query = entityManager.createQuery(jpql, Type.class);
        List<Type> typeList = query.getResultList();
        return typeList; 
    }

	public int create(Type type) { 
		String sql = "INSERT INTO Type (type_name, type_video_count) VALUES (?, ?)";
		return jdbcTemplate.update(sql, type.getType_name(), type.getType_video_count());
	} 
	 
	public int update(Type type) { 
		String sql = "UPDATE Type SET type_name = ?, type_video_count = ? WHERE type_id = ?";
		return jdbcTemplate.update(sql, type.getType_name(), type.getType_video_count(), type.getType_id());	
	} 

    public Type retrieveById(int typeId) {
        String jpql = "SELECT t FROM Type t WHERE t.type_id= :typeId"; 
        Query query = entityManager.createQuery(jpql, Type.class);
        query.setParameter("typeId", typeId);
        
        return (Type) query.getSingleResult();
    }       
    
    public List<Type> retrieveByName(String typeName) {
        String jpql = "SELECT t FROM Type t WHERE t.type_name= :typeName";
        Query query = entityManager.createQuery(jpql, Type.class);

        query.setParameter("typeName", typeName);
   
        List<Type> typeList = query.getResultList();
        return typeList;    
    }
    
    @Transactional                           // Initialize the lazy hibernate collection   
    public List<Type> retrieveByDurationId(int durationId) {
         String jpql = "SELECT t.type_id, t.type_name, COUNT(t) as type_video_count " +
                 "FROM Type t " +
                 "INNER JOIN Video v ON v.type_id = t.type_id " +
                 "WHERE v.duration_id = :durationId " +
                 "GROUP BY t.type_id, t.type_name";

 		Query query = entityManager.createQuery(jpql);
 		query.setParameter("durationId", durationId);
 		

	    List<Type> resultList = new ArrayList<>();
	    List<Object[]> results = query.getResultList();
		for (Object[] row : results) {
		    Type type = new Type();
		    type.setType_id((Integer) row[0]);
		    type.setType_name((String) row[1]);		    
		    type.setType_video_count(((Number) row[2]).intValue());
		    resultList.add(type);
		}

	    return resultList;
    }      
    
    @Transactional                           // Initialize the lazy hibernate collection   
    public List<Type> retrieveByInstrumentId(int instrumentId) {
         String jpql = "SELECT t.type_id, t.type_name, COUNT(t) as type_video_count " +
                 "FROM Type t " +
                 "INNER JOIN Video v ON v.type_id = t.type_id " +
                 "INNER JOIN VideoContainsArtist vca ON v.video_id = vca.video.video_id " +  
                 "INNER JOIN Artist a ON a.artist_id = vca.artist.artist_id " +              
                 "WHERE a.instrument_id = :instrumentId " +
                 "GROUP BY t.type_id, t.type_name";

 		Query query = entityManager.createQuery(jpql);
 		query.setParameter("instrumentId", instrumentId);
 		

	    List<Type> resultList = new ArrayList<>();
	    List<Object[]> results = query.getResultList();
		for (Object[] row : results) {
		    Type type = new Type();
		    type.setType_id((Integer) row[0]);
		    type.setType_name((String) row[1]);		    
		    type.setType_video_count(((Number) row[2]).intValue());
		    
		    
		    
		    resultList.add(type);
		}

	    return resultList;
    }  
    
    @Transactional                           // Initialize the lazy hibernate collection   
    public List<Type> retrieveByArtistId(int artistId) {
         String jpql = "SELECT t.type_id, t.type_name, COUNT(t) as type_video_count " +
                 "FROM Type t " +
                 "INNER JOIN Video v ON v.type_id = t.type_id " +
                 "INNER JOIN VideoContainsArtist vca ON v.video_id = vca.video.video_id " +            
                 "WHERE vca.artist.artist_id = :artistId " +
                 "GROUP BY t.type_id, t.type_name";

 		Query query = entityManager.createQuery(jpql);
 		query.setParameter("artistId", artistId);
 		

	    List<Type> resultList = new ArrayList<>();
	    List<Object[]> results = query.getResultList();
		for (Object[] row : results) {
		    Type type = new Type();
		    type.setType_id((Integer) row[0]);
		    type.setType_name((String) row[1]);		    
		    type.setType_video_count(((Number) row[2]).intValue());
		    
		    
		    resultList.add(type);
		}

	    return resultList;
    }      
    
    
    @Transactional                           // Initialize the lazy hibernate collection   
    public List<Type> retrieveByArtistIdAndDurationId(int artistId, int durationId) {
         String jpql = "SELECT t.type_id, t.type_name, COUNT(t) as type_video_count " +
                 "FROM Type t " +
                 "INNER JOIN Video v ON v.type_id = t.type_id " +
                 "INNER JOIN VideoContainsArtist vca ON v.video_id = vca.video.video_id " +            
                 "WHERE vca.artist.artist_id = :artistId AND v.duration_id = :durationId " +
                 "GROUP BY t.type_id, t.type_name";

 		Query query = entityManager.createQuery(jpql);
 		query.setParameter("artistId", artistId);
 		query.setParameter("durationId", durationId); 		
 		

	    List<Type> resultList = new ArrayList<>();
	    List<Object[]> results = query.getResultList();
		for (Object[] row : results) {
		    Type type = new Type();
		    type.setType_id((Integer) row[0]);
		    type.setType_name((String) row[1]);		    
		    type.setType_video_count(((Number) row[2]).intValue());
		    
		    
		    resultList.add(type);
		}

	    return resultList;
    }          
    
    
    @Transactional                           // Initialize the lazy hibernate collection   
    public List<Type> retrieveByTypeIdAndDurationId(int typeId, int durationId) {
         String jpql = "SELECT t.type_id, t.type_name, COUNT(t) as type_video_count " +
                 "FROM Type t " +
                 "INNER JOIN Video v ON v.type_id = t.type_id " +          
                 "WHERE t.type_id = :typeId AND v.duration_id = :durationId " +
                 "GROUP BY t.type_id, t.type_name";

 		Query query = entityManager.createQuery(jpql);
 		query.setParameter("typeId", typeId);
 		query.setParameter("durationId", durationId); 		
 		

	    List<Type> resultList = new ArrayList<>();
	    List<Object[]> results = query.getResultList();
		for (Object[] row : results) {
		    Type type = new Type();
		    type.setType_id((Integer) row[0]);
		    type.setType_name((String) row[1]);		    
		    type.setType_video_count(((Number) row[2]).intValue());
		    
		    
		    resultList.add(type);
		}

	    return resultList;
    }
    
    @Transactional                           // Initialize the lazy hibernate collection   
    public List<Type> retrieveByInstrumentIdAndDurationId(int instrumentId,int durationId) {
         String jpql = "SELECT t.type_id, t.type_name, COUNT(t) as type_video_count " +
                 "FROM Type t " +
                 "INNER JOIN Video v ON v.type_id = t.type_id " +
                 "INNER JOIN VideoContainsArtist vca ON v.video_id = vca.video.video_id " +  
                 "INNER JOIN Artist a ON a.artist_id = vca.artist.artist_id " +              
                 "WHERE a.instrument_id = :instrumentId AND v.duration_id = :durationId  " +
                 "GROUP BY t.type_id, t.type_name ";

 		Query query = entityManager.createQuery(jpql);
 		query.setParameter("instrumentId", instrumentId);
 		query.setParameter("durationId", durationId); 		
 		

	    List<Type> resultList = new ArrayList<>();
	    List<Object[]> results = query.getResultList();
		for (Object[] row : results) {
		    Type type = new Type();
		    type.setType_id((Integer) row[0]);
		    type.setType_name((String) row[1]);		    
		    type.setType_video_count(((Number) row[2]).intValue());
		    
		    
		    
		    resultList.add(type);
		}

	    return resultList;
    }      
    
    
    
}
/* 
 * 
 

    \
	
	
    public static Type retrieveType_By_type_name(String type_name) { //

        Connection con = getConnection();

        Type type = new Type();         
        try {
            PreparedStatement ps = con.prepareStatement("select * \r\n"
                                                        + "from type \r\n"
                                                        + "where type_name=?");
            ps.setString(1,type_name); 
            ResultSet rs=ps.executeQuery(); 

            rs.next() ;
            type.setType_id(rs.getInt(1));               
            type.setType_name(rs.getString(2));               


        } catch (SQLException ex) {

        }
        return type;            
    }
	
    
    
    
    public static List<TypeAndVideoCount> retrieveAllTypeAndVideoCount() { //

        Connection con = getConnection();

        List<TypeAndVideoCount> list = new ArrayList<>();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT  type.type_id ,type.type_name, count(*) as 'videoCount'\n" +
                            "FROM type\n" +
                            "inner join video ON video.type_id=type.type_id\n" +
                            "group by type.type_id ,type.type_name;\n");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                TypeAndVideoCount typeAndVideoCount = new TypeAndVideoCount();
                typeAndVideoCount.setType_id(rs.getInt(1));
                typeAndVideoCount.setType_name(rs.getString(2));
                typeAndVideoCount.setVideo_count(rs.getInt(3));
                list.add(typeAndVideoCount);
            }

        } catch (SQLException ex) {

        }
        return list;            //... tin opoia tha e3ageis sto telos gia ton     Servlet to use
    }


    public static List<TypeAndVideoCount> retrieveTypeAndVideoCount_By_instrument_id(int instrument_id) { //

        Connection con = getConnection();

        List<TypeAndVideoCount> list = new ArrayList<>();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT  type.type_id, type.type_name, count(*) as 'videoCount'\n" +
                            "FROM type\n" +
                            "inner join video ON video.type_id=type.type_id\n" +
                            "inner join videoContainsArtist ON video.video_id=videoContainsArtist.video_id\n" +
                            "inner join Artist ON artist.artist_id=videoContainsArtist.artist_id\n" +
                            "where instrument_id=?\n" +
                            "group by type.type_id, type.type_name;\n");
            ps.setInt(1, instrument_id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                TypeAndVideoCount typeAndVideoCount = new TypeAndVideoCount();
                typeAndVideoCount.setType_id(rs.getInt(1));
                typeAndVideoCount.setType_name(rs.getString(2));
                typeAndVideoCount.setVideo_count(rs.getInt(3));
                list.add(typeAndVideoCount);
            }

        } catch (SQLException ex) {

        }
        return list;            //... tin opoia tha e3ageis sto telos gia ton     Servlet to use
    }

    public static List<TypeAndVideoCount> retrieveTypeAndVideoCount_By_duration_id(int duration_id) { //

        Connection con = getConnection();

        List<TypeAndVideoCount> list = new ArrayList<>();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT  type.type_id, type.type_name, count(*) as 'videoCount'\n" +
                            "FROM type\n" +
                            "inner join video ON video.type_id=type.type_id\n" +
                            "where duration_id=?\n" +
                            "group by type.type_id, type.type_name;\n");
            ps.setInt(1, duration_id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                TypeAndVideoCount typeAndVideoCount = new TypeAndVideoCount();
                typeAndVideoCount.setType_id(rs.getInt(1));
                typeAndVideoCount.setType_name(rs.getString(2));
                typeAndVideoCount.setVideo_count(rs.getInt(3));
                list.add(typeAndVideoCount);
            }

        } catch (SQLException ex) {

        }
        return list;            //... tin opoia tha e3ageis sto telos gia ton     Servlet to use
    }

    
    public static List<TypeAndVideoCount> retrieveTypeAndVideoCount_By_artist_id(int nothing, int artist_id) { //

        Connection con = getConnection();

        List<TypeAndVideoCount> list = new ArrayList<>();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT  Type.type_id ,type.type_name, count(*) as 'videoCount'\n" +
                            "FROM type\n" +
                            "inner join Video ON Video.type_id=type.type_id\n" +
                            "inner join videoContainsArtist ON videoContainsArtist.video_id=Video.video_id\n" +
                            "where artist_id=?\n" +
                            "group by Type.type_id ,type.type_name\n" +
                            ";");
            ps.setInt(1, artist_id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                TypeAndVideoCount typeAndVideoCount = new TypeAndVideoCount();
                typeAndVideoCount.setType_id(rs.getInt(1));
                typeAndVideoCount.setType_name(rs.getString(2));
                typeAndVideoCount.setVideo_count(rs.getInt(3));
                list.add(typeAndVideoCount);
            }

        } catch (SQLException ex) {

        }
        return list;            //... tin opoia tha e3ageis sto telos gia ton     Servlet to use
    }


    public static List<TypeAndVideoCount> retrieveTypeAndVideoCount_By_artist_id_And_duration_id(int artist_id, int duration_id) { //

        Connection con = getConnection();

        List<TypeAndVideoCount> list = new ArrayList<>();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT  type.type_id ,type.type_name, count(*) as 'videoCount'\n" +
                            "FROM type\n" +
                            "inner join video ON video.type_id=type.type_id\n" +
                            "inner join VideoContainsArtist ON VideoContainsArtist.video_id=video.video_id\n" +
                            "where artist_id=? and duration_id=?\n" +
                            "group by type.type_id ,type.type_name;\n");
            ps.setInt(1, artist_id);
            ps.setInt(2, duration_id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                TypeAndVideoCount typeAndVideoCount = new TypeAndVideoCount();
                typeAndVideoCount.setType_id(rs.getInt(1));
                typeAndVideoCount.setType_name(rs.getString(2));
                typeAndVideoCount.setVideo_count(rs.getInt(3));
                list.add(typeAndVideoCount);
            }

        } catch (SQLException ex) {

        }
        return list;            //... tin opoia tha e3ageis sto telos gia ton     Servlet to use
    }

    public static List<TypeAndVideoCount> retrieveTypeAndVideoCount_By_type_id_And_duration_id(int nothing1, int nothing2, int type_id, int duration_id) {//

        Connection con = getConnection();

        List<TypeAndVideoCount> list = new ArrayList<>();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT  type.type_id ,type.type_name, count(*) as 'videoCount'\n" +
                            "FROM type\n" +
                            "inner join video ON video.type_id=type.type_id\n" +
                            "where Type.type_id=? and duration_id=?\n" +
                            "group by type.type_id ,type.type_name;"
            );
            ps.setInt(1, type_id);
            ps.setInt(2, duration_id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                TypeAndVideoCount typeAndVideoCount = new TypeAndVideoCount();
                typeAndVideoCount.setType_id(rs.getInt(1));
                typeAndVideoCount.setType_name(rs.getString(2));
                typeAndVideoCount.setVideo_count(rs.getInt(3));
                list.add(typeAndVideoCount);
            }

        } catch (SQLException ex) {

        }
        return list;            //... tin opoia tha e3ageis sto telos gia ton     Servlet to use
    }


    public static List<TypeAndVideoCount> retrieveTypeAndVideoCount_By_instrument_id_And_duration_id(int nothing1, int nothing2, int nothing3, int instrument_id,int duration_id) { //

        Connection con = getConnection();

        List<TypeAndVideoCount> list = new ArrayList<>();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT  type.type_id ,type.type_name, count(*) as 'videoCount'\n" +
                            "FROM type\n" +
                            "inner join video ON video.type_id=type.type_id\n" +
                            "inner join videoContainsArtist ON videoContainsArtist.video_id=video.video_id\n" +
                            "inner join Artist ON  Artist.artist_id=videoContainsArtist.artist_id\n" +
                            "where instrument_id=? and duration_id=?\n" +
                            "group by type.type_id ,type.type_name;\n");
            ps.setInt(1, instrument_id);
            ps.setInt(2, duration_id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                TypeAndVideoCount typeAndVideoCount = new TypeAndVideoCount();
                typeAndVideoCount.setType_id(rs.getInt(1));
                typeAndVideoCount.setType_name(rs.getString(2));
                typeAndVideoCount.setVideo_count(rs.getInt(3));
                list.add(typeAndVideoCount);
            }

        } catch (SQLException ex) {

        }
        return list;            //... tin opoia tha e3ageis sto telos gia ton     Servlet to use
    }


    
    
    
    
 *   
 *   */
