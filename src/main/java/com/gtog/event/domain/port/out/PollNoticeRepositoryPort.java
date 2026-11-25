package com.gtog.event.domain.port.out;

import java.util.stream.Stream;

import com.gtog.event.domain.model.Event;

public interface PollNoticeRepositoryPort {
	Stream<Event> findUnclaimedPublishedPolls();
	boolean claim(String eventId, long eventVersion, long pollRevision);
}
