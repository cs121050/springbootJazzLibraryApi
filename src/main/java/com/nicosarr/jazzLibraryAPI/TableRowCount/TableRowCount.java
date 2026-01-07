package com.nicosarr.jazzLibraryAPI.TableRowCount;


import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import jakarta.persistence.Table;


@Entity
@Table(name = "TableRowCount")  
@JacksonXmlRootElement(localName = "tableRowCount")
public class TableRowCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int table_row_count_id;  // Assuming there's a primary key

    @Column(name = "table_id")
    private int table_id;
    
    @Column(name = "table_name")
    private String table_name;    

    @Column(name = "video_count")  // Assuming there's a count column
    private int video_count;
	
	public TableRowCount() {

	}
	
	public TableRowCount(int table_row_count_id, int table_id, String table_name, int video_count) {
		this.table_row_count_id = table_row_count_id;
		this.table_id = table_id;
		this.table_name = table_name;
		this.video_count = video_count;
	}

	public TableRowCount(int table_id, String table_name, int video_count) {
		this.table_id = table_id;
		this.table_name = table_name;
		this.video_count = video_count;
	}	
	
	public TableRowCount(int table_id, String table_name) {
		this.table_id = table_id;
		this.table_name = table_name;
	}
	
	public TableRowCount(int video_count) {
		this.video_count = video_count;
	}		

	
	

	public int getTable_row_count_id() {
		return table_row_count_id;
	}

	public void setTable_row_count_id(int table_row_count_id) {
		this.table_row_count_id = table_row_count_id;
	}

	public int getTable_id() {
		return table_id;
	}

	public void setTable_id(int table_id) {
		this.table_id = table_id;
	}

	public String getTable_name() {
		return table_name;
	}

	public void setTable_name(String table_name) {
		this.table_name = table_name;
	}

	public int getCount() {
		return video_count;
	}

	public void setCount(int video_count) {
		this.video_count = video_count;
	}

	@Override
	public String toString() {
		return "TableRowCount [table_row_count_id=" + table_row_count_id + ", table_id=" + table_id + ", table_name="
				+ table_name + ", video_count=" + video_count + ", getTable_row_count_id()=" + getTable_row_count_id()
				+ ", getTable_id()=" + getTable_id() + ", getTable_name()=" + getTable_name() + ", getCount()="
				+ getCount() + ", getClass()=" + getClass() + ", hashCode()=" + hashCode() + ", toString()="
				+ super.toString() + "]";
	}
	
	
	
	
    
    
    

}