package com.gtog.event.infrastructure.in.web;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationStatus;
import com.gtog.event.domain.port.in.ChangeResponseDeadlineCommand;
import com.gtog.event.domain.port.in.ChangeResponseDeadlineUseCase;
import com.gtog.event.domain.port.out.EventGuestRepositoryPort;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.EventResponseDeadlineRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.EventMongoRepository;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.gtog.user.infrastructure.out.persistence.UserMongoRepository;

import static com.gtog.event.InvitationTestFixtures.NOW;
import static com.gtog.event.InvitationTestFixtures.event;
import static com.gtog.event.InvitationTestFixtures.guest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@WithUserDetails(value = "host@example.com", setupBefore = TestExecutionEvent.TEST_EXECUTION)
class ResponseDeadlineControllerIntegrationTest {

	private static final String PATH = "/api/events/event-id/response-deadline";
	private static final LocalDateTime DEADLINE = LocalDateTime.of(2027, 1, 1, 17, 30);
	private final MockMvc mockMvc;
	@MockitoSpyBean private EventRepositoryPort events;
	private final EventResponseDeadlineRepositoryPort deadlines;
	private final EventGuestRepositoryPort guests;
	private final ChangeResponseDeadlineUseCase changeDeadline;
	private final EventMongoRepository eventDocuments;
	private final UserMongoRepository users;
	@MockitoBean private Clock clock;

	ResponseDeadlineControllerIntegrationTest(MockMvc mockMvc, EventResponseDeadlineRepositoryPort deadlines,
			EventGuestRepositoryPort guests, ChangeResponseDeadlineUseCase changeDeadline,
			EventMongoRepository eventDocuments, UserMongoRepository users) {
		this.mockMvc = mockMvc;
		this.deadlines = deadlines;
		this.guests = guests;
		this.changeDeadline = changeDeadline;
		this.eventDocuments = eventDocuments;
		this.users = users;
	}

	@BeforeEach
	void setUp() {
		eventDocuments.deleteAll();
		users.deleteAll();
		for (int i = 1; i <= 2; i++) {
			UserDocument host = new UserDocument();
			host.setId("host-" + i);
			host.setName("Host");
			host.setEmail(i == 1 ? "host@example.com" : "other@example.com");
			host.setPasswordHash("unused-test-hash");
			host.setTimeZone("Europe/Madrid");
			users.save(host);
		}
		fixedTime(NOW);
	}

	@Test
	void draftAcceptsANewDeadlineAndNullRemovesIt() throws Exception {
		events.save(event(EventStatus.DRAFT, null));
		mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(DEADLINE)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.responseDeadline").value("2027-01-01T17:30:00"));
		mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.responseDeadline").isEmpty());
		Event stored = events.findById("event-id").orElseThrow();
		assertThat(stored.getResponseDeadline()).isNull();
		assertThat(stored.getVersion()).isEqualTo(2L);
	}

