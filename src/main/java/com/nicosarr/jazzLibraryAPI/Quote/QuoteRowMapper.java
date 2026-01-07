package com.nicosarr.jazzLibraryAPI.Quote;

import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.SQLException;

public class QuoteRowMapper implements RowMapper<Quote> {
    @Override
    public Quote mapRow(ResultSet rs, int rowNum) throws SQLException {
        Quote quote = new Quote();
        quote.setQuote_id(rs.getInt("quote_id"));
        quote.setQuote_text(rs.getString("quote_text"));
        quote.setArtist_id(rs.getInt("artist_id"));
        return quote;
    }
}