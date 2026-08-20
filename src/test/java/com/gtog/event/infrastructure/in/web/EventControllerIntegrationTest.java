package com.gtog.event.infrastructure.in.web;

import java.time.LocalDateTime;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.event.infrastructure.out.persistence.EventMongoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventControllerIntegrationTest {

	private static final String VENUE_JSON = """
			{
			  "placeName": "Sala Apolo",
			  "address": "Carrer Nou de la Rambla, 113, Barcelona",
			  "latitude": 41.3767,
			  "longitude": 2.1662,
			  "placeId": "ChIJT7Xj1uOipBIRdKY0X_0V7Xk"
			}""";

	private static final String ONLINE_ACCESS_JSON = """
			{
			  "platform": "Zoom",
			  "url": "https://zoom.us/j/123456789",
			  "linkVisibility": "ALWAYS"
			}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EventMongoRepository eventMongoRepository;

	@BeforeEach
	void cleanDatabase() {
		eventMongoRepository.deleteAll();
	}

	@Test
	void createsAnEventAndReturns201WithLocationAndDraftStatus() throws Exception {
		String requestBody = """
				{
				  "hostId": "host-1",
				  "title": "Cumpleaños",
				  "description": "Fiesta de cumpleaños",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON",
				  "venue": %s
				}
				""".formatted(VENUE_JSON);

		mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.hostId").value("host-1"));

		List<EventDocument> stored = eventMongoRepository.findAll();
		assertThat(stored).hasSize(1);
		EventDocument document = stored.get(0);
		assertThat(document.getHostId()).isEqualTo("host-1");
		assertThat(document.getTitle()).isEqualTo("Cumpleaños");
		assertThat(document.getStartsAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 20, 0));
		assertThat(document.getEndsAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 23, 0));
		assertThat(document.getTimeZone()).isEqualTo("Europe/Madrid");
		assertThat(document.getModality()).isEqualTo("IN_PERSON");
		assertThat(document.getStatus()).isEqualTo("DRAFT");
		assertThat(document.getVersion()).isEqualTo(0L);
	}

	@Test
	void returns422WithProblemDetailWhenEndsAtIsNotAfterStartsAt() throws Exception {
		String requestBody = """
				{
				  "hostId": "host-1",
				  "title": "Cumpleaños",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T20:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON"
				}
				""";

		mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").exists());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	void returns422WithProblemDetailWhenTimeZoneIsInvalid() throws Exception {
		String requestBody = """
				{
				  "hostId": "host-1",
				  "title": "Cumpleaños",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Not/AZone",
				  "modality": "IN_PERSON"
				}
				""";

		mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").exists());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	void returns422WithProblemDetailWhenTitleIsBlank() throws Exception {
		String requestBody = """
				{
				  "hostId": "host-1",
				  "title": "   ",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON"
				}
				""";

		mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").exists());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	void returns400WhenARequiredFieldIsMissing() throws Exception {
		String requestBody = """
				{
				  "title": "Cumpleaños",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON"
				}
				""";

		mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isBadRequest());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	void returnsAnEventByIdWithFullDetail() throws Exception {
		String location = createEvent("host-1", "Cumpleaños");

		mockMvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hostId").value("host-1"))
				.andExpect(jsonPath("$.title").value("Cumpleaños"))
				.andExpect(jsonPath("$.status").value("DRAFT"));
	}

	@Test
	void returns404WithProblemDetailWhenTheEventDoesNotExist() throws Exception {
		mockMvc.perform(get("/api/events/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").exists());
	}

	@Test
	void listsEventSummariesForTheGivenHost() throws Exception {
		createEvent("host-1", "Cumpleaños");
		createEvent("host-1", "Boda");
		createEvent("host-2", "Otro evento");

		mockMvc.perform(get("/api/events").param("hostId", "host-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].title").exists())
				.andExpect(jsonPath("$[0].hostId").doesNotExist());
	}

	@Test
	void returnsEmptyListWhenTheHostHasNoEvents() throws Exception {
		mockMvc.perform(get("/api/events").param("hostId", "host-without-events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void returns400WhenHostIdIsMissingFromTheListing() throws Exception {
		mockMvc.perform(get("/api/events"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createsAnEventWithDefaultResponseOptionsWhenNoneAreProvided() throws Exception {
		String location = createEvent("host-1", "Cumpleaños");

		mockMvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.responseOptions.length()").value(2))
				.andExpect(jsonPath("$.responseOptions[0].label").value("Asisto"))
				.andExpect(jsonPath("$.responseOptions[0].countsAsAttendance").value(true))
				.andExpect(jsonPath("$.responseOptions[1].label").value("No asisto"))
				.andExpect(jsonPath("$.responseOptions[1].countsAsAttendance").value(false))
				.andExpect(jsonPath("$.allowComment").value(false))
				.andExpect(jsonPath("$.allowResponseChange").value(true));
	}

	@Test
	void createsAnEventWithCustomResponseOptions() throws Exception {
		String requestBody = """
				{
				  "hostId": "host-1",
				  "title": "Cumpleaños",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON",
				  "responseOptions": [
				    { "label": "Voy", "countsAsAttendance": true },
				    { "label": "No voy", "countsAsAttendance": false },
				    { "label": "Quizas", "countsAsAttendance": false }
				  ],
				  "allowComment": true,
				  "venue": %s
				}
				""".formatted(VENUE_JSON);

		String location = mockMvc
				.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.responseOptions.length()").value(3))
				.andExpect(jsonPath("$.allowComment").value(true))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(get(location))
				.andExpect(jsonPath("$.responseOptions[0].label").value("Voy"))
				.andExpect(jsonPath("$.responseOptions[2].label").value("Quizas"));
	}

	@Test
	void returns422WhenResponseDeadlineIsAfterStartsAt() throws Exception {
		String requestBody = """
				{
				  "hostId": "host-1",
				  "title": "Cumpleaños",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON",
				  "responseDeadline": "2026-09-01T20:30:00"
				}
				""";

		mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	void replaceResponseOptionsReplacesTheListAndReturns200WithTheUpdatedEvent() throws Exception {
		String location = createEvent("host-1", "Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		String requestBody = """
				{
				  "responseOptions": [
				    { "label": "Voy", "countsAsAttendance": true },
				    { "label": "No voy", "countsAsAttendance": false }
				  ],
				  "allowComment": true,
				  "allowResponseChange": false
				}
				""";

		mockMvc.perform(put("/api/events/" + eventId + "/response-options")
				.contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.responseOptions.length()").value(2))
				.andExpect(jsonPath("$.responseOptions[0].label").value("Voy"))
				.andExpect(jsonPath("$.allowComment").value(true))
				.andExpect(jsonPath("$.allowResponseChange").value(false));
	}

	@Test
	void replaceResponseOptionsPreservesTheIdOfAnExistingOptionWhenRenamedById() throws Exception {
		String location = createEvent("host-1", "Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		String eventJson = mockMvc.perform(get(location))
				.andReturn().getResponse().getContentAsString();
		String firstOptionId = JsonPath.read(eventJson, "$.responseOptions[0].id");

		String requestBody = """
				{
				  "responseOptions": [
				    { "id": "%s", "label": "Asisto seguro", "countsAsAttendance": true },
				    { "label": "No asisto", "countsAsAttendance": false }
				  ],
				  "allowComment": false,
				  "allowResponseChange": true
				}
				""".formatted(firstOptionId);

		mockMvc.perform(put("/api/events/" + eventId + "/response-options")
				.contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.responseOptions[0].id").value(firstOptionId))
				.andExpect(jsonPath("$.responseOptions[0].label").value("Asisto seguro"));
	}

	@Test
	void replaceResponseOptionsReturns422WhenAnIdDoesNotBelongToTheEvent() throws Exception {
		String location = createEvent("host-1", "Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		String requestBody = """
				{
				  "responseOptions": [
				    { "id": "does-not-exist", "label": "Asisto", "countsAsAttendance": true },
				    { "label": "No asisto", "countsAsAttendance": false }
				  ],
				  "allowComment": false,
				  "allowResponseChange": true
				}
				""";

		mockMvc.perform(put("/api/events/" + eventId + "/response-options")
				.contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	void replaceResponseOptionsReturns404WhenTheEventDoesNotExist() throws Exception {
		String requestBody = """
				{
				  "responseOptions": [
				    { "label": "Asisto", "countsAsAttendance": true },
				    { "label": "No asisto", "countsAsAttendance": false }
				  ],
				  "allowComment": false,
				  "allowResponseChange": true
				}
				""";

		mockMvc.perform(put("/api/events/does-not-exist/response-options")
				.contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isNotFound());
	}

	@Test
	void returns422WhenInPersonEventHasNoVenue() throws Exception {
		String requestBody = """
				{
				  "hostId": "host-1",
				  "title": "Cumpleaños",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON"
				}
				""";

		mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	void createsAnOnlineEventWithOnlineAccessAndReturns201() throws Exception {
		String location = createOnlineEvent("host-1", "Charla online");

		mockMvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.modality").value("ONLINE"))
				.andExpect(jsonPath("$.onlineAccess.platform").value("Zoom"))
				.andExpect(jsonPath("$.onlineAccess.url").value("https://zoom.us/j/123456789"))
				.andExpect(jsonPath("$.venue").doesNotExist());
	}

	@Test
	void returns422WhenOnlineEventHasNoOnlineAccess() throws Exception {
		String requestBody = """
				{
				  "hostId": "host-1",
				  "title": "Charla online",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "ONLINE"
				}
				""";

		mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	void replaceVenueUpdatesTheVenueAndReturns200() throws Exception {
		String location = createEvent("host-1", "Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		String requestBody = """
				{
				  "placeName": "Otra sala",
				  "address": "Otra direccion",
				  "latitude": 0.0,
				  "longitude": 0.0,
				  "placeId": "other-place-id"
				}
				""";

		mockMvc.perform(put("/api/events/" + eventId + "/venue")
				.contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.venue.placeName").value("Otra sala"));
	}

	@Test
	void replaceVenueReturns404WhenTheEventDoesNotExist() throws Exception {
		mockMvc.perform(put("/api/events/does-not-exist/venue")
				.contentType(MediaType.APPLICATION_JSON).content(VENUE_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	void replaceVenueReturns422WhenTheEventIsOnline() throws Exception {
		String location = createOnlineEvent("host-1", "Charla online");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		mockMvc.perform(put("/api/events/" + eventId + "/venue")
				.contentType(MediaType.APPLICATION_JSON).content(VENUE_JSON))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void replaceOnlineAccessUpdatesItAndReturns200() throws Exception {
		String location = createOnlineEvent("host-1", "Charla online");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		String requestBody = """
				{
				  "platform": "Teams",
				  "url": "https://teams.microsoft.com/x",
				  "linkVisibility": "ALWAYS"
				}
				""";

		mockMvc.perform(put("/api/events/" + eventId + "/online-access")
				.contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.onlineAccess.platform").value("Teams"));
	}

	@Test
	void replaceOnlineAccessReturns404WhenTheEventDoesNotExist() throws Exception {
		mockMvc.perform(put("/api/events/does-not-exist/online-access")
				.contentType(MediaType.APPLICATION_JSON).content(ONLINE_ACCESS_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	void replaceOnlineAccessReturns422WhenTheEventIsInPerson() throws Exception {
		String location = createEvent("host-1", "Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		mockMvc.perform(put("/api/events/" + eventId + "/online-access")
				.contentType(MediaType.APPLICATION_JSON).content(ONLINE_ACCESS_JSON))
				.andExpect(status().isUnprocessableEntity());
	}

	private String createOnlineEvent(String hostId, String title) throws Exception {
		String requestBody = """
				{
				  "hostId": "%s",
				  "title": "%s",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "ONLINE",
				  "onlineAccess": %s
				}
				""".formatted(hostId, title, ONLINE_ACCESS_JSON);

		return mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader("Location");
	}

	private String createEvent(String hostId, String title) throws Exception {
		String requestBody = """
				{
				  "hostId": "%s",
				  "title": "%s",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON",
				  "venue": %s
				}
				""".formatted(hostId, title, VENUE_JSON);

		return mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andReturn().getResponse().getHeader("Location");
	}
}
