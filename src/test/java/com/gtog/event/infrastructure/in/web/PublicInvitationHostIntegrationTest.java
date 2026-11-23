package com.gtog.event.infrastructure.in.web;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.gtog.event.application.PublicInvitationService;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.*;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.infrastructure.out.mail.CapturingEmailSenderAdapter;
import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.jayway.jsonpath.JsonPath;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.auth.auto-verify-email=false")
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PublicInvitationHostIntegrationTest {
	private static final Instant NOW = Instant.parse("2027-01-01T10:00:00Z");
	private final int port;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final PublishEventUseCase publisher;
	private final PublicInvitationService invitations;
	private final CapturingEmailSenderAdapter mail;
	private final PasswordEncoder passwords;
	private final HttpClient client = HttpClient.newHttpClient();
	private Map<String, String> auth;
	@MockitoBean private Clock clock;
	PublicInvitationHostIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo,
			EventRepositoryPort events, PublishEventUseCase publisher, PublicInvitationService invitations,
			CapturingEmailSenderAdapter mail, PasswordEncoder passwords) {
		this.port = port; this.mongo = mongo; this.events = events; this.publisher = publisher;
		this.invitations = invitations; this.mail = mail; this.passwords = passwords;
	}
	@BeforeEach void setUp() throws Exception {
		when(clock.instant()).thenReturn(NOW);
		mongo.remove(new Query(), EventDocument.class); mongo.remove(new Query(), UserDocument.class);
		mongo.getCollection("responses").deleteMany(new Document()); mail.reset();
		mongo.insert(com.gtog.UserTestFixtures.verified("host-1", "Host", "host@example.com", passwords.encode("password12"), "UTC"));
		String csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var login = send("POST", "/api/auth/login", "{\"email\":\"host@example.com\",\"password\":\"password12\"}",
				Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf));
		assertThat(login.statusCode()).isEqualTo(200);
		auth = Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(login, "JSESSIONID"), "X-XSRF-TOKEN", csrf);
	}
	@AfterEach void close() { client.close(); }
	private Event draft(boolean enabled) {
		return events.save(Event.builder().hostId("host-1").title("Evento").startsAt(LocalDateTime.of(2027, 1, 1, 18, 0))
				.endsAt(LocalDateTime.of(2027, 1, 1, 20, 0)).timeZone("Europe/Madrid").modality(Modality.IN_PERSON)
				.venue(new Venue("Sala", "Dirección", 0.0, 0.0, "place", null)).publicInvitationEnabled(enabled).build());
	}
	private Event published() { return publisher.publish("host-1", draft(true).getId()); }
	private String path(Event event) { return "/api/events/" + event.getId() + "/public-invitation"; }
	private Document bson(String collection, String id) { return mongo.getCollection(collection).find(new Document("_id", id)).first(); }
	private void ok(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
	}
	@Test void creationAcceptsTheIntentionButNeverLeaksItsTokenThroughEventResponses() throws Exception {
		var created = send("POST", "/api/events", """
				{"title":"Nuevo","startsAt":"2027-01-01T18:00:00","endsAt":"2027-01-01T20:00:00","timeZone":"Europe/Madrid",
				"modality":"IN_PERSON","venue":{"placeName":"Sala","address":"Dirección","latitude":0,"longitude":0,"placeId":"p"},"publicInvitationEnabled":true}
				""", auth);
		assertThat(created.statusCode()).isEqualTo(201);
		assertThat(JsonPath.parse(created.body()).read("$.publicInvitationEnabled", Boolean.class)).isTrue();
		String id = JsonPath.parse(created.body()).read("$.id");
		assertThat(bson("events", id).get("publicInvitation", Document.class)).doesNotContainKey("token");
		Event published = publisher.publish("host-1", id);
		var detail = send("GET", "/api/events/" + id, "", auth);
		assertThat(detail.statusCode()).isEqualTo(200);
		assertThat(detail.body()).doesNotContain(published.getPublicInvitation().token(), "\"token\"", "\"publicInvitation\"");
	}
	@Test void draftToggleOnlyChangesIntentionAndPublishedToggleIsIdempotentAndRevocable() throws Exception {
		Event event = draft(false);
		var enabled = send("PUT", path(event), "{\"enabled\":true}", auth); ok(enabled);
		assertThat(JsonPath.parse(enabled.body()).read("$.enabled", Boolean.class)).isTrue();
		assertThat(JsonPath.parse(enabled.body()).read("$.path", String.class)).isNull();
		ok(send("GET", path(event), "", auth));
		event = publisher.publish("host-1", event.getId());
		String first = "/j/" + event.getPublicInvitation().token();
		Document before = bson("events", event.getId());
		var repeated = send("PUT", path(event), "{\"enabled\":true}", auth); ok(repeated);
		assertThat(JsonPath.parse(repeated.body()).read("$.path", String.class)).isEqualTo(first);
		assertThat(bson("events", event.getId())).isEqualTo(before);
		var disabled = send("PUT", path(event), "{\"enabled\":false}", auth); ok(disabled);
		assertThat(JsonPath.parse(disabled.body()).read("$.enabled", Boolean.class)).isFalse();
		assertThat(JsonPath.parse(disabled.body()).read("$.path", String.class)).isNull();
		var renewed = send("PUT", path(event), "{\"enabled\":true}", auth); ok(renewed);
		assertThat(JsonPath.parse(renewed.body()).read("$.path", String.class)).startsWith("/j/").isNotEqualTo(first);
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED"})
	void closedEventsKeepTheLinkReadableButRejectTheToggleAndCloseTheDashboard(EventStatus status) throws Exception {
		Event event = published();
		mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$set", new Document("status", status.name())));
		var get = send("GET", path(event), "", auth); ok(get);
		assertThat(JsonPath.parse(get.body()).read("$.path", String.class)).isEqualTo("/j/" + event.getPublicInvitation().token());
		assertThat(JsonPath.parse(get.body()).read("$.enabled", Boolean.class)).isTrue();
		assertThat(send("PUT", path(event), "{\"enabled\":false}", auth).statusCode()).isEqualTo(409);
		var dashboard = send("GET", "/api/events/" + event.getId() + "/dashboard", "", auth); ok(dashboard);
		assertThat(JsonPath.parse(dashboard.body()).read("$.publicInvitation.open", Boolean.class)).isFalse();
	}
	@Test void dashboardAndGuestListExposeSourceAndEmailSkipsPublicContactsWithoutAnyQuotaOrReservation() throws Exception {
		Event event = published();
		Guest guest = invitations.join(new JoinPublicInvitationCommand(event.getPublicInvitation().token(), null, "Ana", "ana@example.com", null));
		var dashboard = send("GET", "/api/events/" + event.getId() + "/dashboard", "", auth); ok(dashboard);
		var json = JsonPath.parse(dashboard.body());
		assertThat(json.read("$.publicInvitation.enabled", Boolean.class)).isTrue();
		assertThat(json.read("$.publicInvitation.joined", Integer.class)).isEqualTo(1);
		assertThat(json.read("$.publicInvitation.limit", Integer.class)).isEqualTo(25);
		assertThat(json.read("$.publicInvitation.open", Boolean.class)).isTrue();
		assertThat(json.read("$.totals.guests", Integer.class)).isEqualTo(1);
		assertThat(json.read("$.guests[0].source", String.class)).isEqualTo("PUBLIC_LINK");
		assertThat(JsonPath.parse(send("GET", "/api/events/" + event.getId() + "/guests", "", auth).body()).read("$[0].source", String.class)).isEqualTo("PUBLIC_LINK");
		Document beforeEvent = bson("events", event.getId()), beforeUser = bson("users", "host-1");
		var sent = send("POST", "/api/events/" + event.getId() + "/invitations/send", "{\"guestIds\":[\"" + guest.getId() + "\"]}", auth);
		assertThat(sent.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(sent.body()).read("$.results[0].reason", String.class)).isEqualTo("UNVERIFIED_CONTACT");
		assertThat(mail.attempts()).isEmpty();
		assertThat(bson("events", event.getId())).isEqualTo(beforeEvent); assertThat(bson("users", "host-1")).isEqualTo(beforeUser);
		when(clock.instant()).thenReturn(Instant.parse("2027-01-01T21:00:00Z"));
		assertThat(JsonPath.parse(send("GET", "/api/events/" + event.getId() + "/dashboard", "", auth).body()).read("$.publicInvitation.open", Boolean.class)).isFalse();
	}
	@Test void anExpiredDeadlineClosesTheDashboard() throws Exception {
		Event event = published();
		mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$set", new Document("responseDeadline", java.util.Date.from(NOW.minusSeconds(1)))));
		assertThat(JsonPath.parse(send("GET", "/api/events/" + event.getId() + "/dashboard", "", auth).body()).read("$.publicInvitation.open", Boolean.class)).isFalse();
	}
	@ParameterizedTest @ValueSource(strings = {"GET", "PUT"})
	void ownerAndSessionAreRequired(String method) throws Exception {
		Event event = draft(false);
		mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$set", new Document("hostId", "other")));
		assertThat(send(method, path(event), method.equals("PUT") ? "{\"enabled\":true}" : "", auth).statusCode()).isEqualTo(404);
		assertThat(send(method, path(event), method.equals("PUT") ? "{\"enabled\":true}" : "", Map.of("Content-Type", "application/json")).statusCode()).isEqualTo(method.equals("PUT") ? 403 : 401);
	}
	@Test void anAuthenticatedToggleRequiresCsrf() throws Exception {
		Event event = draft(false);
		assertThat(send("PUT", path(event), "{\"enabled\":true}", Map.of("Content-Type", "application/json", "Cookie", auth.get("Cookie"))).statusCode()).isEqualTo(403);
	}
	@ParameterizedTest @ValueSource(strings = {"GET", "PUT"})
	void aValidCsrfCookieDoesNotReplaceTheHostSession(String method) throws Exception {
		Event event = draft(false); String csrf = auth.get("X-XSRF-TOKEN");
		assertThat(send(method, path(event), method.equals("PUT") ? "{\"enabled\":true}" : "",
				Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf)).statusCode()).isEqualTo(401);
	}
	@Test void publicGuestsCanReceiveWhatsAppLinksOnlyWhenTheyHaveAPhone() throws Exception {
		Event event = published(); String token = event.getPublicInvitation().token();
		Guest withPhone = invitations.join(new JoinPublicInvitationCommand(token, null, "Con teléfono", null, "+34612345678"));
		Guest withoutPhone = invitations.join(new JoinPublicInvitationCommand(token, null, "Sin teléfono", null, null));
		Document before = bson("events", event.getId());
		var response = send("POST", "/api/events/" + event.getId() + "/invitations/whatsapp-links", "", auth); ok(response);
		var json = JsonPath.parse(response.body());
		assertThat(json.read("$.results[0].guestId", String.class)).isEqualTo(withPhone.getId());
		assertThat(json.read("$.results[0].status", String.class)).isEqualTo("READY");
		assertThat(json.read("$.results[0].url", String.class)).startsWith("https://wa.me/34612345678?text=");
		assertThat(json.read("$.results[1].guestId", String.class)).isEqualTo(withoutPhone.getId());
		assertThat(json.read("$.results[1].reason", String.class)).isEqualTo("NO_PHONE");
		assertThat(bson("events", event.getId())).isEqualTo(before);
	}
	@Test void editingAPublicGuestsContactPreservesSourceAndStillSkipsEmailWithoutQuotaOrReservation() throws Exception {
		Event event = published();
		Guest guest = invitations.join(new JoinPublicInvitationCommand(event.getPublicInvitation().token(), null, "Ana", null, null));
		var edit = send("PUT", "/api/events/" + event.getId() + "/guests/" + guest.getId(),
				"{\"name\":\"Editada\",\"email\":\"new@example.com\",\"phone\":\"+34 612 345 678\"}", auth);
		assertThat(edit.statusCode()).isEqualTo(200);
		Guest changed = events.findById(event.getId()).orElseThrow().getGuest(guest.getId());
		assertThat(changed.getEmail()).isEqualTo("new@example.com"); assertThat(changed.getPhoneE164()).isEqualTo("+34612345678");
		assertThat(changed.getSource()).isEqualTo(GuestSource.PUBLIC_LINK); assertThat(changed.getJoinedAt()).isEqualTo(guest.getJoinedAt());
		Document beforeEvent = bson("events", event.getId()), beforeUser = bson("users", "host-1");
		var response = send("POST", "/api/events/" + event.getId() + "/invitations/send", "", auth); ok(response);
		assertThat(JsonPath.parse(response.body()).read("$.results[0].reason", String.class)).isEqualTo("UNVERIFIED_CONTACT");
		assertThat(mail.attempts()).isEmpty(); assertThat(bson("events", event.getId())).isEqualTo(beforeEvent);
		assertThat(bson("users", "host-1")).isEqualTo(beforeUser);
	}
	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)); headers.forEach(request::header);
		return client.send(request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private String cookie(HttpResponse<?> response, String name) {
		return response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
				.filter(value -> value.getName().equals(name)).findFirst().orElseThrow().getValue();
	}
}
