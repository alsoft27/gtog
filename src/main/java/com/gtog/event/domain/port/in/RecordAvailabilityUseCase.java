package com.gtog.event.domain.port.in;

import java.util.List;
import com.gtog.event.domain.model.InvitationView;

public interface RecordAvailabilityUseCase {
	InvitationView recordAvailability(String token, Long pollRevision, List<String> candidateDateIds);
}
