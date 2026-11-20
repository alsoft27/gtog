package com.gtog.event.infrastructure.in.web;

import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.event.infrastructure.out.persistence.GuestResponseDocument;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.gtog.user.infrastructure.out.persistence.UserMongoRepository;
import static com.gtog.event.AttendanceTestFixtures.event;
import static com.gtog.event.AttendanceTestFixtures.response;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.gtog.AuthBypassDisabled
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@WithUserDetails(value = "host@example.com", setupBefore = TestExecutionEvent.TEST_EXECUTION)
class EventDashboardControllerIntegrationTest {
	private static final String PATH = "/api/events/event-id/dashboard";
	private final MockMvc mvc;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final GuestResponseRepositoryPort responses;
	private final UserMongoRepository users;
	private Event event;

	EventDashboardControllerIntegrationTest(MockMvc mvc, MongoTemplate mongo, EventRepositoryPort events,
			GuestResponseRepositoryPort responses, UserMongoRepository users) {
		this.mvc = mvc;
		this.mongo = mongo;
		this.events = events;
		this.responses = responses;
		this.users = users;
	}

	@BeforeEach
	void setUp() {
		mongo.remove(new Query(), EventDocument.class);
		mongo.remove(new Query(), GuestResponseDocument.class);
		users.deleteAll();
		for (int i = 1; i <= 2; i++) {
			UserDocument user = new UserDocument();
			user.setId("host-" + i);
			user.setName("Host");
			user.setEmail(i == 1 ? "host@example.com" : "other@example.com");
			user.setPasswordHash("unused-test-hash");
			user.setTimeZone("Europe/Madrid");
		user.setEmailVerified(true);
			users.save(user);
		}
		event = events.save(event(EventStatus.PUBLISHED));
	}

	@Test
	void returnsAllTotalsOrderedOptionsAndOrderedGuestRows() throws Exception {
		seedResponse(1, "yes-id");
		seedResponse(2, "no-id");
		mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.totals.guests").value(3)).andExpect(jsonPath("$.totals.answered").value(2))
				.andExpect(jsonPath("$.totals.pending").value(1)).andExpect(jsonPath("$.totals.attending").value(1))
				.andExpect(jsonPath("$.byOption[0].optionId").value("yes-id"))
				.andExpect(jsonPath("$.byOption[0].label").value("Asisto"))
				.andExpect(jsonPath("$.byOption[0].countsAsAttendance").value(true))
				.andExpect(jsonPath("$.byOption[0].count").value(1))
				.andExpect(jsonPath("$.byOption[1].optionId").value("no-id"))
				.andExpect(jsonPath("$.byOption[1].count").value(1))
				.andExpect(jsonPath("$.byOption[1].countsAsAttendance").value(false))
				.andExpect(jsonPath("$.guests[0].id").value("guest-1"))
				.andExpect(jsonPath("$.guests[0].answered").value(false))
				.andExpect(jsonPath("$.guests[0].response").value(nullValue()))
				.andExpect(jsonPath("$.guests[0].optionKnown").value(nullValue()))
				.andExpect(jsonPath("$.guests[1].id").value("guest-2"))
				.andExpect(jsonPath("$.guests[1].name").value("Guest 2"))
				.andExpect(jsonPath("$.guests[1].email").value("contact-2@example.com"))
				.andExpect(jsonPath("$.guests[1].phoneE164").value(event.getGuests().get(1).getPhoneE164()))
				.andExpect(jsonPath("$.guests[1].deliveryChannel").value("EMAIL"))
				.andExpect(jsonPath("$.guests[1].invitationStatus").value("SENT"))
				.andExpect(jsonPath("$.guests[1].answered").value(true))
				.andExpect(jsonPath("$.guests[1].optionKnown").value(true))
				.andExpect(jsonPath("$.guests[1].response.responseOptionId").value("yes-id"))
				.andExpect(jsonPath("$.guests[1].response.label").value("Asisto"))
				.andExpect(jsonPath("$.guests[1].response.comment").value("Response comment 1"))
				.andExpect(jsonPath("$.guests[1].response.answeredAt").value(NOW.toString()))
				.andExpect(jsonPath("$.guests[1].response.updatedAt").value(NOW.plusSeconds(1).toString()))
				.andExpect(jsonPath("$.guests[2].id").value("guest-3"))
				.andExpect(jsonPath("$.guests[2].response.responseOptionId").value("no-id"));
	}

	@Test
	void aDirectlySeededOrphanDoesNotChangeAnyCounterOrAddARow() throws Exception {
		seedResponse(1, "yes-id");
		seedResponse(2, "no-id");
		String before = body();
		mongo.insert(new GuestResponseDocument(event.getId(), "orphan-secret", "yes-id", "orphan comment", "orphan@example.com", NOW, NOW));
		assertThat(body()).isEqualTo(before).doesNotContain("orphan-secret", "orphan comment", "orphan@example.com");
	}

