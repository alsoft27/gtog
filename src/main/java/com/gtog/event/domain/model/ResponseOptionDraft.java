package com.gtog.event.domain.model;

// Entrada para crear un evento: todavia no tiene id, el dominio lo genera al construir el ResponseOption.
public record ResponseOptionDraft(String label, boolean countsAsAttendance) {
}
