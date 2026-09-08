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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.event.infrastructure.out.persistence.EventMongoRepository;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.gtog.user.infrastructure.out.persistence.UserMongoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

	static final String TEST_HOST_EMAIL = "host@example.com";
	static final String TEST_OTHER_HOST_EMAIL = "other@example.com";
	private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
	static final String TEST_HASHED_PASSWORD = PASSWORD_ENCODER.encode("password12");

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

	@Autowired
	private UserMongoRepository userMongoRepository;

	@BeforeEach
	void cleanDatabase() {
		eventMongoRepository.deleteAll();
		userMongoRepository.deleteAll();
		UserDocument host = new UserDocument();
		host.setId("host-id-1");
		host.setName("Test Host");
		host.setEmail(TEST_HOST_EMAIL);
		host.setPasswordHash(TEST_HASHED_PASSWORD);
		host.setTimeZone("Europe/Madrid");
		userMongoRepository.save(host);
		UserDocument otherHost = new UserDocument();
		otherHost.setId("host-id-2");
		otherHost.setName("Other Host");
		otherHost.setEmail(TEST_OTHER_HOST_EMAIL);
		otherHost.setPasswordHash(TEST_HASHED_PASSWORD);
		otherHost.setTimeZone("Europe/Madrid");
		userMongoRepository.save(otherHost);
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void createsAnEventAndReturns201WithLocationAndDraftStatus() throws Exception {
		String requestBody = """
				{
				  "title": "Cumpleaños",
				  "description": "Fiesta de cumpleaños",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON",
				  "venue": %s
				}
				""".formatted(VENUE_JSON);

		mockMvc.perform(post("/api/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.hostId").value("host-id-1"));

		List<EventDocument> stored = eventMongoRepository.findAll();
		assertThat(stored).hasSize(1);
		EventDocument document = stored.get(0);
		assertThat(document.getHostId()).isEqualTo("host-id-1");
		assertThat(document.getTitle()).isEqualTo("Cumpleaños");
		assertThat(document.getStartsAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 20, 0));
		assertThat(document.getEndsAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 23, 0));
		assertThat(document.getTimeZone()).isEqualTo("Europe/Madrid");
		assertThat(document.getModality()).isEqualTo("IN_PERSON");
		assertThat(document.getStatus()).isEqualTo("DRAFT");
		assertThat(document.getVersion()).isEqualTo(0L);
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void returns422WithProblemDetailWhenEndsAtIsNotAfterStartsAt() throws Exception {
		String requestBody = """
				{
				  "title": "Cumpleaños",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T20:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON"
				}
				""";

		mockMvc.perform(post("/api/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").exists());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void returns422WithProblemDetailWhenTimeZoneIsInvalid() throws Exception {
		String requestBody = """
				{
				  "title": "Cumpleaños",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Not/AZone",
				  "modality": "IN_PERSON"
				}
				""";

		mockMvc.perform(post("/api/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").exists());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void returns422WithProblemDetailWhenTitleIsBlank() throws Exception {
		String requestBody = """
				{
				  "title": "   ",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON"
				}
				""";

		mockMvc.perform(post("/api/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").exists());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void returns400WhenARequiredFieldIsMissing() throws Exception {
		String requestBody = """
				{
				  "title": "Cumpleaños",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid"
				}
				""";

		mockMvc.perform(post("/api/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isBadRequest());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void returnsAnEventByIdWithFullDetail() throws Exception {
		String location = createEvent("Cumpleaños");

		mockMvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hostId").value("host-id-1"))
				.andExpect(jsonPath("$.title").value("Cumpleaños"))
				.andExpect(jsonPath("$.status").value("DRAFT"));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void returns404WithProblemDetailWhenTheEventDoesNotExist() throws Exception {
		mockMvc.perform(get("/api/events/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").exists());
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void listsEventSummariesForTheGivenHost() throws Exception {
		createEvent("Cumpleaños");
		createEvent("Boda");
		createEvent("Otro evento");

		mockMvc.perform(get("/api/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].title").exists())
				.andExpect(jsonPath("$[0].hostId").doesNotExist());
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void returnsEmptyListWhenTheHostHasNoEvents() throws Exception {
		mockMvc.perform(get("/api/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void createsAnEventWithDefaultResponseOptionsWhenNoneAreProvided() throws Exception {
		String location = createEvent("Cumpleaños");

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
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void createsAnEventWithCustomResponseOptions() throws Exception {
		String requestBody = """
				{
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
				.perform(post("/api/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.responseOptions.length()").value(3))
				.andExpect(jsonPath("$.allowComment").value(true))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(get(location))
				.andExpect(jsonPath("$.responseOptions[0].label").value("Voy"))
				.andExpect(jsonPath("$.responseOptions[2].label").value("Quizas"));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void returns422WhenResponseDeadlineIsAfterStartsAt() throws Exception {
		String requestBody = """
				{
				  "title": "Cumpleaños",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON",
				  "responseDeadline": "2026-09-01T20:30:00"
				}
				""";

		mockMvc.perform(post("/api/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void replaceResponseOptionsReplacesTheListAndReturns200WithTheUpdatedEvent() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		String requestBody = """
				{
				  "responseOptions": [
				    { "label": "Voy", "countsAsAttendance": true },
				    { "label": "No voy", "countsAsAttendance": false }
				  ]
				}
				""";

		mockMvc.perform(put("/api/events/" + eventId + "/response-options")
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.responseOptions.length()").value(2))
				.andExpect(jsonPath("$.responseOptions[0].label").value("Voy"));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void replaceResponseOptionsPreservesTheIdOfAnExistingOptionWhenRenamedById() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		String eventJson = mockMvc.perform(get(location))
				.andReturn().getResponse().getContentAsString();
		String firstOptionId = JsonPath.read(eventJson, "$.responseOptions[0].id");

		String requestBody = """
				{
				  "responseOptions": [
				    { "id": "%s", "label": "Asisto seguro", "countsAsAttendance": true },
				    { "label": "No asisto", "countsAsAttendance": false }
				  ]
				}
				""".formatted(firstOptionId);

		mockMvc.perform(put("/api/events/" + eventId + "/response-options")
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.responseOptions[0].id").value(firstOptionId))
				.andExpect(jsonPath("$.responseOptions[0].label").value("Asisto seguro"));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void replaceResponseOptionsReturns422WhenAnIdDoesNotBelongToTheEvent() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		String requestBody = """
				{
				  "responseOptions": [
				    { "id": "does-not-exist", "label": "Asisto", "countsAsAttendance": true },
				    { "label": "No asisto", "countsAsAttendance": false }
				  ]
				}
				""";

		mockMvc.perform(put("/api/events/" + eventId + "/response-options")
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void replaceResponseOptionsReturns404WhenTheEventDoesNotExist() throws Exception {
		String requestBody = """
				{
				  "responseOptions": [
				    { "label": "Asisto", "countsAsAttendance": true },
				    { "label": "No asisto", "countsAsAttendance": false }
				  ]
				}
				""";

		mockMvc.perform(put("/api/events/does-not-exist/response-options")
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void returns422WhenInPersonEventHasNoVenue() throws Exception {
		String requestBody = """
				{
				  "title": "Cumpleaños",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON"
				}
				""";

		mockMvc.perform(post("/api/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void createsAnOnlineEventWithOnlineAccessAndReturns201() throws Exception {
		String location = createOnlineEvent("Charla online");

		mockMvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.modality").value("ONLINE"))
				.andExpect(jsonPath("$.onlineAccess.platform").value("Zoom"))
				.andExpect(jsonPath("$.onlineAccess.url").value("https://zoom.us/j/123456789"))
				.andExpect(jsonPath("$.venue").doesNotExist());
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void returns422WhenOnlineEventHasNoOnlineAccess() throws Exception {
		String requestBody = """
				{
				  "title": "Charla online",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "ONLINE"
				}
				""";

		mockMvc.perform(post("/api/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity());

		assertThat(eventMongoRepository.findAll()).isEmpty();
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void replaceVenueUpdatesTheVenueAndReturns200() throws Exception {
		String location = createEvent("Cumpleaños");
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
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.venue.placeName").value("Otra sala"));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void replaceVenueReturns404WhenTheEventDoesNotExist() throws Exception {
		mockMvc.perform(put("/api/events/does-not-exist/venue")
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VENUE_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void replaceVenueReturns409WhenTheEventIsOnline() throws Exception {
		String location = createOnlineEvent("Charla online");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		mockMvc.perform(put("/api/events/" + eventId + "/venue")
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VENUE_JSON))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void replaceOnlineAccessUpdatesItAndReturns200() throws Exception {
		String location = createOnlineEvent("Charla online");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		String requestBody = """
				{
				  "platform": "Teams",
				  "url": "https://teams.microsoft.com/x",
				  "linkVisibility": "ALWAYS"
				}
				""";

		mockMvc.perform(put("/api/events/" + eventId + "/online-access")
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.onlineAccess.platform").value("Teams"));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void replaceOnlineAccessReturns404WhenTheEventDoesNotExist() throws Exception {
		mockMvc.perform(put("/api/events/does-not-exist/online-access")
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ONLINE_ACCESS_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void replaceOnlineAccessReturns409WhenTheEventIsInPerson() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		mockMvc.perform(put("/api/events/" + eventId + "/online-access")
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ONLINE_ACCESS_JSON))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void replaceResponseOptionsReturns409WhenEventIsPublished() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);
		publishEvent(eventId);

		String requestBody = """
				{
				  "responseOptions": [
				    { "label": "Asisto", "countsAsAttendance": true },
				    { "label": "No asisto", "countsAsAttendance": false }
				  ]
				}
				""";

		mockMvc.perform(put("/api/events/" + eventId + "/response-options")
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void updateEventReturns200WithUpdatedFields() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		String requestBody = """
				{
				  "title": "Boda",
				  "description": "Celebracion",
				  "startsAt": "2026-10-01T18:00:00",
				  "endsAt": "2026-10-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON",
				  "venue": %s,
				  "allowComment": true,
				  "allowResponseChange": false
				}
				""".formatted(VENUE_JSON);

		mockMvc.perform(put("/api/events/" + eventId)
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Boda"))
				.andExpect(jsonPath("$.allowComment").value(true))
				.andExpect(jsonPath("$.allowResponseChange").value(false))
				.andExpect(jsonPath("$.status").value("DRAFT"));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void updateEventReturns409WhenEventIsPublished() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);
		publishEvent(eventId);

		String requestBody = """
				{
				  "title": "Boda",
				  "startsAt": "2026-10-01T18:00:00",
				  "endsAt": "2026-10-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON",
				  "venue": %s
				}
				""".formatted(VENUE_JSON);

		mockMvc.perform(put("/api/events/" + eventId)
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void updateEventReturns422WhenModalityChangesWithoutLocationBlock() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		String requestBody = """
				{
				  "title": "Charla",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "ONLINE"
				}
				""";

		mockMvc.perform(put("/api/events/" + eventId)
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void publishEventReturns200WithPublishedStatus() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		mockMvc.perform(post("/api/events/" + eventId + "/publish").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED"));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void publishEventReturns409WhenAlreadyPublished() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);
		publishEvent(eventId);

		mockMvc.perform(post("/api/events/" + eventId + "/publish").with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void cancelEventReturns200WithCancelledStatus() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);
		publishEvent(eventId);

		mockMvc.perform(post("/api/events/" + eventId + "/cancel").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Imprevisto\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancelledAt").exists())
				.andExpect(jsonPath("$.cancellationReason").value("Imprevisto"));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void cancelEventReturns200WithNullReasonWhenBodyIsEmpty() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);
		publishEvent(eventId);

		mockMvc.perform(post("/api/events/" + eventId + "/cancel").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancellationReason").doesNotExist());
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void cancelEventReturns409WhenEventIsInDraft() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);

		mockMvc.perform(post("/api/events/" + eventId + "/cancel").with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void cancelEventReturns409WhenAlreadyCancelled() throws Exception {
		String location = createEvent("Cumpleaños");
		String eventId = location.substring(location.lastIndexOf('/') + 1);
		publishEvent(eventId);
		mockMvc.perform(post("/api/events/" + eventId + "/cancel").with(csrf()));

		mockMvc.perform(post("/api/events/" + eventId + "/cancel").with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	private void publishEvent(String eventId) throws Exception {
		mockMvc.perform(post("/api/events/" + eventId + "/publish").with(csrf()))
				.andExpect(status().isOk());
	}

	private String createOnlineEvent(String title) throws Exception {
		String requestBody = """
				{
				  "title": "%s",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "ONLINE",
				  "onlineAccess": %s
				}
				""".formatted(title, ONLINE_ACCESS_JSON);

		return mockMvc.perform(post("/api/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader("Location");
	}

	private String createEvent(String title) throws Exception {
		String requestBody = """
				{
				  "title": "%s",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON",
				  "venue": %s
				}
				""".formatted(title, VENUE_JSON);

		return mockMvc.perform(post("/api/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader("Location");
	}
}