	@Test
	void anUnknownOptionCountsAsAnsweredWithFalseOptionKnownAndNullLabel() throws Exception {
		seedResponse(0, "removed-option");
		mvc.perform(get(PATH)).andExpect(status().isOk())
				.andExpect(jsonPath("$.totals.answered").value(1)).andExpect(jsonPath("$.totals.pending").value(2))
				.andExpect(jsonPath("$.totals.attending").value(0))
				.andExpect(jsonPath("$.byOption[0].count").value(0)).andExpect(jsonPath("$.byOption[1].count").value(0))
				.andExpect(jsonPath("$.guests[0].answered").value(true)).andExpect(jsonPath("$.guests[0].optionKnown").value(false))
				.andExpect(jsonPath("$.guests[0].response.responseOptionId").value("removed-option"))
				.andExpect(jsonPath("$.guests[0].response.label").value(nullValue()));
	}

	@Test
	void onlyExposesEachOwnTokenOnceInsideInvitationPathAndNeverAnotherEventsToken() throws Exception {
		seedResponse(1, "yes-id");
		seedResponse(2, "no-id");
		var other = com.gtog.event.GuestTestFixtures.guest(999);
		events.save(com.gtog.event.GuestTestFixtures.event("other-event", EventStatus.PUBLISHED, null, other));
		String body = body();
		assertThat(Pattern.compile("\"(?:guestToken|token)\"\\s*:").matcher(body).find()).isFalse();
		for (var guest : event.getGuests()) {
			assertThat(body.split(Pattern.quote(guest.getToken()), -1).length - 1).isEqualTo(1);
			assertThat(body).contains("\"invitationPath\":\"/r/" + guest.getToken() + "\"");
		}
		assertThat(body).doesNotContain(other.getToken());
	}

	@Test
	void neverExposesTheEmailProvidedInAnyGuestResponseInTheFullBody() throws Exception {
		seedResponse(1, "yes-id");
		seedResponse(2, "no-id");
		assertThat(body()).contains("contact-2@example.com", "contact-3@example.com")
				.doesNotContain("private-response-1@example.com", "private-response-2@example.com");
	}

	@Test
	@WithUserDetails(value = "other@example.com", setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void anotherHostsDashboardReturns404() throws Exception {
		mvc.perform(get(PATH)).andExpect(status().isNotFound());
	}

	@Test
	void anUnknownEventReturns404() throws Exception {
		mvc.perform(get("/api/events/missing/dashboard")).andExpect(status().isNotFound());
	}

	@Test
	@WithAnonymousUser
	void requiresAuthentication() throws Exception {
		mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@EnumSource(EventStatus.class)
	void neverChangesEventVersionGuestStateOrResponseDocumentsInAnyEventState(EventStatus state) throws Exception {
		mongo.remove(new Query(), EventDocument.class);
		event = events.save(event(state));
		seedResponse(1, "yes-id");
		var beforeEvent = mongo.getCollection("events").find().first();
		var beforeResponse = mongo.getCollection("responses").find().first();
		body();
		assertThat(mongo.getCollection("events").find().first()).isEqualTo(beforeEvent);
		assertThat(mongo.getCollection("responses").find().first()).isEqualTo(beforeResponse);
	}

	@Test
	void batchResponseReadIncludesOnlyRequestedEventsAndSupportsAnEmptySet() {
		seedResponse(1, "yes-id");
		mongo.insert(new GuestResponseDocument("other-event", "other-secret", "other-option", null, null, NOW, NOW));
		mongo.insert(new GuestResponseDocument("excluded-event", "excluded-secret", "excluded-option", null, null, NOW, NOW));
		assertThat(responses.findByEventIds(Set.of("event-id", "other-event")))
				.extracting(com.gtog.event.domain.model.GuestResponse::eventId).containsExactlyInAnyOrder("event-id", "other-event");
		assertThat(responses.findByEventIds(Set.of())).isEmpty();
	}

	@Test
	void documentsTheDashboardContract() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/events/{id}/dashboard'].get.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/api/events/{id}/dashboard'].get.responses['404']").exists());
	}

	private void seedResponse(int guestIndex, String option) {
		var response = response(event, guestIndex, option);
		mongo.insert(new GuestResponseDocument(response.eventId(), response.guestToken(), response.responseOptionId(),
				response.comment(), response.email(), response.answeredAt(), response.updatedAt()));
	}
	private String body() throws Exception {
		return mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andReturn().getResponse().getContentAsString();
	}
}
