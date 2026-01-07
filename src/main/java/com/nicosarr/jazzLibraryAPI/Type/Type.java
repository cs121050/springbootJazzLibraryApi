package com.nicosarr.jazzLibraryAPI.Type;

import java.util.ArrayList;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "Type")  // database tables
@JacksonXmlRootElement(localName = "type")
public class Type {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY if your DB supports auto-increment	
	private int type_id;
    @Column(name = "type_name")
	private String type_name;
    @Column(name = "type_video_count")	      
    private int type_video_count;    
 
    public Type () {}	
    public Type (int type_id, String type_name, int type_video_count){
	   	this.type_id = type_id;
	   	
	   	this.type_name = type_name;
 	
	   	this.type_video_count = type_video_count;
    }
    public Type (String type_name, int type_video_count){
 	   	this.type_name = type_name;
 		this.type_video_count = type_video_count;	
    }    
	public String toString(){
        return "type_id:" + type_id + "#type_name:" + type_name+ "#type_video_count:" + type_video_count;
    }   
    public String valuesToString(){
        return type_id + "#" + type_name + "#" + type_video_count;
    }  
    public Type toObject(){
        return new Type(this.type_id, this.type_name, this.type_video_count);          
    }
    
	public int getType_id() {
		return type_id;
	}
	public void setType_id(int type_id) {
		this.type_id = type_id;
	}
	public String getType_name() {
		return type_name;
	}
	public void setType_name(String type_name) {
		this.type_name = type_name;
	}
	public int getType_video_count() {
		return type_video_count;
	}
	public void setType_video_count(int type_video_count) {
		this.type_video_count = type_video_count;
	}

	
	
}

@JacksonXmlRootElement(localName = "typeArrayListManager")
class TypeArrayListManager {

	private ArrayList<Type> typeList;

    public TypeArrayListManager() {
    	typeList = new ArrayList<>();
    }

    public void addType(Type type) {
    	typeList.add(type);
    }

    public ArrayList<Type> getType() {
        return typeList;			
    }  	
	
}
