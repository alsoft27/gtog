package com.gtog.event.domain.port.in;

import java.util.List;

import com.gtog.event.domain.model.Event;

public interface ListEventsByHostUseCase {

	List<Event> listEventsByHost(String hostId);
}
