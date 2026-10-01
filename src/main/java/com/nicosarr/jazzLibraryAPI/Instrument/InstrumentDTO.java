package com.nicosarr.jazzLibraryAPI.Instrument;

public class InstrumentDTO {

	private int instrument_id;
	private String instrument_name;

	private String name_variations; 
	
	// Constructors
	public InstrumentDTO() {}

	public InstrumentDTO(int instrument_id, String instrument_name, String name_variations) {
		this.instrument_id = instrument_id;
		this.instrument_name = instrument_name;
		this.name_variations = name_variations;
	}

	// Static factory method to convert from Entity
	public static InstrumentDTO fromEntity(Instrument instrument) {
		return new InstrumentDTO(
			instrument.getInstrument_id(),
			instrument.getInstrument_name(),
			instrument.getName_variations()
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

	
	
	public String getName_variations() {
		return name_variations;
	}

	public void setName_variations(String name_variations) {
		this.name_variations = name_variations;
	}

	public void setInstrument_name(String instrument_name) {
		this.instrument_name = instrument_name;
	}
}
