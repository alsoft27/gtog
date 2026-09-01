package com.gtog.event.domain.port.in;

import com.gtog.event.domain.model.Event;

public interface UpdateEventUseCase {

	Event update(UpdateEventCommand command);
}
