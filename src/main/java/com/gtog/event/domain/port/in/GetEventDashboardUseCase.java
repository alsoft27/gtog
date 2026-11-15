package com.gtog.event.domain.port.in;

import com.gtog.event.domain.model.EventAttendanceSummary;

public interface GetEventDashboardUseCase {
	EventAttendanceSummary getDashboard(String hostId, String eventId);
}
