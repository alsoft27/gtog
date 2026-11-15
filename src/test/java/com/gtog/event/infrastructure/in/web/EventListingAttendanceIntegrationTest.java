package com.gtog.event.infrastructure.in.web;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.event.infrastructure.out.persistence.GuestResponseDocument;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.gtog.user.infrastructure.out.persistence.UserMongoRepository;
import com.jayway.jsonpath.JsonPath;

import static com.gtog.event.AttendanceTestFixtures.event;
import static com.gtog.event.AttendanceTestFixtures.response;
import static com.gtog.event.GuestTestFixtures.guest;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@WithUserDetails(value = "host@example.com", setupBefore = TestExecutionEvent.TEST_EXECUTION)
class EventListingAttendanceIntegrationTest {
	private static final String SECURITY_CACHE_CONTROL = "no-cache, no-store, max-age=0, must-revalidate";
	private final MockMvc mvc;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final UserMongoRepository users;
	@MockitoSpyBean private GuestResponseRepositoryPort responses;
	private Event event;

	EventListingAttendanceIntegrationTest(MockMvc mvc, MongoTemplate mongo, EventRepositoryPort events,
			UserMongoRepository users) {
		this.mvc = mvc;
		this.mongo = mongo;
		this.events = events;
		this.users = users;
	}

	@BeforeEach
	void setUp() {
		mongo.remove(new Query(), EventDocument.class);
		mongo.remove(new Query(), GuestResponseDocument.class);
		users.deleteAll();
		UserDocument user = new UserDocument();
		user.setId("host-1");
		user.setName("Host");
		user.setEmail("host@example.com");
		user.setPasswordHash("unused-test-hash");
		user.setTimeZone("Europe/Madrid");
		users.save(user);
		event = events.save(event(EventStatus.PUBLISHED));
	}

	@Test
	void listingTotalsMatchTheDashboardAndIgnoreADirectlySeededOrphan() throws Exception {
		seedResponse(event, 1, "yes-id");
		seedResponse(event, 2, "no-id");
		mongo.insert(new GuestResponseDocument(event.getId(), "orphan-token", "yes-id", null, null, NOW, NOW));
		String dashboard = mvc.perform(get("/api/events/" + event.getId() + "/dashboard"))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andReturn().getResponse().getContentAsString();
		Map<String, Integer> totals = JsonPath.read(dashboard, "$.totals");
		assertThat(totals).containsExactlyInAnyOrderEntriesOf(Map.of("guests", 3, "answered", 2, "pending", 1, "attending", 1));

		mvc.perform(get("/api/events")).andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", SECURITY_CACHE_CONTROL))
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].length()").value(9))
				.andExpect(jsonPath("$[0].id").value(event.getId()))
				.andExpect(jsonPath("$[0].title").value(event.getTitle()))
				.andExpect(jsonPath("$[0].startsAt").exists()).andExpect(jsonPath("$[0].modality").value("IN_PERSON"))
				.andExpect(jsonPath("$[0].status").value("PUBLISHED"))
				.andExpect(jsonPath("$[0].totalGuests").value(totals.get("guests")))
				.andExpect(jsonPath("$[0].answered").value(totals.get("answered")))
				.andExpect(jsonPath("$[0].pending").value(totals.get("pending")))
				.andExpect(jsonPath("$[0].attending").value(totals.get("attending")))
				.andExpect(jsonPath("$[0].byOption").doesNotExist());
	}

	@Test
	void readsResponsesOnceForAllOfTheAuthenticatedHostsEvents() throws Exception {
		seedResponse(event, 1, "yes-id");
		seedResponse(event, 2, "no-id");
		var second = events.save(com.gtog.event.GuestTestFixtures.event("second-event", EventStatus.DRAFT, null, guest(10), guest(11)));
		seedResponse(second, 0, second.getResponseOptions().getFirst().id());
		var foreign = events.save(com.gtog.event.GuestTestFixtures.event("foreign-event", EventStatus.PUBLISHED, null, guest(99)));
		mongo.updateFirst(Query.query(Criteria.where("_id").is(foreign.getId())), new Update().set("hostId", "host-2"), EventDocument.class);
		seedResponse(foreign, 0, foreign.getResponseOptions().getFirst().id());

		mvc.perform(get("/api/events")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[?(@.id == 'event-id')].totalGuests").value(contains(3)))
				.andExpect(jsonPath("$[?(@.id == 'event-id')].answered").value(contains(2)))
				.andExpect(jsonPath("$[?(@.id == 'event-id')].pending").value(contains(1)))
				.andExpect(jsonPath("$[?(@.id == 'event-id')].attending").value(contains(1)))
				.andExpect(jsonPath("$[?(@.id == 'second-event')].totalGuests").value(contains(2)))
				.andExpect(jsonPath("$[?(@.id == 'second-event')].answered").value(contains(1)))
				.andExpect(jsonPath("$[?(@.id == 'second-event')].pending").value(contains(1)))
				.andExpect(jsonPath("$[?(@.id == 'second-event')].attending").value(contains(1)));
		verify(responses).findByEventIds(Set.of(event.getId(), second.getId()));
		verifyNoMoreInteractions(responses);
	}

	@Test
	void anEmptyListingDoesNotReadResponses() throws Exception {
		mongo.remove(new Query(), EventDocument.class);
		mvc.perform(get("/api/events")).andExpect(status().isOk()).andExpect(content().json("[]"));
		verifyNoInteractions(responses);
	}

	@Test
	void anUnknownOptionCountsAsAnsweredButNeverAsAttending() throws Exception {
		seedResponse(event, 0, "removed-option");
		mvc.perform(get("/api/events")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].totalGuests").value(3)).andExpect(jsonPath("$[0].answered").value(1))
				.andExpect(jsonPath("$[0].pending").value(2)).andExpect(jsonPath("$[0].attending").value(0));
	}

	@ParameterizedTest
	@EnumSource(EventStatus.class)
	void listingNeverChangesEventVersionGuestsOrResponseDocumentsInAnyState(EventStatus state) throws Exception {
		mongo.remove(new Query(), EventDocument.class);
		event = events.save(event(state));
		seedResponse(event, 1, "yes-id");
		var beforeEvent = mongo.getCollection("events").find().first();
		var beforeResponse = mongo.getCollection("responses").find().first();
		mvc.perform(get("/api/events")).andExpect(status().isOk()).andExpect(jsonPath("$[0].answered").value(1));
		assertThat(mongo.getCollection("events").find().first()).isEqualTo(beforeEvent);
		assertThat(mongo.getCollection("responses").find().first()).isEqualTo(beforeResponse);
	}

	@Test
	void documentsTheFourListingCountersWithoutOptionBreakdown() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.components.schemas.EventSummaryResponse.properties.totalGuests.type").value("integer"))
				.andExpect(jsonPath("$.components.schemas.EventSummaryResponse.properties.answered.type").value("integer"))
				.andExpect(jsonPath("$.components.schemas.EventSummaryResponse.properties.pending.type").value("integer"))
				.andExpect(jsonPath("$.components.schemas.EventSummaryResponse.properties.attending.type").value("integer"))
				.andExpect(jsonPath("$.components.schemas.EventSummaryResponse.properties.byOption").doesNotExist());
	}

	private void seedResponse(Event source, int guestIndex, String optionId) {
		var response = response(source, guestIndex, optionId);
		mongo.insert(new GuestResponseDocument(response.eventId(), response.guestToken(), response.responseOptionId(),
				response.comment(), response.email(), response.answeredAt(), response.updatedAt()));
	}
}
