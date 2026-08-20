package com.gtog.event.domain.model;

import java.util.UUID;

public record ResponseOption(String id, String label, boolean countsAsAttendance) {

	public ResponseOption {
		if (label == null || label.isBlank()) {
			throw new BlankResponseOptionLabelException();
		}
	}

	public static ResponseOption create(String label, boolean countsAsAttendance) {
		return new ResponseOption(UUID.randomUUID().toString(), label, countsAsAttendance);
	}
}