	@Test
	void publishedAcceptsBeforeTheStartWithTheEventsTimeZone() throws Exception {
		Event event = events.save(event(EventStatus.PUBLISHED, null));
		fixedTime(event.startsAtInstant().minusSeconds(1));
		mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(DEADLINE)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.responseDeadline").value("2027-01-01T17:30:00"));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	void publishedRejectsAtTheExactStartAndAfterIt(int seconds) throws Exception {
		Event event = events.save(event(EventStatus.PUBLISHED, null));
		fixedTime(event.startsAtInstant().plusSeconds(seconds));
		mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
		assertThat(events.findById("event-id").orElseThrow().getVersion()).isZero();
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED"})
	void closedStatesRejectChanges(EventStatus state) throws Exception {
		events.save(event(state, null));
		mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null)))
				.andExpect(status().isConflict());
	}

	@Test
	void aPastDeadlineIsAcceptedForAPublishedEventBeforeItsStart() throws Exception {
		events.save(event(EventStatus.PUBLISHED, null));
		LocalDateTime past = LocalDateTime.of(2026, 12, 31, 12, 0);
		mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(past)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.responseDeadline").value("2026-12-31T12:00:00"));
	}

	@Test
	void publishedAllowsRemovingTheDeadlineBeforeTheStart() throws Exception {
		events.save(event(EventStatus.PUBLISHED, null));
		mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null)))
				.andExpect(status().isOk());
		assertThat(events.findById("event-id").orElseThrow().getResponseDeadline()).isNull();
	}

	@Test
	void deadlineAfterStartIs422InBothTheDedicatedEndpointAndDraftEdit() throws Exception {
		events.save(event(EventStatus.DRAFT, null));
		mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(body(LocalDateTime.of(2027, 1, 1, 18, 0, 1))))
				.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.status").value(422));
		mockMvc.perform(put("/api/events/event-id").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
				{"title":"Event", "startsAt":"2027-01-01T18:00:00", "endsAt":"2027-01-01T20:00:00",
				 "timeZone":"Europe/Madrid", "modality":"IN_PERSON", "responseDeadline":"2027-01-01T18:00:01"}
				"""))
				.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.status").value(422));
	}

	@Test
	@WithUserDetails(value = "other@example.com", setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void anotherHostsEventReturns404() throws Exception {
		events.save(event(EventStatus.PUBLISHED, null));
		mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(DEADLINE)))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithAnonymousUser
	void requiresAuthentication() throws Exception {
		mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(DEADLINE)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void requiresCsrf() throws Exception {
		mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(body(DEADLINE)))
				.andExpect(status().isForbidden());
	}

	@Test
	void concurrentGuestOpeningDoesNotBlockTheDeadlineChangeOrLoseEitherWrite() throws Exception {
		Guest first = guest(101, InvitationStatus.NOT_SENT, null);
		events.save(event(EventStatus.PUBLISHED, null, first));
		CyclicBarrier barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var a = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS);
				return changeDeadline.changeResponseDeadline(new ChangeResponseDeadlineCommand("host-1", "event-id", DEADLINE)); });
			var b = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS);
				return guests.markGuestOpened("event-id", first.getToken(), NOW); });
			assertThat(a.get(15, TimeUnit.SECONDS).getResponseDeadline()).isEqualTo(DEADLINE);
			assertThat(b.get(15, TimeUnit.SECONDS)).isPresent();
		}
		Event stored = events.findById("event-id").orElseThrow();
		assertThat(stored.getResponseDeadline()).isEqualTo(DEADLINE);
		assertThat(stored.getGuest(first.getId()).getOpenedAt()).isEqualTo(NOW);
		assertThat(stored.getVersion()).isEqualTo(2L);
	}

	@Test
	void staleHostCancellationReturns409AndCannotOverwriteTheUpdatedDeadline() throws Exception {
		Event stale = events.save(event(EventStatus.PUBLISHED, null));
		mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(DEADLINE)))
				.andExpect(status().isOk());
		doReturn(Optional.of(stale)).when(events).findById("event-id");
		mockMvc.perform(post("/api/events/event-id/cancel").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Concurrent cancellation\"}"))
				.andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value("Event changed concurrently; reload and retry"));
		var stored = eventDocuments.findById("event-id").orElseThrow();
		assertThat(stored.getResponseDeadline()).isEqualTo(DEADLINE);
		assertThat(stored.getStatus()).isEqualTo("PUBLISHED");
		assertThat(stored.getVersion()).isEqualTo(1L);
	}

	@Test
	void aCancellationBetweenValidationAndWriteIsA409() throws Exception {
		Event stale = events.save(event(EventStatus.PUBLISHED, null));
		Event cancelled = events.findById("event-id").orElseThrow();
		cancelled.cancel("Cancelled", NOW);
		events.save(cancelled);
		doReturn(Optional.of(stale)).when(events).findById("event-id");
		mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(DEADLINE)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
		assertThat(eventDocuments.findById("event-id").orElseThrow().getStatus()).isEqualTo("CANCELLED");
	}

	@Test
	void partialWriteAlsoRequiresTheCorrectHost() {
		events.save(event(EventStatus.PUBLISHED, null));
		assertThat(deadlines.updateDeadline("event-id", "another-host", DEADLINE)).isEmpty();
		assertThat(events.findById("event-id").orElseThrow().getVersion()).isZero();
	}

	@Test
	void documentsTheDeadlineEndpoint() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/events/{id}/response-deadline'].put.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/api/events/{id}/response-deadline'].put.responses['409']").exists());
	}

	private void fixedTime(Instant now) {
		when(clock.instant()).thenReturn(Clock.fixed(now, ZoneOffset.UTC).instant());
	}

	private String body(LocalDateTime deadline) {
		return "{\"responseDeadline\":" + (deadline == null ? "null" : "\"" + deadline + "\"") + "}";
	}
}
