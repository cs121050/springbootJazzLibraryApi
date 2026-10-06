package com.nicosarr.jazzLibraryAPI.Instrument;

public class InstrumentDTO {

	private int instrument_id;
	private String instrument_name;

	//private String name_variations; 
	
	// Constructors
	public InstrumentDTO() {}

	public InstrumentDTO(int instrument_id) {
		this.instrument_id = instrument_id;
	}

	// Static factory method to convert from Entity
	public static InstrumentDTO fromEntity(Instrument instrument) {
		return new InstrumentDTO(
			instrument.getInstrument_id()
		);
	}

	// Getters and Setters
	public int getInstrument_id() {
		return instrument_id;
	}

	public void setInstrument_id(int instrument_id) {
		this.instrument_id = instrument_id;
	}

	public String getInstrument_name() {
		return instrument_name;
	}

	public void setInstrument_name(String instrument_name) {
		this.instrument_name = instrument_name;
	}
}
