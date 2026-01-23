package com.nicosarr.jazzLibraryAPI.Quote;

public class QuoteDTO {

    private int quote_id;
    private String quote_text;
    private Integer artist_id; // Changed to Integer to support null
    private Integer video_id;  // Added video_id field

    // Constructors
    public QuoteDTO() {}

    public QuoteDTO(int quote_id, String quote_text, Integer artist_id, Integer video_id) {
        this.quote_id = quote_id;
        this.quote_text = quote_text;
        this.artist_id = artist_id;
        this.video_id = video_id;
    }

    // Static factory method to convert from Entity
    public static QuoteDTO fromEntity(Quote quote) {
        return new QuoteDTO(
            quote.getQuote_id(),
            quote.getQuote_text(),
            quote.getArtist_id(),
            quote.getVideo_id()
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

    public Integer getArtist_id() {
        return artist_id;
    }

    public void setArtist_id(Integer artist_id) {
        this.artist_id = artist_id;
    }

    public Integer getVideo_id() {
        return video_id;
    }

    public void setVideo_id(Integer video_id) {
        this.video_id = video_id;
    }
}