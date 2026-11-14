package com.gtog.event.infrastructure.in.web;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.EventConcurrentModificationException;
import com.gtog.event.domain.model.GuestResponseStorageException;
import com.gtog.event.domain.model.LinkVisibility;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.EventGuestRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseCleanupPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.EventMongoRepository;
import com.gtog.event.infrastructure.out.persistence.GuestResponseDocument;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.gtog.user.infrastructure.out.persistence.UserMongoRepository;
import static com.gtog.event.ResponseTestFixtures.responseEvent;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static com.gtog.event.InvitationTestFixtures.URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithUserDetails(value = "host@example.com", setupBefore = TestExecutionEvent.TEST_EXECUTION)
class ResponseOptionsAndCascadeIntegrationTest {
	private static final String OPTIONS = "/api/events/event-id/response-options";
	private static final String DELETE = "/api/events/event-id/guests/guest-101";
	private static final String KEEP = """
			{"responseOptions":[{"id":"yes-id","label":"Asisto","countsAsAttendance":true},
			{"id":"no-id","label":"No asisto","countsAsAttendance":false}]}
			""";
	private static final String REMOVE_YES = """
			{"responseOptions":[{"id":"no-id","label":"No asisto","countsAsAttendance":false},
			{"label":"Nuevo si","countsAsAttendance":true}]}
			""";
	private static final String FLIP = """
			{"responseOptions":[{"id":"yes-id","label":"Ahora no","countsAsAttendance":false},
			{"id":"no-id","label":"No asisto","countsAsAttendance":false}]}
			""";
	@Autowired private MockMvc mvc;
	@Autowired private MongoTemplate mongo;
	@Autowired private EventRepositoryPort events;
	@Autowired private GuestResponseRepositoryPort responses;
	@Autowired private EventMongoRepository eventDocuments;
	@Autowired private UserMongoRepository users;
	@MockitoSpyBean private GuestResponseCleanupPort cleanup;
	@MockitoSpyBean private EventGuestRepositoryPort guests;
	@MockitoBean private Clock clock;
	private String token;
	private String otherToken;

