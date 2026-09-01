package com.gtog.event.domain.model;

public class ModalityChangedWithoutLocationException extends EventDomainException {

	public ModalityChangedWithoutLocationException() {
		super("Modality change requires the location block for the new modality");
	}
}
