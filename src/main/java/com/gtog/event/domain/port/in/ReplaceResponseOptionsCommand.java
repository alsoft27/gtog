package com.gtog.event.domain.port.in;

import java.util.List;

import com.gtog.event.domain.model.ResponseOptionEdit;

public record ReplaceResponseOptionsCommand(String eventId, List<ResponseOptionEdit> responseOptions) {
}
