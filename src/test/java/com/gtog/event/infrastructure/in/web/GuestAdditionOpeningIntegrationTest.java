package com.gtog.event.infrastructure.in.web;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationStatus;
import com.gtog.event.domain.port.out.EventGuestRepositoryPort;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.user.infrastructure.out.persistence.UserDocument;

import static com.gtog.event.GuestTestFixtures.event;
import static com.gtog.event.GuestTestFixtures.guest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.auth.auto-verify-email=false")
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GuestAdditionOpeningIntegrationTest {
	private final int port;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final PasswordEncoder passwords;
	private final HttpClient client = HttpClient.newHttpClient();
	@MockitoSpyBean private EventGuestRepositoryPort guests;

	GuestAdditionOpeningIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo,
			EventRepositoryPort events, PasswordEncoder passwords) {
		this.port = port;
		this.mongo = mongo;
		this.events = events;
		this.passwords = passwords;
	}

	@BeforeEach
	void setUp() {
		mongo.remove(new Query(), EventDocument.class);
		mongo.remove(new Query(), UserDocument.class);
		mongo.insert(com.gtog.UserTestFixtures.verified("host-1", "Host", "opening-host@example.com",
				passwords.encode("password12"), "Europe/Madrid"));
		events.save(event("opening-event", EventStatus.PUBLISHED, null, guest(1)));
	}

	@Test
	void addingAGuestSucceedsWhenAnotherGuestOpensBetweenReadingAndWriting() throws Exception {
		var before = events.findById("opening-event").orElseThrow();
		var existing = before.getGuests().getFirst();
		String csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var login = send("POST", "/api/auth/login",
				"{\"email\":\"opening-host@example.com\",\"password\":\"password12\"}",
				Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf));
		assertThat(login.statusCode()).isEqualTo(200);
		String session = cookie(login, "JSESSIONID");

		// La lectura del caso de uso ya termino. Forzar ahora una apertura real y esperar su escritura.
		doAnswer(invocation -> {
			var opened = send("GET", "/api/invitations/" + existing.getToken(), "", Map.of());
			assertThat(opened.statusCode()).isEqualTo(200);
			assertThat(events.findById("opening-event").orElseThrow().getVersion()).isEqualTo(before.getVersion() + 1);
			return invocation.callRealMethod();
		}).when(guests).addGuest(eq("opening-event"), eq("host-1"), anyLong(), any(Guest.class));

		var added = send("POST", "/api/events/opening-event/guests",
				"{\"name\":\"New guest\",\"email\":\"new-opening@example.com\"}",
				Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + session,
						"X-XSRF-TOKEN", csrf));
		assertThat(added.statusCode()).as("El alta debe conservar la apertura y añadir al otro invitado").isEqualTo(201);
		var after = events.findById("opening-event").orElseThrow();
		assertThat(after.getGuests()).hasSize(2);
		assertThat(after.getGuest(existing.getId()).getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
		assertThat(after.getGuest(existing.getId()).getOpenedAt()).isNotNull();
		assertThat(after.getGuests().getLast().getEmail()).isEqualTo("new-opening@example.com");
		assertThat(after.getVersion()).isEqualTo(before.getVersion() + 2);
	}

	@Test
	void individualAdditionAt500GuestsReturnsCapacityExceededWithoutWriting() throws Exception {
		mongo.remove(new Query(), EventDocument.class);
		Guest[] seeded = java.util.stream.IntStream.rangeClosed(1, 500).mapToObj(com.gtog.event.GuestTestFixtures::guest).toArray(Guest[]::new);
		events.save(event("opening-event", EventStatus.PUBLISHED, null, seeded));
		var before = mongo.getCollection("events").find(new org.bson.Document("_id", "opening-event")).first();
		String csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var login = send("POST", "/api/auth/login", "{\"email\":\"opening-host@example.com\",\"password\":\"password12\"}",
				Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf));
		assertThat(login.statusCode()).isEqualTo(200);
		var response = send("POST", "/api/events/opening-event/guests", "{\"name\":\"New\",\"email\":\"new@example.com\"}",
				Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(login, "JSESSIONID"), "X-XSRF-TOKEN", csrf));
		assertThat(response.statusCode()).isEqualTo(409);
		assertThat(com.jayway.jsonpath.JsonPath.parse(response.body()).read("$.code", String.class)).isEqualTo("CAPACITY_EXCEEDED");
		assertThat(mongo.getCollection("events").find(new org.bson.Document("_id", "opening-event")).first()).isEqualTo(before);
	}

	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		headers.forEach(request::header);
		return client.send(request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private String cookie(HttpResponse<?> response, String name) {
		return response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
				.filter(cookie -> cookie.getName().equals(name)).findFirst().orElseThrow().getValue();
	}
}
