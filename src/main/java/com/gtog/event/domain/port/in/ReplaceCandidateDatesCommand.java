package com.gtog.event.domain.port.in;

import java.util.List;
import com.gtog.event.domain.model.CandidateDateEdit;

public record ReplaceCandidateDatesCommand(String hostId, String eventId, Long pollRevision, List<CandidateDateEdit> candidateDates) {}