	@BeforeEach
	void setUp() {
		eventDocuments.deleteAll();
		mongo.remove(new Query(), GuestResponseDocument.class);
		users.deleteAll();
		for (int i = 1; i <= 2; i++) {
			UserDocument user = new UserDocument();
			user.setId("host-" + i);
			user.setName("Host");
			user.setEmail(i == 1 ? "host@example.com" : "other@example.com");
			user.setPasswordHash("unused-test-hash");
			user.setTimeZone("Europe/Madrid");
			users.save(user);
		}
		when(clock.instant()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC).instant());
		seed(EventStatus.PUBLISHED);
	}

	@Test
	void renamesAndAddsWhilePreservingExistingResponsesAndConfirmation() throws Exception {
		answer();
		mvc.perform(put(OPTIONS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
				{"responseOptions":[{"id":"yes-id","label":"Confirmo","countsAsAttendance":true},
				{"id":"no-id","label":"Declino","countsAsAttendance":false},
				{"label":"Tal vez","countsAsAttendance":false}]}
				"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.responseOptions.length()").value(3))
				.andExpect(jsonPath("$.responseOptions[0].id").value("yes-id"));
		assertThat(responses.find("event-id", token).orElseThrow().responseOptionId()).isEqualTo("yes-id");
		mvc.perform(get(invitation())).andExpect(status().isOk())
				.andExpect(jsonPath("$.responseOptions[0].label").value("Confirmo"))
				.andExpect(jsonPath("$.onlineAccess.url").value(URL));
	}

	@Test
	void cannotDeleteAnAnsweredOptionOnAPublishedEvent() throws Exception {
		answer();
		mvc.perform(put(OPTIONS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REMOVE_YES))
				.andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Cannot delete a response option with responses on a published event"));
		assertThat(events.findById("event-id").orElseThrow().getResponseOptions()).extracting(option -> option.id())
				.containsExactly("yes-id", "no-id");
	}

	@Test
	void cannotChangeAttendanceOfAnAnsweredOptionOnAPublishedEvent() throws Exception {
		answer();
		mvc.perform(put(OPTIONS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(FLIP))
				.andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Cannot change attendance of a response option with responses on a published event"));
		assertThat(events.findById("event-id").orElseThrow().getResponseOptions().getFirst().countsAsAttendance()).isTrue();
	}

	@Test
	void mayDeleteAnUnansweredOptionOnAPublishedEvent() throws Exception {
		answer();
		mvc.perform(put(OPTIONS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
				{"responseOptions":[{"id":"yes-id","label":"Asisto","countsAsAttendance":true},
				{"label":"Tal vez","countsAsAttendance":false}]}
				"""))
				.andExpect(status().isOk());
		assertThat(events.findById("event-id").orElseThrow().getResponseOptions()).extracting(option -> option.id())
				.doesNotContain("no-id");
	}

	@Test
	void mayChangeAttendanceOfAnUnansweredOptionOnAPublishedEvent() throws Exception {
		answer();
		mvc.perform(put(OPTIONS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
				{"responseOptions":[{"id":"yes-id","label":"Asisto","countsAsAttendance":true},
				{"id":"no-id","label":"Tambien asisto","countsAsAttendance":true}]}
				"""))
				.andExpect(status().isOk());
	}

	@Test
	void draftDoesNotRestrictOptionsEvenWithStoredResponses() throws Exception {
		seed(EventStatus.DRAFT);
		insertResponse("event-id", token, "yes-id");
		mvc.perform(put(OPTIONS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REMOVE_YES))
				.andExpect(status().isOk());
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED"})
	void closedEventsRejectOptionChanges(EventStatus state) throws Exception {
		seed(state);
		mvc.perform(put(OPTIONS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KEEP))
				.andExpect(status().isConflict());
	}

	@Test
	@WithUserDetails(value = "other@example.com", setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void anotherHostsOptionsAre404() throws Exception {
		mvc.perform(put(OPTIONS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KEEP))
				.andExpect(status().isNotFound());
	}

	@Test
	void answeredOptionIdsAreDistinctAndScopedToTheEvent() {
		insertResponse("event-id", token, "yes-id");
		insertResponse("event-id", otherToken, "yes-id");
		insertResponse("another-event", token, "foreign-option");
		assertThat(responses.findAnsweredOptionIds("event-id")).isEqualTo(Set.of("yes-id"));
	}

	@Test
	void deletingAnAnsweredGuestRequiresConfirmationAndPreservesBothDocuments() throws Exception {
		answer();
		mvc.perform(delete(DELETE).with(csrf())).andExpect(status().isConflict());
		assertThat(events.findById("event-id").orElseThrow().getGuest("guest-101")).isNotNull();
		assertThat(responses.find("event-id", token)).isPresent();
		verify(cleanup, never()).deleteResponse("event-id", token);
	}

	@Test
	void confirmedDeletionRemovesTheGuestBeforeTheirResponseAndPreservesTheOtherResponse() throws Exception {
		answer();
		insertResponse("event-id", otherToken, "no-id");
		doAnswer(invocation -> {
			assertThat(events.findById("event-id").orElseThrow().getGuests()).noneMatch(g -> g.getToken().equals(token));
			assertThat(responses.find("event-id", token)).isPresent();
			return invocation.callRealMethod();
		}).when(cleanup).deleteResponse("event-id", token);
		mvc.perform(delete(DELETE).with(csrf()).param("confirmDeleteResponse", "true")).andExpect(status().isNoContent());
		assertThat(responses.find("event-id", token)).isEmpty();
		assertThat(responses.find("event-id", otherToken)).isPresent();
		mvc.perform(get(invitation())).andExpect(status().isNotFound())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.instance").value("/api/invitations"));
	}

	@Test
	void aCleanupFailureLeavesAnUnreachableResponseAndNoLiveGuest() throws Exception {
		answer();
		doThrow(new GuestResponseStorageException()).when(cleanup).deleteResponse("event-id", token);
		mvc.perform(delete(DELETE).with(csrf()).param("confirmDeleteResponse", "true"))
				.andExpect(status().isInternalServerError()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		assertThat(events.findById("event-id").orElseThrow().getGuests()).noneMatch(g -> g.getToken().equals(token));
		assertThat(responses.find("event-id", token)).isPresent();
		mvc.perform(get(invitation())).andExpect(status().isNotFound());
	}

	@Test
	void aGuestRemovalConflictNeverDeletesTheLiveGuestsResponse() throws Exception {
		answer();
		doThrow(new EventConcurrentModificationException()).when(guests).removeGuest("event-id", "host-1", 0L, "guest-101");
		mvc.perform(delete(DELETE).with(csrf()).param("confirmDeleteResponse", "true")).andExpect(status().isConflict());
		assertThat(events.findById("event-id").orElseThrow().getGuest("guest-101")).isNotNull();
		assertThat(responses.find("event-id", token)).isPresent();
		verify(cleanup, never()).deleteResponse(anyString(), anyString());
	}

	@Test
	void cleansAResponseThatArrivedAfterTheInitialExistenceCheck() throws Exception {
		doAnswer(invocation -> {
			insertResponse("event-id", token, "yes-id");
			return false;
		}).when(cleanup).hasResponse("event-id", token);
		mvc.perform(delete(DELETE).with(csrf())).andExpect(status().isNoContent());
		assertThat(responses.find("event-id", token)).isEmpty();
		assertThat(events.findById("event-id").orElseThrow().getGuests()).noneMatch(g -> g.getToken().equals(token));
	}

	@Test
	void anUnansweredGuestCanBeDeletedWithoutConfirmation() throws Exception {
		mvc.perform(delete(DELETE).with(csrf())).andExpect(status().isNoContent());
		assertThat(events.findById("event-id").orElseThrow().getGuests()).noneMatch(g -> g.getToken().equals(token));
	}

	private void seed(EventStatus state) {
		eventDocuments.deleteAll();
		var event = events.save(responseEvent(state, true, true, null, LinkVisibility.ON_CONFIRMATION));
		token = event.getGuests().getFirst().getToken();
		otherToken = event.getGuests().get(1).getToken();
	}
	private void answer() throws Exception {
		mvc.perform(put(invitation() + "/response").contentType(MediaType.APPLICATION_JSON)
				.content("{\"responseOptionId\":\"yes-id\"}")).andExpect(status().isOk());
	}
	private void insertResponse(String eventId, String guestToken, String optionId) {
		responses.insert(new com.gtog.event.domain.model.GuestResponse(eventId, guestToken, optionId, null, null, NOW, NOW));
	}
	private String invitation() { return "/api/invitations/" + token; }
}
