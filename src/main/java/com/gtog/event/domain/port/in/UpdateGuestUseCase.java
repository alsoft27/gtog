package com.gtog.event.domain.port.in;

import com.gtog.event.domain.model.Guest;

public interface UpdateGuestUseCase {

	Guest update(UpdateGuestCommand command);
}
