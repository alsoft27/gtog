package com.gtog.event.domain.port.out;

import com.gtog.event.domain.model.InvitationCalendarData;

public interface InvitationCalendarRendererPort {
	byte[] render(InvitationCalendarData calendar);
}
