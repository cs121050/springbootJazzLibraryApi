package com.nicosarr.jazzLibraryAPI.Quote;

public class QuoteDTO {

	private int quote_id;
	private String quote_text;
	private int artist_id;

	// Constructors
	public QuoteDTO() {}

	public QuoteDTO(int quote_id, String quote_text, int artist_id) {
		this.quote_id = quote_id;
		this.quote_text = quote_text;
		this.artist_id = artist_id;
	}

	// Static factory method to convert from Entity
	public static QuoteDTO fromEntity(Quote quote) {
		return new QuoteDTO(
			quote.getQuote_id(),
			quote.getQuote_text(),
			quote.getArtist_id()
		);
	}

	// Getters and Setters
	public int getQuote_id() {
		return quote_id;
	}

	public void setQuote_id(int quote_id) {
		this.quote_id = quote_id;
	}

	public String getQuote_text() {
		return quote_text;
	}

	public void setQuote_text(String quote_text) {
		this.quote_text = quote_text;
	}

	public int getArtist_id() {
		return artist_id;
	}

	public void setArtist_id(int artist_id) {
		this.artist_id = artist_id;
	}
}
