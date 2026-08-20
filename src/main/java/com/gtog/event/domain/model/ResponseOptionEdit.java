package com.gtog.event.domain.model;

// Entrada para reemplazar las opciones de un evento existente. Id nulo significa opcion nueva; id no nulo
// debe coincidir con una opcion ya existente en el evento, o el dominio lo rechaza como referencia desconocida.
public record ResponseOptionEdit(String id, String label, boolean countsAsAttendance) {
}
