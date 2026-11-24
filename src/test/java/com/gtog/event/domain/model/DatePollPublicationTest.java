package com.gtog.event.domain.model;

import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.gtog.event.DatePollTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

class DatePollPublicationTest {
	@Test void publishingPreservesTheCandidatesAndRevisionWithoutCreatingDatesOrOptions() {
		Event event = draft(null); var before = event.getSchedule(); event.publish();
		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.getSchedule()).isEqualTo(before);
		assertThat(event.getStartsAt()).isNull(); assertThat(event.getEndsAt()).isNull();
		assertThat(event.startsAtInstant()).isNull(); assertThat(event.endsAtInstant()).isNull();
		assertThat(event.getResponseOptions()).isEmpty();
	}
	@Test void publishingWithPublicIntentCreatesTheLinkOnlyAtPublication() {
		Event original = draft(null);
		Event event = Event.reconstituteBuilder().id(original.getId()).hostId(original.getHostId()).title(original.getTitle())
				.status(EventStatus.DRAFT).schedule(original.getSchedule()).responseDeadline(original.getResponseDeadline())
				.modality(original.getModality()).venue(original.getVenue()).responseOptions(List.of())
				.publicInvitation(new PublicInvitation(true, null)).build();
		AtomicInteger generated = new AtomicInteger();
		assertThat(event.getPublicInvitation().token()).isNull();
		event.publish(() -> { generated.incrementAndGet(); return "X".repeat(43); });
		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.getPublicInvitation().token()).hasSize(43);
		assertThat(generated.get()).isEqualTo(1);
	}
	@ParameterizedTest @EnumSource(LinkVisibility.class)
	void onlineVisibilityDoesNotRequireFakeStartInstants(LinkVisibility visibility) {
		Event event = published(visibility);
		assertThat(event.visibleOnlineAccess(false, Instant.parse("2027-03-01T10:00:00Z")).isPresent()).isEqualTo(visibility == LinkVisibility.ALWAYS);
	}
	@ParameterizedTest @ValueSource(strings = {"2027-03-01T10:00:00Z", "2027-05-10T18:00:00Z", "2027-06-01T10:00:00Z"})
	void hoursBeforeStaysHiddenWhileThereIsNoDefinitiveStart(String now) {
		assertThat(published(LinkVisibility.HOURS_BEFORE).visibleOnlineAccess(false, Instant.parse(now))).isEmpty();
	}
	@Test void cancellationHidesEvenAnAlwaysVisibleRoomAndRetainsCandidates() {
		Event event = published(LinkVisibility.ALWAYS); var before = event.getSchedule();
		event.cancel("Cancelado", Instant.parse("2027-03-01T10:00:00Z"));
		assertThat(event.visibleOnlineAccess(true, Instant.parse("2027-03-01T10:00:00Z"))).isEmpty();
		assertThat(event.getSchedule()).isEqualTo(before);
	}
	@Test void aPollCannotStoreAnAttendanceOptionResponse() {
		assertThatThrownBy(() -> GuestResponse.create(published(null), "guest", "option", null, null,
				Instant.parse("2027-03-01T10:00:00Z"))).isInstanceOf(EventScheduleConflictException.class);
	}
}
