package com.nicosarr.jazzLibraryAPI.TableRowCount;

import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nicosarr.jazzLibraryAPI.Quote.Quote;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;



@Repository
public class TableRowCountRep {

/*
1	Video	
2	Artist	
3	instrument	
4	Quote	
 */

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<TableRowCount> retrieveAll() {
        String jpql = "SELECT trc FROM TableRowCount trc"; 
        Query query = entityManager.createQuery(jpql, TableRowCount.class);
        List<TableRowCount> tableRowCountList = query.getResultList();
        return tableRowCountList; 
    }
    
    public int create(TableRowCount tableRowCount) { 
    	String sql = "INSERT INTO TableRowCount (table_id, table_name, count) VALUES (?, ?, ?)";
    	return jdbcTemplate.update(sql, tableRowCount.getTable_id(), tableRowCount.getTable_name(), tableRowCount.getCount());
    } 
     
    public int update(TableRowCount tableRowCount) { 
    	String sql = "UPDATE TableRowCount SET table_id = ?, table_name = ?, count = ? WHERE table_row_count_id = ?";
    	return jdbcTemplate.update(sql, tableRowCount.getTable_id(), tableRowCount.getTable_name(), tableRowCount.getCount(), tableRowCount.getTable_row_count_id());	
    } 
    
    public int refreshCount() { 
    	String sql = "EXEC refreshCounts";
    	return jdbcTemplate.update(sql);	
    }     
    
    public int updateCount(int tableId, int count) { 
    	String sql = "UPDATE TableRowCount SET count = ? WHERE table_id = ?";
    	return jdbcTemplate.update(sql, count, tableId);	
    }   
    
//    public int updateTableId(int tableId, int newTableId) { 
//    	String jpql = "UPDATE TableRowCount "
//    			+ "SET table_id = :newTableId "
//    			+ "WHERE table_id = :tableId "
//    			+ "AND :newTableId IS NOT NULL "
//    			+ "AND NOT EXISTS ( "
//    			+ "    SELECT 1 FROM TableRowCount WHERE table_id = :newTableId "
//    			+ ") ";
//    	Query query = entityManager.createQuery(jpql, TableRowCount.class);
//        query.setParameter("newTableId", newTableId);
//        query.setParameter("tableId", tableId);
//        
//    	return jdbcTemplate.update(jpql, newTableId, tableId);	
//    }   
    
    public TableRowCount retrieveTableRowCountByTableId(int tableId) {
        String jpql = "SELECT trc FROM TableRowCount trc WHERE trc.table_id= :tableId"; 
        Query query = entityManager.createQuery(jpql, TableRowCount.class);
        query.setParameter("tableId", tableId);
        TableRowCount tableRowCount = (TableRowCount) query.getSingleResult();
        
        return tableRowCount;
    }       
    
    
	
	
}
