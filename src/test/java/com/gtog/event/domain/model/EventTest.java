package com.gtog.event.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

	private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 9, 1, 20, 0);
	private static final LocalDateTime ENDS_AT = LocalDateTime.of(2026, 9, 1, 23, 0);
	private static final String TIME_ZONE = "Europe/Madrid";

	@Test
	void createSetsStatusDraft() {
		Event event = createEvent();

		assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
	}

	@Test
	void createGeneratesNonNullUniqueId() {
		Event first = createEvent();
		Event second = createEvent();

		assertThat(first.getId()).isNotNull();
		assertThat(first.getId()).isNotEqualTo(second.getId());
	}

	@Test
	void createSetsVersionNull() {
		Event event = createEvent();

		assertThat(event.getVersion()).isNull();
	}

	@Test
	void startsAtInstantAndEndsAtInstantAreComputedFromLocalTimeAndTimeZone() {
		Event event = createEvent();

		assertThat(event.startsAtInstant()).isEqualTo(STARTS_AT.atZone(ZoneOffset.of("+02:00")).toInstant());
		assertThat(event.endsAtInstant()).isEqualTo(ENDS_AT.atZone(ZoneOffset.of("+02:00")).toInstant());
	}

	@Test
	void doesNotAllowEndsAtBeforeStartsAt() {
		LocalDateTime endsAt = STARTS_AT.minusHours(1);

		assertThatThrownBy(() -> baseBuilder().endsAt(endsAt).build())
				.isInstanceOf(InvalidEventPeriodException.class);
	}

	@Test
	void doesNotAllowEndsAtEqualToStartsAt() {
		assertThatThrownBy(() -> baseBuilder().endsAt(STARTS_AT).build())
				.isInstanceOf(InvalidEventPeriodException.class);
	}

	@Test
	void rejectsInvalidTimeZone() {
		assertThatThrownBy(() -> baseBuilder().timeZone("Not/AZone").build())
				.isInstanceOf(InvalidTimeZoneException.class);
	}

	@Test
	void rejectsNullTitle() {
		assertThatThrownBy(() -> baseBuilder().title(null).build()).isInstanceOf(BlankEventTitleException.class);
	}

	@Test
	void rejectsBlankTitle() {
		assertThatThrownBy(() -> baseBuilder().title("   ").build()).isInstanceOf(BlankEventTitleException.class);
	}

	@Test
	void reconstitutesAnExistingEventWithoutRevalidating() {
		List<ResponseOption> responseOptions = List.of(ResponseOption.create("Asisto", true));
		Event event = Event.reconstituteBuilder()
				.id("event-1")
				.hostId("host-1")
				.title("Cumpleaños")
				.description("Descripción")
				.startsAt(STARTS_AT)
				.endsAt(ENDS_AT)
				.timeZone(TIME_ZONE)
				.modality(Modality.IN_PERSON)
				.status(EventStatus.PUBLISHED)
				.responseOptions(responseOptions)
				.allowComment(true)
				.allowResponseChange(false)
				.version(3L)
				.build();

		assertThat(event.getId()).isEqualTo("event-1");
		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.getVersion()).isEqualTo(3L);
		assertThat(event.getResponseOptions()).isEqualTo(responseOptions);
		assertThat(event.isAllowComment()).isTrue();
		assertThat(event.isAllowResponseChange()).isFalse();
	}

	@Test
	void createAppliesDefaultResponseOptionsWhenNoneProvided() {
		Event event = createEvent();

		assertThat(event.getResponseOptions()).hasSize(2);
		assertThat(event.getResponseOptions().get(0).label()).isEqualTo("Asisto");
		assertThat(event.getResponseOptions().get(0).countsAsAttendance()).isTrue();
		assertThat(event.getResponseOptions().get(1).label()).isEqualTo("No asisto");
		assertThat(event.getResponseOptions().get(1).countsAsAttendance()).isFalse();
	}

	@Test
	void createAcceptsProvidedResponseOptions() {
		Event event = createEvent(List.of(
				new ResponseOptionDraft("Voy", true),
				new ResponseOptionDraft("No voy", false),
				new ResponseOptionDraft("Quizas", false)));

		assertThat(event.getResponseOptions()).extracting(ResponseOption::label)
				.containsExactly("Voy", "No voy", "Quizas");
		assertThat(event.getResponseOptions()).allSatisfy(option -> assertThat(option.id()).isNotNull());
	}

	@Test
	void createRejectsFewerThanTwoResponseOptions() {
		List<ResponseOptionDraft> drafts = List.of(new ResponseOptionDraft("Asisto", true));

		assertThatThrownBy(() -> createEvent(drafts)).isInstanceOf(InvalidResponseOptionCountException.class);
	}

	@Test
	void createRejectsMoreThanFiveResponseOptions() {
		List<ResponseOptionDraft> drafts = List.of(
				new ResponseOptionDraft("A", true),
				new ResponseOptionDraft("B", false),
				new ResponseOptionDraft("C", false),
				new ResponseOptionDraft("D", false),
				new ResponseOptionDraft("E", false),
				new ResponseOptionDraft("F", false));

		assertThatThrownBy(() -> createEvent(drafts)).isInstanceOf(InvalidResponseOptionCountException.class);
	}

	@Test
	void createRejectsResponseOptionsWithoutAnyCountingAsAttendance() {
		List<ResponseOptionDraft> drafts = List.of(
				new ResponseOptionDraft("Asisto", false),
				new ResponseOptionDraft("No asisto", false));

		assertThatThrownBy(() -> createEvent(drafts)).isInstanceOf(NoAttendanceResponseOptionException.class);
	}

	@Test
	void createRejectsDuplicateResponseOptionLabels() {
		List<ResponseOptionDraft> drafts = List.of(
				new ResponseOptionDraft("Asisto", true),
				new ResponseOptionDraft("Asisto", false));

		assertThatThrownBy(() -> createEvent(drafts)).isInstanceOf(DuplicateResponseOptionLabelException.class);
	}

	@Test
	void createRejectsBlankResponseOptionLabel() {
		List<ResponseOptionDraft> drafts = List.of(
				new ResponseOptionDraft(" ", true),
				new ResponseOptionDraft("No asisto", false));

		assertThatThrownBy(() -> createEvent(drafts)).isInstanceOf(BlankResponseOptionLabelException.class);
	}

	@Test
	void createDefaultsAllowCommentToFalseAndAllowResponseChangeToTrue() {
		Event event = createEvent();

		assertThat(event.isAllowComment()).isFalse();
		assertThat(event.isAllowResponseChange()).isTrue();
	}

	@Test
	void createHonoursExplicitAllowCommentAndAllowResponseChange() {
		Event event = baseBuilder().allowComment(true).allowResponseChange(false).build();

		assertThat(event.isAllowComment()).isTrue();
		assertThat(event.isAllowResponseChange()).isFalse();
	}

	@Test
	void createRejectsResponseDeadlineAfterStartsAt() {
		LocalDateTime deadline = STARTS_AT.plusMinutes(1);

		assertThatThrownBy(() -> baseBuilder().responseDeadline(deadline).build())
				.isInstanceOf(InvalidResponseDeadlineException.class);
	}

	@Test
	void createAcceptsResponseDeadlineEqualToStartsAt() {
		Event event = baseBuilder().responseDeadline(STARTS_AT).build();

		assertThat(event.getResponseDeadline()).isEqualTo(STARTS_AT);
	}

	@Test
	void replaceResponseOptionsPreservesIdOfExistingOptionMatchedById() {
		Event event = createEvent();
		String existingId = event.getResponseOptions().get(0).id();

		event.replaceResponseOptions(List.of(
				new ResponseOptionEdit(existingId, "Asisto seguro", true),
				new ResponseOptionEdit(null, "No asisto", false)), false, true, null);

		assertThat(event.getResponseOptions().get(0).id()).isEqualTo(existingId);
		assertThat(event.getResponseOptions().get(0).label()).isEqualTo("Asisto seguro");
	}

	@Test
	void replaceResponseOptionsGeneratesNewIdForOptionsWithoutId() {
		Event event = createEvent();

		event.replaceResponseOptions(List.of(
				new ResponseOptionEdit(null, "Asisto", true),
				new ResponseOptionEdit(null, "No asisto", false)), false, true, null);

		assertThat(event.getResponseOptions()).allSatisfy(option -> assertThat(option.id()).isNotNull());
	}

	@Test
	void replaceResponseOptionsRejectsAnIdThatDoesNotBelongToTheEvent() {
		Event event = createEvent();

		assertThatThrownBy(() -> event.replaceResponseOptions(List.of(
				new ResponseOptionEdit("unknown-id", "Asisto", true),
				new ResponseOptionEdit(null, "No asisto", false)), false, true, null))
				.isInstanceOf(UnknownResponseOptionIdException.class);
	}

	@Test
	void replaceResponseOptionsValidatesTheResultingListJustLikeCreate() {
		Event event = createEvent();

		assertThatThrownBy(() -> event.replaceResponseOptions(
				List.of(new ResponseOptionEdit(null, "Solo una", true)), false, true, null))
				.isInstanceOf(InvalidResponseOptionCountException.class);
	}

	@Test
	void replaceResponseOptionsUpdatesAllowCommentAllowResponseChangeAndResponseDeadline() {
		Event event = createEvent();

		event.replaceResponseOptions(List.of(
				new ResponseOptionEdit(null, "Asisto", true),
				new ResponseOptionEdit(null, "No asisto", false)), true, false, STARTS_AT);

		assertThat(event.isAllowComment()).isTrue();
		assertThat(event.isAllowResponseChange()).isFalse();
		assertThat(event.getResponseDeadline()).isEqualTo(STARTS_AT);
	}

	@Test
	void replaceResponseOptionsRejectsResponseDeadlineAfterStartsAt() {
		Event event = createEvent();
		LocalDateTime deadline = STARTS_AT.plusMinutes(1);

		assertThatThrownBy(() -> event.replaceResponseOptions(List.of(
				new ResponseOptionEdit(null, "Asisto", true),
				new ResponseOptionEdit(null, "No asisto", false)), false, true, deadline))
				.isInstanceOf(InvalidResponseDeadlineException.class);
	}

	@Test
	void doesNotAllowReplacingResponseOptionsWhenEventIsNotDraft() {
		List<ResponseOption> responseOptions = List.of(ResponseOption.create("Asisto", true),
				ResponseOption.create("No asisto", false));
		Event event = Event.reconstituteBuilder()
				.id("event-1")
				.hostId("host-1")
				.title("Cumpleaños")
				.description("Descripción")
				.startsAt(STARTS_AT)
				.endsAt(ENDS_AT)
				.timeZone(TIME_ZONE)
				.modality(Modality.IN_PERSON)
				.status(EventStatus.PUBLISHED)
				.responseOptions(responseOptions)
				.allowComment(false)
				.allowResponseChange(true)
				.version(0L)
				.build();

		assertThatThrownBy(() -> event.replaceResponseOptions(List.of(
				new ResponseOptionEdit(null, "Asisto", true),
				new ResponseOptionEdit(null, "No asisto", false)), false, true, null))
				.isInstanceOf(EventNotEditableException.class);
	}

	@Test
	void createRejectsInPersonEventWithoutVenue() {
		assertThatThrownBy(() -> baseBuilder().venue(null).build()).isInstanceOf(MissingVenueException.class);
	}

	@Test
	void createRejectsInPersonEventWithOnlineAccess() {
		assertThatThrownBy(() -> baseBuilder().onlineAccess(anOnlineAccess()).build())
				.isInstanceOf(UnexpectedOnlineAccessException.class);
	}

	@Test
	void createRejectsOnlineEventWithoutOnlineAccess() {
		assertThatThrownBy(() -> baseBuilder().modality(Modality.ONLINE).venue(null).build())
				.isInstanceOf(MissingOnlineAccessException.class);
	}

	@Test
	void createRejectsOnlineEventWithVenue() {
		assertThatThrownBy(() -> baseBuilder().modality(Modality.ONLINE).onlineAccess(anOnlineAccess()).build())
				.isInstanceOf(UnexpectedVenueException.class);
	}

	@Test
	void createAcceptsOnlineEventWithOnlineAccessAndNoVenue() {
		Event event = baseBuilder().modality(Modality.ONLINE).venue(null).onlineAccess(anOnlineAccess()).build();

		assertThat(event.getOnlineAccess()).isEqualTo(anOnlineAccess());
		assertThat(event.getVenue()).isNull();
	}

	@Test
	void replaceVenueUpdatesTheVenueWhileInPersonAndDraft() {
		Event event = createEvent();
		Venue newVenue = new Venue("Otra sala", "Otra direccion", 0.0, 0.0, "other-place-id", null);

		event.replaceVenue(newVenue);

		assertThat(event.getVenue()).isEqualTo(newVenue);
	}

	@Test
	void replaceVenueRejectsWhenEventIsNotDraft() {
		Event event = Event.reconstituteBuilder()
				.id("event-1")
				.hostId("host-1")
				.title("Cumpleaños")
				.description("Descripción")
				.startsAt(STARTS_AT)
				.endsAt(ENDS_AT)
				.timeZone(TIME_ZONE)
				.modality(Modality.IN_PERSON)
				.status(EventStatus.PUBLISHED)
				.responseOptions(List.of(ResponseOption.create("Asisto", true)))
				.allowComment(false)
				.allowResponseChange(true)
				.venue(aVenue())
				.version(0L)
				.build();

		assertThatThrownBy(() -> event.replaceVenue(aVenue())).isInstanceOf(EventNotEditableException.class);
	}

	@Test
	void replaceVenueRejectsWhenEventIsOnline() {
		Event event = baseBuilder().modality(Modality.ONLINE).venue(null).onlineAccess(anOnlineAccess()).build();

		assertThatThrownBy(() -> event.replaceVenue(aVenue())).isInstanceOf(UnexpectedVenueException.class);
	}

	@Test
	void replaceOnlineAccessUpdatesItWhileOnlineAndDraft() {
		Event event = baseBuilder().modality(Modality.ONLINE).venue(null).onlineAccess(anOnlineAccess()).build();
		OnlineAccess newOnlineAccess = new OnlineAccess("Teams", "https://teams.microsoft.com/x", null, null, null,
				LinkVisibility.ALWAYS, null);

		event.replaceOnlineAccess(newOnlineAccess);

		assertThat(event.getOnlineAccess()).isEqualTo(newOnlineAccess);
	}

	@Test
	void replaceOnlineAccessRejectsWhenEventIsInPerson() {
		Event event = createEvent();

		assertThatThrownBy(() -> event.replaceOnlineAccess(anOnlineAccess()))
				.isInstanceOf(UnexpectedOnlineAccessException.class);
	}

	@Test
	void visibleOnlineAccessIsEmptyWhenEventHasNoOnlineAccess() {
		Event event = createEvent();

		assertThat(event.visibleOnlineAccess(true, Instant.now())).isEmpty();
	}

	@Test
	void visibleOnlineAccessIsAlwaysVisible() {
		OnlineAccess onlineAccess = new OnlineAccess("Zoom", "https://zoom.us/j/123", null, null, null,
				LinkVisibility.ALWAYS, null);
		Event event = baseBuilder().modality(Modality.ONLINE).venue(null).onlineAccess(onlineAccess).build();

		assertThat(event.visibleOnlineAccess(false, STARTS_AT.minusYears(1).atZone(ZoneOffset.of("+02:00")).toInstant()))
				.contains(onlineAccess);
	}

	@Test
	void visibleOnlineAccessOnConfirmationRequiresGuestToHaveConfirmed() {
		OnlineAccess onlineAccess = new OnlineAccess("Zoom", "https://zoom.us/j/123", null, null, null,
				LinkVisibility.ON_CONFIRMATION, null);
		Event event = baseBuilder().modality(Modality.ONLINE).venue(null).onlineAccess(onlineAccess).build();
		Instant now = STARTS_AT.atZone(ZoneOffset.of("+02:00")).toInstant();

		assertThat(event.visibleOnlineAccess(false, now)).isEmpty();
		assertThat(event.visibleOnlineAccess(true, now)).contains(onlineAccess);
	}

	@Test
	void visibleOnlineAccessHoursBeforeIsHiddenBeforeTheWindow() {
		OnlineAccess onlineAccess = new OnlineAccess("Zoom", "https://zoom.us/j/123", null, null, null,
				LinkVisibility.HOURS_BEFORE, 2);
		Event event = baseBuilder().modality(Modality.ONLINE).venue(null).onlineAccess(onlineAccess).build();
		Instant threeHoursBeforeStart = event.startsAtInstant().minus(3, ChronoUnit.HOURS);

		assertThat(event.visibleOnlineAccess(true, threeHoursBeforeStart)).isEmpty();
	}

	@Test
	void visibleOnlineAccessHoursBeforeIsVisibleInsideTheWindow() {
		OnlineAccess onlineAccess = new OnlineAccess("Zoom", "https://zoom.us/j/123", null, null, null,
				LinkVisibility.HOURS_BEFORE, 2);
		Event event = baseBuilder().modality(Modality.ONLINE).venue(null).onlineAccess(onlineAccess).build();
		Instant oneHourBeforeStart = event.startsAtInstant().minus(1, ChronoUnit.HOURS);

		assertThat(event.visibleOnlineAccess(false, oneHourBeforeStart)).contains(onlineAccess);
	}

	private Event createEvent() {
		return baseBuilder().build();
	}

	private Event createEvent(List<ResponseOptionDraft> responseOptionDrafts) {
		return baseBuilder().responseOptions(responseOptionDrafts).build();
	}

	private Event.Builder baseBuilder() {
		return Event.builder()
				.hostId("host-1")
				.title("Cumpleaños")
				.description("Descripción")
				.startsAt(STARTS_AT)
				.endsAt(ENDS_AT)
				.timeZone(TIME_ZONE)
				.modality(Modality.IN_PERSON)
				.venue(aVenue());
	}

	private Venue aVenue() {
		return new Venue("Sala Apolo", "Carrer Nou de la Rambla, 113, Barcelona", 41.3767, 2.1662,
				"ChIJT7Xj1uOipBIRdKY0X_0V7Xk", null);
	}

	private OnlineAccess anOnlineAccess() {
		return new OnlineAccess("Zoom", "https://zoom.us/j/123", null, null, null, LinkVisibility.ALWAYS, null);
	}
}
