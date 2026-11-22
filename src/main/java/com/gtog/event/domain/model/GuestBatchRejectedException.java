package com.gtog.event.domain.model;

import java.util.List;

public class GuestBatchRejectedException extends EventDomainException {
	private final List<GuestBatchError> errors;
	private final boolean conflict;
	public GuestBatchRejectedException(List<GuestBatchError> errors, boolean conflict) {
		super("Guest batch rejected"); this.errors = List.copyOf(errors); this.conflict = conflict;
	}
	public List<GuestBatchError> getErrors() { return errors; }
	public boolean isConflict() { return conflict; }
}
