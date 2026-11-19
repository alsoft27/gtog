package com.gtog.event.infrastructure.in.web;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record WhatsAppLinksRequest(@Schema(description = "Ids únicos; omitir para todos en orden de alta. Máximo configurable: 100 por defecto.") List<String> guestIds) {}
