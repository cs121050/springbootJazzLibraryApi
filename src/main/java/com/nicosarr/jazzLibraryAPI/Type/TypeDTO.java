package com.nicosarr.jazzLibraryAPI.Type;

public class TypeDTO {

	private int type_id;
	private String type_name;

	// Constructors
	public TypeDTO() {}

	public TypeDTO(int type_id, String type_name) {
		this.type_id = type_id;
		this.type_name = type_name;
	}

	// Static factory method to convert from Entity
	public static TypeDTO fromEntity(Type type) {
		return new TypeDTO(
			type.getType_id(),
			type.getType_name()
		);
	}

	// Getters and Setters
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
}
