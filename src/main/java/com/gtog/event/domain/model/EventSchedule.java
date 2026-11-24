package com.gtog.event.domain.model;

// Cada tipo temporal aporta sus propios datos; una consulta no necesita fechas ficticias.
public sealed interface EventSchedule permits FixedDateSchedule {
	EventType type();
	String timeZone();
}
