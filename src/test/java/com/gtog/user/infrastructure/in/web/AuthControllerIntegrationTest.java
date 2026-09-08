package com.gtog.user.infrastructure.in.web;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.event.infrastructure.out.persistence.EventMongoRepository;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.gtog.user.infrastructure.out.persistence.UserMongoRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

	static final String HOST_A_EMAIL = "hosta@example.com";
	static final String HOST_B_EMAIL = "hostb@example.com";
	private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
	private static final String HASHED_PASSWORD = PASSWORD_ENCODER.encode("password12");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserMongoRepository userMongoRepository;

	@Autowired
	private EventMongoRepository eventMongoRepository;

	@BeforeEach
	void cleanDatabase() {
		eventMongoRepository.deleteAll();
		userMongoRepository.deleteAll();
		UserDocument hostA = new UserDocument();
		hostA.setId("hosta-id");
		hostA.setName("Host A");
		hostA.setEmail(HOST_A_EMAIL);
		hostA.setPasswordHash(HASHED_PASSWORD);
		hostA.setTimeZone("Europe/Madrid");
		userMongoRepository.save(hostA);

		UserDocument hostB = new UserDocument();
		hostB.setId("hostb-id");
		hostB.setName("Host B");
		hostB.setEmail(HOST_B_EMAIL);
		hostB.setPasswordHash(HASHED_PASSWORD);
		hostB.setTimeZone("Europe/Madrid");
		userMongoRepository.save(hostB);
	}

	@Test
	void registersUserSuccessfully() throws Exception {
		String body = """
				{
				  "name": "Ana García",
				  "email": "ANA@Example.COM",
				  "rawPassword": "password12",
				  "timeZone": "Europe/Madrid"
				}
				""";

		mockMvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("Ana García"))
				.andExpect(jsonPath("$.email").value("ana@example.com"))
				.andExpect(jsonPath("$.timeZone").value("Europe/Madrid"));
	}

	@Test
	void rejectsRegistrationWithDuplicateEmail() throws Exception {
		String body = """
				{
				  "name": "Host A",
				  "email": "%s",
				  "rawPassword": "password12",
				  "timeZone": "Europe/Madrid"
				}
				""".formatted(HOST_A_EMAIL);

		mockMvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void rejectsRegistrationWithPasswordTooShort() throws Exception {
		String body = """
				{
				  "name": "Test User",
				  "email": "new@example.com",
				  "rawPassword": "short1",
				  "timeZone": "Europe/Madrid"
				}
				""";

		mockMvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	void rejectsRegistrationWithBlankName() throws Exception {
		String body = """
				{
				  "name": "",
				  "email": "new@example.com",
				  "rawPassword": "password12",
				  "timeZone": "Europe/Madrid"
				}
				""";

		mockMvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	void rejectsRegistrationWithInvalidEmail() throws Exception {
		String body = """
				{
				  "name": "Test User",
				  "email": "not-an-email",
				  "rawPassword": "password12",
				  "timeZone": "Europe/Madrid"
				}
				""";

		mockMvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	void logsInSuccessfully() throws Exception {
		String body = """
				{
				  "email": "%s",
				  "password": "password12"
				}
				""".formatted(HOST_A_EMAIL);

		mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("hosta-id"))
				.andExpect(jsonPath("$.name").value("Host A"))
				.andExpect(jsonPath("$.email").value(HOST_A_EMAIL))
				.andExpect(jsonPath("$.timeZone").value("Europe/Madrid"));
	}

	@Test
	void rejectsLoginWithWrongPassword() throws Exception {
		String body = """
				{
				  "email": "%s",
				  "password": "wrongpassword"
				}
				""".formatted(HOST_A_EMAIL);

		mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsLoginWithUnknownEmail() throws Exception {
		String body = """
				{
				  "email": "nobody@example.com",
				  "password": "password12"
				}
				""";

		mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logsOutSuccessfully() throws Exception {
		// Login para obtener sesión real
		MvcResult loginResult = mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + HOST_A_EMAIL + "\",\"password\":\"password12\"}"))
				.andExpect(status().isOk())
				.andReturn();

		MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

		mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
				.andExpect(status().isNoContent());
	}

	@Test
	void returns401WhenAccessingProtectedEndpointWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/events"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithUserDetails(value = HOST_B_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void accessToAnotherHostsEventReturns404() throws Exception {
		// Siembra un evento de Host A directamente en Mongo; version=null para que Spring Data lo inserte sin check
		EventDocument eventA = new EventDocument(
				"event-a-id", "hosta-id", "Evento de A", null,
				LocalDateTime.of(2026, 10, 1, 18, 0), LocalDateTime.of(2026, 10, 1, 23, 0),
				"Europe/Madrid", "IN_PERSON", "DRAFT",
				List.of(), false, true, null, null, null, null, null, null);
		eventMongoRepository.save(eventA);

		// Host B intenta acceder al evento de Host A → 404 (nunca 403)
		mockMvc.perform(get("/api/events/event-a-id"))
				.andExpect(status().isNotFound());
	}

	private void seedHostAEvents() {
		EventDocument inPerson = new EventDocument(
				"event-of-a", "hosta-id", "Evento de A", null,
				LocalDateTime.of(2027, 1, 1, 20, 0), LocalDateTime.of(2027, 1, 1, 23, 0),
				"Europe/Madrid", "IN_PERSON", "PUBLISHED",
				List.of(), false, true, null, null, null, null, null, null);
		eventMongoRepository.save(inPerson);

		EventDocument online = new EventDocument(
				"event-online-of-a", "hosta-id", "Evento online de A", null,
				LocalDateTime.of(2027, 1, 1, 20, 0), LocalDateTime.of(2027, 1, 1, 23, 0),
				"Europe/Madrid", "ONLINE", "PUBLISHED",
				List.of(), false, true, null, null, null, null, null, null);
		eventMongoRepository.save(online);
	}

	@Test
	void invitationsEndpointIsAccessibleWithoutAuthentication() throws Exception {
		// /api/invitations/** es público aunque el endpoint completo no exista todavía (R7):
		// la regla de SecurityConfig debe devolver 404, no 401.
		mockMvc.perform(get("/api/invitations/any-token"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(value = HOST_B_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void updateEventOfAnotherHostReturns404() throws Exception {
		seedHostAEvents();
		String requestBody = """
				{
				  "title": "Intento de edición",
				  "startsAt": "2026-09-01T20:00:00",
				  "endsAt": "2026-09-01T23:00:00",
				  "timeZone": "Europe/Madrid",
				  "modality": "IN_PERSON",
				  "venue": {
				    "placeName": "Sala", "address": "Dir", "latitude": 0.0, "longitude": 0.0, "placeId": "pid"
				  }
				}
				""";
		mockMvc.perform(put("/api/events/event-of-a").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(value = HOST_B_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void publishEventOfAnotherHostReturns404() throws Exception {
		seedHostAEvents();
		mockMvc.perform(post("/api/events/event-of-a/publish").with(csrf()))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(value = HOST_B_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void cancelEventOfAnotherHostReturns404() throws Exception {
		seedHostAEvents();
		mockMvc.perform(post("/api/events/event-of-a/cancel").with(csrf()))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(value = HOST_B_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void replaceVenueOfAnotherHostsEventReturns404() throws Exception {
		seedHostAEvents();
		String venueJson = """
				{
				  "placeName": "Otro sitio", "address": "Dir", "latitude": 0.0, "longitude": 0.0, "placeId": "pid"
				}
				""";
		mockMvc.perform(put("/api/events/event-of-a/venue").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(venueJson))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(value = HOST_B_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void replaceOnlineAccessOfAnotherHostsEventReturns404() throws Exception {
		seedHostAEvents();
		String onlineAccessJson = """
				{
				  "platform": "Zoom",
				  "url": "https://zoom.us/j/123",
				  "linkVisibility": "ALWAYS"
				}
				""";
		mockMvc.perform(put("/api/events/event-online-of-a/online-access").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(onlineAccessJson))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(value = HOST_A_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void hostSeesOnlyOwnEventsInTheListing() throws Exception {
		// Siembra 2 eventos de Host A y 1 de Host B directamente en Mongo; version=null para insert sin check
		EventDocument eventA1 = new EventDocument(
				"event-a1", "hosta-id", "Evento A1", null,
				LocalDateTime.of(2026, 10, 1, 18, 0), LocalDateTime.of(2026, 10, 1, 23, 0),
				"Europe/Madrid", "IN_PERSON", "DRAFT",
				List.of(), false, true, null, null, null, null, null, null);
		EventDocument eventA2 = new EventDocument(
				"event-a2", "hosta-id", "Evento A2", null,
				LocalDateTime.of(2026, 11, 1, 18, 0), LocalDateTime.of(2026, 11, 1, 23, 0),
				"Europe/Madrid", "IN_PERSON", "DRAFT",
				List.of(), false, true, null, null, null, null, null, null);
		EventDocument eventB1 = new EventDocument(
				"event-b1", "hostb-id", "Evento B1", null,
				LocalDateTime.of(2026, 12, 1, 18, 0), LocalDateTime.of(2026, 12, 1, 23, 0),
				"Europe/Madrid", "IN_PERSON", "DRAFT",
				List.of(), false, true, null, null, null, null, null, null);
		eventMongoRepository.save(eventA1);
		eventMongoRepository.save(eventA2);
		eventMongoRepository.save(eventB1);

		// Host A solo ve sus 2 eventos
		mockMvc.perform(get("/api/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
	}
}
