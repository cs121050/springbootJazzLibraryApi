package com.nicosarr.jazzLibraryAPI.Quote;


import java.util.ArrayList;


import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "Quote")  // database tables
@JacksonXmlRootElement(localName = "quote")
public class Quote {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY if your DB supports auto-increment	
	private int quote_id;
    @Column(name = "quote_text")	
    private String quote_text;
    @Column(name = "artist_id")
    private int artist_id;


	public Quote () {
		
	}
	
    public Quote(int quote_id, String quote_text, int artist_id) {
        this.quote_id = quote_id;
        this.quote_text = quote_text;
        this.artist_id = artist_id;
    }
    public Quote(String quote_text, int artist_id) {
        this.quote_text = quote_text;
        this.artist_id = artist_id;
    }
    public Quote(String quote_text) {
        this.quote_text = quote_text;
        this.artist_id = 0;
    }    
    
    public String getQuote_text() {
		return quote_text;
	}
	public void setQuote_text(String quote_text) {
		this.quote_text = quote_text;
	}

	public int getQuote_id() {
		return quote_id;
	}
	public void setQuote_id(int quote_id) {
		this.quote_id = quote_id;
	}

	public int getArtist_id() {
		return artist_id;
	}

	public void setArtist_id(int artist_id) {
		this.artist_id = artist_id;
	}

	public String toString(){
        return "quote_id:" + quote_id + "#quote_text:" + quote_text + "#artist_id:" + artist_id;
    }   
    public String valuesToString(){
        return quote_id + "#" + quote_text + "#" + artist_id + "#";
    }      
    public Quote toObject(){
        return new Quote(this.quote_id, this.quote_text, this.artist_id);          
    }
    
    

}
    

@JacksonXmlRootElement(localName = "quoteArrayListManager")
class QuoteArrayListManager {

    private ArrayList<Quote> quoteList;

    public QuoteArrayListManager() {
    	quoteList = new ArrayList<>();
    }

    public void addQuote(Quote quote) {
    	quoteList.add(quote);
    }

    public ArrayList<Quote> getQuote() {
        return quoteList;
    }  
   	
}
    
	
