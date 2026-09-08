package com.gtog.event.domain.port.in;

import com.gtog.event.domain.model.OnlineAccess;

public record ReplaceOnlineAccessCommand(String hostId, String eventId, OnlineAccess onlineAccess) {
}
