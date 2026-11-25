package com.gtog.event.domain.port.out;

import java.util.Optional;
import java.util.Set;
import java.util.List;
import com.gtog.event.domain.model.GuestResponse;
import com.gtog.event.domain.model.GuestAvailability;
import com.gtog.event.domain.model.GuestAvailabilityResponse;

public interface GuestResponseRepositoryPort {
	GuestResponse insert(GuestResponse response);
	GuestResponse upsert(GuestResponse response);
	Optional<GuestResponse> find(String eventId, String guestToken);
	Set<String> findAnsweredOptionIds(String eventId);
	List<GuestResponse> findByEventIds(Set<String> eventIds);
	GuestAvailability saveAvailability(String eventId, String guestToken, GuestAvailability availability, boolean allowChange);
	Optional<GuestAvailability> findAvailability(String eventId, String guestToken, long revision);
	List<GuestAvailabilityResponse> findAvailabilityByEventIds(Set<String> eventIds);
	Set<String> findAvailabilityTokens(String eventId, long revision);
}
