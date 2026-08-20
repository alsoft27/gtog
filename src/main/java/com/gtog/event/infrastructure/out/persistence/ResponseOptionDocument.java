package com.gtog.event.infrastructure.out.persistence;

// Documento embebido en EventDocument.responseOptions, sin coleccion ni @Document propios.
public class ResponseOptionDocument {

	private final String id;
	private final String label;
	private final boolean countsAsAttendance;

	public ResponseOptionDocument(String id, String label, boolean countsAsAttendance) {
		this.id = id;
		this.label = label;
		this.countsAsAttendance = countsAsAttendance;
	}

	public String getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}

	public boolean isCountsAsAttendance() {
		return countsAsAttendance;
	}
}
