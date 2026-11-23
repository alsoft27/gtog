package com.gtog.event.infrastructure.in.web;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.gtog.event.application.PublicInvitationService;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.*;
import com.gtog.event.domain.port.out.*;
import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.jayway.jsonpath.JsonPath;
import static com.gtog.event.GuestTestFixtures.token;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.auth.auto-verify-email=false")
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
class PublicInvitationPublicationWebIntegrationTest {
	private static final String LINK_TOKEN = token(80000), GUEST_TOKEN = token(80001);
	private final int port;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final EventGuestRepositoryPort guests;
	private final PublicInvitationService invitations;
	private final PublishEventUseCase publisher;
	private final PasswordEncoder passwords;
	private final HttpClient client = HttpClient.newHttpClient();
	private Map<String, String> auth;
	@MockitoSpyBean private PublicInvitationTokenGeneratorPort linkTokens;
	@MockitoSpyBean private GuestTokenGeneratorPort guestTokens;
	@MockitoBean private Clock clock;

	PublicInvitationPublicationWebIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo,
			EventRepositoryPort events, EventGuestRepositoryPort guests, PublicInvitationService invitations,
			PublishEventUseCase publisher, PasswordEncoder passwords) {
		this.port = port; this.mongo = mongo; this.events = events; this.guests = guests; this.invitations = invitations;
		this.publisher = publisher; this.passwords = passwords;
	}
	@BeforeEach void setUp() throws Exception {
		when(clock.instant()).thenReturn(Instant.parse("2027-01-01T10:00:00Z"));
		mongo.remove(new Query(), EventDocument.class); mongo.remove(new Query(), UserDocument.class);
		mongo.insert(com.gtog.UserTestFixtures.verified("host-1", "Host", "host@example.com", passwords.encode("password12"), "UTC"));
		String csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var login = send("POST", "/api/auth/login", "{\"email\":\"host@example.com\",\"password\":\"password12\"}",
				Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf));
		assertThat(login.statusCode()).isEqualTo(200);
		auth = Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(login, "JSESSIONID"), "X-XSRF-TOKEN", csrf);
	}
	@AfterEach void closeClient() { client.close(); }
	private Event draft(boolean enabled) {
		return events.save(Event.builder().hostId("host-1").title("Evento").startsAt(LocalDateTime.of(2027, 1, 1, 18, 0))
				.endsAt(LocalDateTime.of(2027, 1, 1, 20, 0)).timeZone("Europe/Madrid").modality(Modality.IN_PERSON)
				.venue(new Venue("Sala", "Dirección", 0.0, 0.0, "place", null)).publicInvitationEnabled(enabled).build());
	}
	private Event published(boolean enabled) { return publisher.publish("host-1", draft(enabled).getId()); }
	private Document bson(String id) { return mongo.getCollection("events").find(new Document("_id", id)).first(); }
	private Event withExistingGuest() {
		Event owner = published(false); owner.addGuest(Guest.create("First", "first@example.com", null, GUEST_TOKEN, value -> value));
		return events.save(owner);
	}
	private void assertPrivate(CapturedOutput output, String body) {
		assertThat(body).doesNotContain(LINK_TOKEN, GUEST_TOKEN);
		assertThat(output.getAll()).doesNotContain(LINK_TOKEN, GUEST_TOKEN);
	}
	@Test void aPublicTokenIndexErrorIsTranslatedWithoutMessageCauseOrLogLeak(CapturedOutput output) {
		doReturn(LINK_TOKEN).when(linkTokens).generate(); published(true); Event other = published(false);
		Document before = bson(other.getId());
		var error = catchThrowableOfType(() -> invitations.change("host-1", other.getId(), true), PublicInvitationStorageException.class);
		assertThat(error.getCause()).isNull(); assertPrivate(output, error.getMessage()); assertThat(bson(other.getId())).isEqualTo(before);
	}
	@Test void aGuestTokenIndexErrorInPublicRegistrationIsTranslatedWithoutCauseOrLogLeak(CapturedOutput output) {
		withExistingGuest(); doReturn(LINK_TOKEN).when(linkTokens).generate(); published(true);
		doReturn(GUEST_TOKEN).when(guestTokens).generate();
		var error = catchThrowableOfType(() -> invitations.join(new JoinPublicInvitationCommand(LINK_TOKEN, null, "Ana", null, null)), PublicInvitationStorageException.class);
		assertThat(error.getCause()).isNull(); assertPrivate(output, error.getMessage());
	}
	@Test void aGuestTokenIndexErrorInHostRegistrationHasAPrivate409AndCompleteLogs(CapturedOutput output) throws Exception {
		withExistingGuest(); Event other = published(false); doReturn(GUEST_TOKEN).when(guestTokens).generate();
		var response = send("POST", "/api/events/" + other.getId() + "/guests", "{\"name\":\"Other\",\"email\":\"other@example.com\"}", auth);
		assertThat(response.statusCode()).isEqualTo(409); assertPrivate(output, response.body());
		var error = catchThrowableOfType(() -> guests.addGuest(other.getId(), "host-1", other.getVersion(),
				Guest.create("Other", "other@example.com", null, GUEST_TOKEN, value -> value)), DuplicateGuestTokenException.class);
		assertThat(error.getCause()).isNull(); assertPrivate(output, error.getMessage());
	}
	@Test void aPublicationTokenCollisionReturnsGeneric500WithoutTokenInBodyOrCompleteLogs(CapturedOutput output) throws Exception {
		doReturn(LINK_TOKEN).when(linkTokens).generate(); published(true); Event other = draft(true); Document before = bson(other.getId());
		var response = send("POST", "/api/events/" + other.getId() + "/publish", "", auth);
		assertThat(response.statusCode()).isEqualTo(500);
		assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
		assertThat(JsonPath.parse(response.body()).read("$.detail", String.class)).isEqualTo("Internal server error");
		assertPrivate(output, response.body()); assertThat(bson(other.getId())).isEqualTo(before);
		var error = catchThrowableOfType(() -> publisher.publish("host-1", other.getId()), EventStorageException.class);
		assertThat(error.getCause()).isNull(); assertPrivate(output, error.getMessage());
	}
	@ParameterizedTest @ValueSource(booleans = { false, true })
	void draftPutAndOtherEditsPreserveBothIntentions(boolean enabled) throws Exception {
		Event event = draft(enabled); String path = "/api/events/" + event.getId();
		var edit = send("PUT", path, """
				{"title":"Editado","startsAt":"2027-01-01T18:00:00","endsAt":"2027-01-01T20:00:00",
				 "timeZone":"Europe/Madrid","modality":"IN_PERSON"}
				""", auth);
		assertThat(edit.statusCode()).isEqualTo(200); assertIntention(event.getId(), enabled);
		var venue = send("PUT", path + "/venue", "{\"placeName\":\"Nueva sala\",\"address\":\"Dirección\",\"latitude\":0,\"longitude\":0,\"placeId\":\"new-place\"}", auth);
		assertThat(venue.statusCode()).isEqualTo(200); assertIntention(event.getId(), enabled);
		var options = send("PUT", path + "/response-options", "{\"responseOptions\":[{\"label\":\"Sí\",\"countsAsAttendance\":true},{\"label\":\"No\",\"countsAsAttendance\":false}]}", auth);
		assertThat(options.statusCode()).isEqualTo(200); assertIntention(event.getId(), enabled);
		var deadline = send("PUT", path + "/response-deadline", "{\"responseDeadline\":\"2027-01-01T17:00:00\"}", auth);
		assertThat(deadline.statusCode()).isEqualTo(200); assertIntention(event.getId(), enabled);
		assertThat(send("POST", path + "/publish", "", auth).statusCode()).isEqualTo(200);
		Event published = events.findById(event.getId()).orElseThrow();
		assertThat(published.isPublicInvitationEnabled()).isEqualTo(enabled);
		assertThat(published.getPublicInvitation().token() != null).isEqualTo(enabled);
		var publishedLink = published.getPublicInvitation();
		assertThat(send("PUT", path + "/response-options", "{\"responseOptions\":[{\"label\":\"Voy\",\"countsAsAttendance\":true},{\"label\":\"No voy\",\"countsAsAttendance\":false}]}", auth).statusCode()).isEqualTo(200);
		assertThat(events.findById(event.getId()).orElseThrow().getPublicInvitation()).isEqualTo(publishedLink);
		assertThat(send("PUT", path + "/response-deadline", "{\"responseDeadline\":\"2027-01-01T16:00:00\"}", auth).statusCode()).isEqualTo(200);
		assertThat(events.findById(event.getId()).orElseThrow().getPublicInvitation()).isEqualTo(publishedLink);
	}
	@ParameterizedTest @ValueSource(booleans = { false, true })
	void onlineAccessEditingKeepsTheDraftIntention(boolean enabled) throws Exception {
		Event event = events.save(Event.builder().hostId("host-1").title("Evento en línea")
				.startsAt(LocalDateTime.of(2027, 1, 1, 18, 0)).endsAt(LocalDateTime.of(2027, 1, 1, 20, 0))
				.timeZone("Europe/Madrid").modality(Modality.ONLINE).publicInvitationEnabled(enabled)
				.onlineAccess(new OnlineAccess("Jitsi", "https://meeting.example.com/old", null, null, null, LinkVisibility.ALWAYS, null)).build());
		var response = send("PUT", "/api/events/" + event.getId() + "/online-access",
				"{\"platform\":\"Jitsi\",\"url\":\"https://meeting.example.com/new\",\"linkVisibility\":\"ALWAYS\"}", auth);
		assertThat(response.statusCode()).isEqualTo(200); assertIntention(event.getId(), enabled);
	}
	private void assertIntention(String id, boolean enabled) {
		assertThat(bson(id).get("publicInvitation", Document.class)).containsEntry("enabled", enabled).doesNotContainKey("token");
	}
	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)); headers.forEach(request::header);
		return client.send(request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private String cookie(HttpResponse<?> response, String name) {
		return response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
				.filter(cookie -> cookie.getName().equals(name)).findFirst().orElseThrow().getValue();
	}
}
