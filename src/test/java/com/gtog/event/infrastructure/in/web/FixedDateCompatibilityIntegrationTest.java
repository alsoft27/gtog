package com.gtog.event.infrastructure.in.web;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.gtog.UserTestFixtures;
import com.gtog.event.domain.model.EventType;
import com.gtog.event.domain.model.FixedDateSchedule;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.auth.auto-verify-email=false")
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FixedDateCompatibilityIntegrationTest {
	private static final String REQUEST = """
			{"title":"Cena","startsAt":"2027-03-28T01:30:00","endsAt":"2027-03-28T04:30:00",
			"timeZone":"Europe/Madrid","modality":"IN_PERSON","publicInvitationEnabled":true,
			"venue":{"placeName":"Sala","address":"Dirección","latitude":0,"longitude":0,"placeId":"p"}}
			""";
	private final int port;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final PasswordEncoder passwords;
	private final HttpClient client = HttpClient.newHttpClient();
	private Map<String, String> auth;
	@MockitoBean private Clock clock;

	FixedDateCompatibilityIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo,
			EventRepositoryPort events, PasswordEncoder passwords) {
		this.port = port; this.mongo = mongo; this.events = events; this.passwords = passwords;
	}

	@BeforeEach void setUp() throws Exception {
		when(clock.instant()).thenReturn(Instant.parse("2027-03-01T10:00:00Z"));
		mongo.remove(new Query(), EventDocument.class);
		mongo.remove(new Query(), UserDocument.class);
		mongo.getCollection("responses").deleteMany(new Document());
		mongo.insert(UserTestFixtures.verified("host", "Anfitrión", "host@example.com", passwords.encode("password12"), "UTC"));
		String csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var login = send("POST", "/api/auth/login", "{\"email\":\"host@example.com\",\"password\":\"password12\"}",
				Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf));
		assertThat(login.statusCode()).isEqualTo(200);
		auth = Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(login, "JSESSIONID"),
				"X-XSRF-TOKEN", csrf);
	}

	@AfterEach void close() { client.close(); }

	@Test void theExistingCreationRequestPersistsAnExplicitFixedDateTypeAndKeepsTheLocalFields() throws Exception {
		var created = send("POST", "/api/events", REQUEST, auth);
		assertThat(created.statusCode()).isEqualTo(201);
		var json = JsonPath.parse(created.body());
		String id = json.read("$.id");
		assertThat(created.headers().firstValue("Location")).contains("/api/events/" + id);
		assertThat(json.read("$.eventType", String.class)).isEqualTo("FIXED_DATE");
		assertThat(json.read("$.startsAt", String.class)).isEqualTo("2027-03-28T01:30:00");
		assertThat(json.read("$.endsAt", String.class)).isEqualTo("2027-03-28T04:30:00");
		assertThat(json.read("$.timeZone", String.class)).isEqualTo("Europe/Madrid");
		assertThat(bson(id).getString("eventType")).isEqualTo("FIXED_DATE");
		assertThat(bson(id).getLong("version")).isZero();
		assertThat(events.findById(id).orElseThrow().getSchedule()).isInstanceOf(FixedDateSchedule.class);
		var listing = send("GET", "/api/events", "", auth);
		assertThat(listing.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(listing.body()).read("$[0].eventType", String.class)).isEqualTo("FIXED_DATE");
	}

	@Test void aDocumentWithoutTypeRemainsReadableEditablePublishableAndAnswerableWithoutAMigration() throws Exception {
		var created = send("POST", "/api/events", REQUEST, auth);
		assertThat(created.statusCode()).isEqualTo(201);
		String id = JsonPath.parse(created.body()).read("$.id");
		mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$unset", new Document("eventType", "")));
		Document legacy = bson(id);
		assertThat(legacy).doesNotContainKey("eventType");
		assertThat(events.findById(id).orElseThrow().getEventType()).isEqualTo(EventType.FIXED_DATE);
		var detail = send("GET", "/api/events/" + id, "", auth);
		assertThat(detail.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(detail.body()).read("$.eventType", String.class)).isEqualTo("FIXED_DATE");
		assertThat(send("GET", "/api/events", "", auth).statusCode()).isEqualTo(200);
		assertThat(bson(id)).isEqualTo(legacy);

		var edited = send("PUT", "/api/events/" + id, REQUEST.replace("Cena", "Cena editada"), auth);
		assertThat(edited.statusCode()).isEqualTo(200);
		assertThat(bson(id).getString("eventType")).isEqualTo("FIXED_DATE");
		assertThat(bson(id).getLong("version")).isEqualTo(legacy.getLong("version") + 1);
		var published = send("POST", "/api/events/" + id + "/publish", "", auth);
		assertThat(published.statusCode()).isEqualTo(200);
		String option = JsonPath.parse(published.body()).read("$.responseOptions[0].id");
		String joinToken = events.findById(id).orElseThrow().getPublicInvitation().token();
		var preview = send("GET", "/api/invitations/join/" + joinToken, "", Map.of());
		assertThat(preview.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(preview.body()).read("$.eventType", String.class)).isEqualTo("FIXED_DATE");

		var guest = send("POST", "/api/events/" + id + "/guests", "{\"name\":\"Ana\",\"email\":\"ana@example.com\"}", auth);
		assertThat(guest.statusCode()).isEqualTo(201);
		String invitationPath = JsonPath.parse(guest.body()).read("$.invitationPath");
		String route = "/api/invitations/" + invitationPath.substring("/r/".length());
		var invitation = send("GET", route, "", Map.of());
		assertThat(invitation.statusCode()).isEqualTo(200);
		var view = JsonPath.parse(invitation.body());
		assertThat(view.read("$.eventType", String.class)).isEqualTo("FIXED_DATE");
		assertThat(view.read("$.startsAtInstant", String.class)).isEqualTo("2027-03-28T00:30:00Z");
		assertThat(view.read("$.endsAtInstant", String.class)).isEqualTo("2027-03-28T02:30:00Z");
		assertThat(view.read("$.canRespond", Boolean.class)).isTrue();
		var answer = send("PUT", route + "/response", "{\"responseOptionId\":\"" + option + "\"}", Map.of("Content-Type", "application/json"));
		assertThat(answer.statusCode()).isEqualTo(200);
		var dashboard = send("GET", "/api/events/" + id + "/dashboard", "", auth);
		assertThat(dashboard.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(dashboard.body()).read("$.totals.attending", Integer.class)).isEqualTo(1);
		var calendar = send("GET", route + "/calendar", "", Map.of());
		assertThat(calendar.statusCode()).isEqualTo(200);
		assertThat(calendar.body()).contains("BEGIN:VCALENDAR", "Cena editada");
		assertThat(send("POST", "/api/events/" + id + "/cancel", "{}", auth).statusCode()).isEqualTo(200);
		assertThat(send("GET", route, "", Map.of()).statusCode()).isEqualTo(200);
		assertThat(send("GET", route + "/calendar", "", Map.of()).statusCode()).isEqualTo(409);
	}

	@Test void anUnsupportedStoredTypeIsNotSilentlyReinterpretedAsFixedDate() throws Exception {
		var created = send("POST", "/api/events", REQUEST, auth);
		assertThat(created.statusCode()).isEqualTo(201);
		String id = JsonPath.parse(created.body()).read("$.id");
		mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$set", new Document("eventType", "UNKNOWN_TYPE")));
		var detail = send("GET", "/api/events/" + id, "", auth);
		assertThat(detail.statusCode()).isEqualTo(500);
		assertThat(detail.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(detail.body()).doesNotContain("UNKNOWN_TYPE", "IllegalArgumentException");
	}

	private Document bson(String id) {
		return mongo.getCollection("events").find(new Document("_id", id)).first();
	}

	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		headers.forEach(request::header);
		return client.send(request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private String cookie(HttpResponse<?> response, String name) {
		return response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
				.filter(value -> value.getName().equals(name)).findFirst().orElseThrow().getValue();
	}
}
