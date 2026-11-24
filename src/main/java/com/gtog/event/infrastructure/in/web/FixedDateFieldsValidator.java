package com.gtog.event.infrastructure.in.web;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import com.gtog.event.domain.model.EventType;

// Conserva el 400 de formato del contrato de fecha fija; las candidatas se validan en dominio (422).
public class FixedDateFieldsValidator implements ConstraintValidator<ValidFixedDateFields, TemporalEventRequest> {
	@Override public boolean isValid(TemporalEventRequest request, ConstraintValidatorContext context) {
		return request == null || request.eventType() == EventType.DATE_POLL
				|| (request.startsAt() != null && request.endsAt() != null);
	}
}
