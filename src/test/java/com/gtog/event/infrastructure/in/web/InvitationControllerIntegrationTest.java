package com.gtog.event.infrastructure.in.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationStatus;
import com.gtog.event.domain.model.LinkVisibility;
import com.gtog.event.domain.port.out.EventGuestRepositoryPort;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.EventMongoRepository;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.gtog.user.infrastructure.out.persistence.UserMongoRepository;

import static com.gtog.event.InvitationTestFixtures.NOW;
import static com.gtog.event.InvitationTestFixtures.URL;
import static com.gtog.event.InvitationTestFixtures.PASSWORD;
import static com.gtog.event.InvitationTestFixtures.ROOM;
import static com.gtog.event.InvitationTestFixtures.INSTRUCTIONS;
import static com.gtog.event.InvitationTestFixtures.event;
import static com.gtog.event.InvitationTestFixtures.guest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class InvitationControllerIntegrationTest {

	@Autowired private MockMvc mockMvc;
	@MockitoSpyBean private EventRepositoryPort events;
	@Autowired private EventGuestRepositoryPort guests;
	@Autowired private EventMongoRepository eventDocuments;
	@Autowired private UserMongoRepository users;
	@MockitoBean private Clock clock;

	@BeforeEach
	void setUp() {
		eventDocuments.deleteAll();
		users.deleteAll();
		UserDocument host = new UserDocument();
		host.setId("host-1");
		host.setName("Host");
		host.setEmail("host@example.com");
		host.setPasswordHash("unused-test-hash");
		host.setTimeZone("Europe/Madrid");
		users.save(host);
		fixedTime(NOW);
	}

	@Test
	void publiclyReturnsOnlyTheInvitedGuestsNameAndSafeEventFields() throws Exception {
		Guest first = guest(101, InvitationStatus.NOT_SENT, null);
		Guest other = guest(202, InvitationStatus.NOT_SENT, null);
		events.save(event(EventStatus.PUBLISHED, null, first, other));
		String body = mockMvc.perform(get(path(first)))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.guest.name").value(first.getName()))
				.andExpect(jsonPath("$.guest.id").doesNotExist())
				.andExpect(jsonPath("$.title").value("Public event"))
				.andExpect(jsonPath("$.description").value("Public description"))
				.andExpect(jsonPath("$.status").value("PUBLISHED"))
				.andExpect(jsonPath("$.modality").value("IN_PERSON"))
				.andExpect(jsonPath("$.startsAt").value("2027-01-01T18:00:00"))
				.andExpect(jsonPath("$.endsAt").value("2027-01-01T20:00:00"))
				.andExpect(jsonPath("$.timeZone").value("Europe/Madrid"))
				.andExpect(jsonPath("$.startsAtInstant").value("2027-01-01T17:00:00Z"))
				.andExpect(jsonPath("$.endsAtInstant").value("2027-01-01T19:00:00Z"))
				.andExpect(jsonPath("$.venue.placeName").value("Sala"))
				.andExpect(jsonPath("$.venue.address").value("Direccion"))
				.andExpect(jsonPath("$.venue.latitude").value(40.0))
				.andExpect(jsonPath("$.venue.longitude").value(-3.0))
				.andExpect(jsonPath("$.venue.placeId").value("place-id"))
				.andExpect(jsonPath("$.venue.directions").value("Entrada"))
				.andExpect(jsonPath("$.responseOptions[0].id").value("yes-id"))
				.andExpect(jsonPath("$.responseOptions[0].label").value("Asisto"))
				.andExpect(jsonPath("$.allowComment").value(true))
				.andExpect(jsonPath("$.allowResponseChange").value(false))
				.andExpect(jsonPath("$.responseDeadline").value("2027-01-01T17:00:00"))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(first.getToken(), other.getToken(), other.getName(), first.getEmail(),
				other.getEmail(), first.getPhoneE164(), other.getPhoneE164(), "host-1", "hostId", "guests", "countsAsAttendance", "version", "guest-101", "guest-202");
	}

	@Test
	void unknownTokenAndDraftHaveExactlyTheSameErrorWithoutLeakingTokens(CapturedOutput output) throws Exception {
		Guest first = guest(101, InvitationStatus.NOT_SENT, null);
		events.save(event(EventStatus.DRAFT, LinkVisibility.ALWAYS, first));
		String unknown = mockMvc.perform(get("/api/invitations/unknown-private-token"))
				.andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.detail").value("Invitation not found"))
				.andExpect(jsonPath("$.instance").value("/api/invitations"))
				.andReturn().getResponse().getContentAsString();
		String draft = mockMvc.perform(get(path(first))).andExpect(status().isNotFound())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andReturn().getResponse().getContentAsString();
		assertThat(draft).isEqualTo(unknown);
		assertThat(draft).doesNotContain(first.getToken(), "unknown-private-token");
		Guest stored = events.findById("event-id").orElseThrow().getGuest(first.getId());
		assertThat(stored.getInvitationStatus()).isEqualTo(InvitationStatus.NOT_SENT);
		assertThat(stored.getOpenedAt()).isNull();
		assertThat(output.getAll()).doesNotContain(first.getToken(), "unknown-private-token");
	}

	@Test
	void confirmationAccessIsOmittedCompletelyButItsSafeMetadataIsPresent() throws Exception {
		Guest first = guest(101, InvitationStatus.NOT_SENT, null);
		events.save(event(EventStatus.PUBLISHED, LinkVisibility.ON_CONFIRMATION, first));
		String body = mockMvc.perform(get(path(first))).andExpect(status().isOk())
				.andExpect(jsonPath("$.onlineAccess").doesNotExist())
				.andExpect(jsonPath("$.onlineAccessInfo.platform").value("Zoom"))
				.andExpect(jsonPath("$.onlineAccessInfo.linkVisibility").value("ON_CONFIRMATION"))
				.andReturn().getResponse().getContentAsString();
		assertNoSecrets(body);
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 0, 1})
	void hoursBeforeVisibilityUsesTheFixedClockOnBothSidesAndAtTheThreshold(int secondsFromThreshold) throws Exception {
		fixedTime(NOW.plusSeconds(secondsFromThreshold));
		Guest first = guest(101, InvitationStatus.NOT_SENT, null);
		events.save(event(EventStatus.PUBLISHED, LinkVisibility.HOURS_BEFORE, first));
		MvcResult result = mockMvc.perform(get(path(first))).andExpect(status().isOk())
				.andExpect(jsonPath("$.onlineAccessInfo.hoursBefore").value(2)).andReturn();
		String body = result.getResponse().getContentAsString();
		if (secondsFromThreshold < 0) {
			assertNoSecrets(body);
			assertThat(body).doesNotContain("\"onlineAccess\"");
		} else {
			assertThat(body).contains(URL, PASSWORD, ROOM, INSTRUCTIONS);
		}
	}

	@Test
	void alwaysAccessIsVisibleEvenLongBeforeTheEvent() throws Exception {
		fixedTime(NOW.minusSeconds(86400));
		Guest first = guest(101, InvitationStatus.NOT_SENT, null);
		events.save(event(EventStatus.PUBLISHED, LinkVisibility.ALWAYS, first));
		String body = mockMvc.perform(get(path(first))).andExpect(status().isOk())
				.andExpect(jsonPath("$.onlineAccess.url").value(URL))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).contains(URL, PASSWORD, ROOM, INSTRUCTIONS);
	}

	@ParameterizedTest
	@EnumSource(LinkVisibility.class)
	void cancelledEventsExposeTheirReasonButNeverAccessOrOpening(LinkVisibility visibility) throws Exception {
		Guest first = guest(101, InvitationStatus.NOT_SENT, null);
		events.save(event(EventStatus.CANCELLED, visibility, first));
		String body = mockMvc.perform(get(path(first))).andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancelledAt").value(NOW.minusSeconds(60).toString()))
				.andExpect(jsonPath("$.cancellationReason").value("Cancelled for testing"))
				.andExpect(jsonPath("$.onlineAccess").doesNotExist()).andReturn().getResponse().getContentAsString();
		assertNoSecrets(body);
		Event stored = events.findById("event-id").orElseThrow();
		assertThat(stored.getGuest(first.getId()).getOpenedAt()).isNull();
		assertThat(stored.getGuest(first.getId()).getInvitationStatus()).isEqualTo(InvitationStatus.NOT_SENT);
		assertThat(stored.getVersion()).isZero();
	}

	@Test
	void finishedEventsRemainPublicWithoutExposingAccessOrOpening() throws Exception {
		Guest first = guest(101, InvitationStatus.SENT, null);
		events.save(event(EventStatus.FINISHED, LinkVisibility.ALWAYS, first));
		String body = mockMvc.perform(get(path(first))).andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.status").value("FINISHED"))
				.andExpect(jsonPath("$.onlineAccess").doesNotExist()).andReturn().getResponse().getContentAsString();
		assertNoSecrets(body);
		Event stored = events.findById("event-id").orElseThrow();
		assertThat(stored.getGuest(first.getId()).getInvitationStatus()).isEqualTo(InvitationStatus.SENT);
		assertThat(stored.getVersion()).isZero();
	}

	@ParameterizedTest
	@EnumSource(value = InvitationStatus.class, names = {"NOT_SENT", "SENT"})
	void recordsOnlyTheFirstVisitWithoutSessionOrCsrfAndNeverChangesItsTimestamp(InvitationStatus initial) throws Exception {
		Guest first = guest(101, initial, null);
		Guest other = guest(202, InvitationStatus.NOT_SENT, null);
		events.save(event(EventStatus.PUBLISHED, null, first, other));
		mockMvc.perform(get(path(first))).andExpect(status().isOk());
		Event opened = events.findById("event-id").orElseThrow();
		assertThat(opened.getGuest(first.getId()).getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
		assertThat(opened.getGuest(first.getId()).getOpenedAt()).isEqualTo(NOW);
		assertThat(opened.getGuest(other.getId()).getInvitationStatus()).isEqualTo(InvitationStatus.NOT_SENT);
		assertThat(opened.getVersion()).isEqualTo(1L);
		fixedTime(NOW.plusSeconds(60));
		mockMvc.perform(get(path(first))).andExpect(status().isOk());
		Event revisited = events.findById("event-id").orElseThrow();
		assertThat(revisited.getGuest(first.getId()).getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
		assertThat(revisited.getGuest(first.getId()).getOpenedAt()).isEqualTo(NOW);
		assertThat(revisited.getVersion()).isEqualTo(1L);
	}

	@Test
	void elemMatchCannotUseAnotherGuestsPendingStateToOverwriteAnOpenedGuest() {
		Guest first = guest(101, InvitationStatus.OPENED, NOW.minusSeconds(60));
		Guest other = guest(202, InvitationStatus.NOT_SENT, null);
		events.save(event(EventStatus.PUBLISHED, null, first, other));
		assertThat(guests.markGuestOpened("event-id", first.getToken(), NOW)).isEmpty();
		Event unchanged = events.findById("event-id").orElseThrow();
		assertThat(unchanged.getGuest(first.getId()).getOpenedAt()).isEqualTo(first.getOpenedAt());
		assertThat(unchanged.getGuest(other.getId()).getInvitationStatus()).isEqualTo(InvitationStatus.NOT_SENT);
		assertThat(unchanged.getVersion()).isZero();
	}

	@Test
	void concurrentFirstVisitsPersistOnlyOneTimestampAndIncrementVersionOnce() throws Exception {
		Guest first = guest(101, InvitationStatus.NOT_SENT, null);
		events.save(event(EventStatus.PUBLISHED, null, first));
		CyclicBarrier barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var a = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return guests.markGuestOpened("event-id", first.getToken(), NOW); });
			var b = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return guests.markGuestOpened("event-id", first.getToken(), NOW.plusSeconds(1)); });
			Optional<Event> firstResult = a.get(15, TimeUnit.SECONDS);
			Optional<Event> secondResult = b.get(15, TimeUnit.SECONDS);
			assertThat(firstResult.isPresent()).isNotEqualTo(secondResult.isPresent());
			Event stored = events.findById("event-id").orElseThrow();
			assertThat(stored.getGuest(first.getId()).getOpenedAt()).isEqualTo(firstResult.isPresent() ? NOW : NOW.plusSeconds(1));
			assertThat(stored.getVersion()).isEqualTo(1L);
		}
	}

	@Test
	@WithUserDetails(value = "host@example.com", setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void aStaleHostCancellationAfterFirstOpeningReturns409WithProblemDetail() throws Exception {
		Guest first = guest(101, InvitationStatus.NOT_SENT, null);
		Event stale = events.save(event(EventStatus.PUBLISHED, null, first));
		mockMvc.perform(get(path(first))).andExpect(status().isOk());
		// Forzar la lectura anterior a la apertura: reproduce de forma determinista la carrera real del save.
		doReturn(Optional.of(stale)).when(events).findById("event-id");
		mockMvc.perform(post("/api/events/event-id/cancel").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Concurrent cancellation\"}"))
				.andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value("Event changed concurrently; reload and retry"));
		var persisted = eventDocuments.findById("event-id").orElseThrow();
		assertThat(persisted.getStatus()).isEqualTo("PUBLISHED");
		assertThat(persisted.getVersion()).isEqualTo(1L);
	}

	@Test
	void successfulVisitsDoNotLogEitherGuestsToken(CapturedOutput output) throws Exception {
		Guest first = guest(101, InvitationStatus.NOT_SENT, null);
		Guest other = guest(202, InvitationStatus.NOT_SENT, null);
		events.save(event(EventStatus.PUBLISHED, null, first, other));
		mockMvc.perform(get(path(first))).andExpect(status().isOk());
		assertThat(output.getAll()).doesNotContain(first.getToken(), other.getToken());
	}

	@Test
	void documentsThePublicEndpoint() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/invitations/{token}'].get.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/api/invitations/{token}'].get.responses['404']").exists());
	}

	private void fixedTime(Instant now) {
		Clock fixed = Clock.fixed(now, ZoneOffset.UTC);
		when(clock.instant()).thenReturn(fixed.instant());
	}

	private String path(Guest guest) {
		return "/api/invitations/" + guest.getToken();
	}

	private void assertNoSecrets(String body) {
		assertThat(body).doesNotContain(URL, PASSWORD, ROOM, INSTRUCTIONS);
	}
}
