package com.gtog.event.domain.port.in;

import java.util.List;

import com.gtog.event.domain.model.EventSummary;

public interface ListEventsByHostUseCase {

	List<EventSummary> listEventsByHost(String hostId);
}
