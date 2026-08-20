package com.gtog.event.domain.port.in;

import com.gtog.event.domain.model.OnlineAccess;

public record ReplaceOnlineAccessCommand(String eventId, OnlineAccess onlineAccess) {
}
