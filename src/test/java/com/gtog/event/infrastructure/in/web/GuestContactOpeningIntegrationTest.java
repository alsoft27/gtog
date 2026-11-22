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
class GuestContactOpeningIntegrationTest {
	private final int port;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final PasswordEncoder passwords;
	private final HttpClient client = HttpClient.newHttpClient();
	@MockitoSpyBean private EventGuestRepositoryPort guests;
	@MockitoSpyBean private MongoTemplate writes;

	GuestContactOpeningIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo,
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
		mongo.remove(new Query(), com.gtog.event.infrastructure.out.persistence.GuestResponseDocument.class);
		mongo.insert(com.gtog.UserTestFixtures.verified("host-1", "Host", "opening-host@example.com",
				passwords.encode("password12"), "Europe/Madrid"));
		events.save(event("opening-event", EventStatus.PUBLISHED, null, guest(1), guest(2)));
	}

	@Test
	void editingAGuestSucceedsWhenAnotherGuestOpensBetweenReadingAndWriting() throws Exception {
		var before = events.findById("opening-event").orElseThrow();
		var target = before.getGuests().get(1);
		doAnswer(openingBeforeWrite(before.getVersion(), before.getGuests().getFirst().getToken()))
				.when(guests).updateGuest(eq("opening-event"), eq("host-1"), anyLong(), any(Guest.class));
		var result = send("PUT", "/api/events/opening-event/guests/" + target.getId(),
				"{\"name\":\"Changed\",\"email\":\"" + target.getEmail() + "\"}", authenticated());
		assertThat(result.statusCode()).as("La apertura de otro invitado no debe impedir la edición").isEqualTo(200);
		var after = events.findById("opening-event").orElseThrow();
		assertThat(after.getGuest(target.getId()).getName()).isEqualTo("Changed");
		assertThat(after.getGuests().getFirst().getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
		assertThat(after.getGuests().getFirst().getOpenedAt()).isNotNull();
		assertThat(after.getVersion()).isEqualTo(before.getVersion() + 2);
	}

	@Test
	void deletingAGuestSucceedsWhenAnotherGuestOpensBetweenReadingAndWriting() throws Exception {
		var before = events.findById("opening-event").orElseThrow();
		var target = before.getGuests().get(1);
		doAnswer(openingBeforeWrite(before.getVersion(), before.getGuests().getFirst().getToken()))
				.when(guests).removeGuest(eq("opening-event"), eq("host-1"), anyLong(), eq(target.getId()));
		var result = send("DELETE", "/api/events/opening-event/guests/" + target.getId(), "", authenticated());
		assertThat(result.statusCode()).as("La apertura de otro invitado no debe impedir el borrado").isEqualTo(204);
		var after = events.findById("opening-event").orElseThrow();
		assertThat(after.getGuests()).hasSize(1);
		assertThat(after.getGuests().getFirst().getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
		assertThat(after.getGuests().getFirst().getOpenedAt()).isNotNull();
		assertThat(after.getVersion()).isEqualTo(before.getVersion() + 2);
	}

	@Test
	void concurrentEditsToTheSameEmailHaveOne200AndOne409() throws Exception {
		var before = events.findById("opening-event").orElseThrow();
		var headers = authenticated();
		var barrier = new java.util.concurrent.CyclicBarrier(2);
		doAnswer(invocation -> { barrier.await(10, java.util.concurrent.TimeUnit.SECONDS); return invocation.callRealMethod(); })
				.when(guests).updateGuest(eq("opening-event"), eq("host-1"), anyLong(), any(Guest.class));
		try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> send("PUT", "/api/events/opening-event/guests/" + before.getGuests().getFirst().getId(),
					"{\"name\":\"First\",\"email\":\"shared@example.com\"}", headers));
			var second = executor.submit(() -> send("PUT", "/api/events/opening-event/guests/" + before.getGuests().getLast().getId(),
					"{\"name\":\"Second\",\"email\":\"SHARED@EXAMPLE.COM\"}", headers));
			assertThat(java.util.List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS).statusCode(),
					second.get(20, java.util.concurrent.TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200, 409);
		}
		var after = events.findById("opening-event").orElseThrow();
		assertThat(after.getGuests().stream().filter(guest -> "shared@example.com".equals(guest.getEmail())).count()).isEqualTo(1);
		assertThat(after.getVersion()).isEqualTo(before.getVersion() + 1);
	}

	@Test
	void editingTheSameGuestTwiceUsesTheLastWrite() {
		var before = events.findById("opening-event").orElseThrow(); var guest = before.getGuests().getLast();
		guests.updateGuest(before.getId(), before.getHostId(), before.getVersion(), guest.editContact("First", guest.getEmail(), null, phone -> phone));
		guests.updateGuest(before.getId(), before.getHostId(), before.getVersion(), guest.editContact("Last", guest.getEmail(), null, phone -> phone));
		var after = events.findById(before.getId()).orElseThrow();
		assertThat(after.getGuest(guest.getId()).getName()).isEqualTo("Last");
		assertThat(after.getVersion()).isEqualTo(before.getVersion() + 2);
	}

	@Test
	void editingWithoutChangingEmailPreservesAConcurrentSendReservation() {
		var before = events.findById("opening-event").orElseThrow(); var target = before.getGuests().getFirst();
		mongo.updateFirst(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("id").is(before.getId()).and("guests.id").is(target.getId())),
				new org.springframework.data.mongodb.core.query.Update().set("guests.$.emailSendCount", 3)
						.set("guests.$.lastEmailSentAt", java.time.Instant.EPOCH).inc("version", 1), EventDocument.class);
		guests.updateGuest(before.getId(), before.getHostId(), before.getVersion(), target.editContact("Changed", target.getEmail(), null, phone -> phone));
		var current = events.findById(before.getId()).orElseThrow().getGuest(target.getId());
		assertThat(current.getEmailSendCount()).isEqualTo(3); assertThat(current.getLastEmailSentAt()).isEqualTo(java.time.Instant.EPOCH);
	}

	@Test
	void aGuestRemovedAfterTheReadReturns404WithoutRunningTheCascade() throws Exception {
		var before = events.findById("opening-event").orElseThrow(); var target = before.getGuests().getLast();
		doAnswer(invocation -> {
			mongo.updateFirst(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("id").is(before.getId())),
					new org.springframework.data.mongodb.core.query.Update().pull("guests", new org.bson.Document("id", target.getId())).inc("version", 1), EventDocument.class);
			insertResponse(before, target);
			return invocation.callRealMethod();
		}).when(guests).removeGuest(eq(before.getId()), eq(before.getHostId()), anyLong(), eq(target.getId()));
		assertThat(send("DELETE", "/api/events/opening-event/guests/" + target.getId(), "", authenticated()).statusCode()).isEqualTo(404);
		assertThat(mongo.getCollection("responses").countDocuments(new org.bson.Document("guestToken", target.getToken()))).isEqualTo(1);
	}

	@Test
	void aResponseArrivingAfterTheCheckIsDeletedAfterTheGuestWithoutConfirmation() throws Exception {
		var before = events.findById("opening-event").orElseThrow(); var target = before.getGuests().getLast();
		doAnswer(invocation -> { insertResponse(before, target); return invocation.callRealMethod(); })
				.when(guests).removeGuest(eq(before.getId()), eq(before.getHostId()), anyLong(), eq(target.getId()));
		assertThat(send("DELETE", "/api/events/opening-event/guests/" + target.getId(), "", authenticated()).statusCode()).isEqualTo(204);
		assertThat(events.findById(before.getId()).orElseThrow().getGuests()).noneMatch(guest -> guest.getId().equals(target.getId()));
		assertThat(mongo.getCollection("responses").countDocuments(new org.bson.Document("guestToken", target.getToken()))).isZero();
	}

	@Test
	void aMatchedButUnmodifiedUpdateDoesNotReturn200() throws Exception {
		var before = events.findById("opening-event").orElseThrow(); var guest = before.getGuests().getLast();
		var document = mongo.getCollection("events").find(new org.bson.Document("_id", before.getId())).first();
		org.mockito.Mockito.doReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 0L, null)).when(writes)
				.updateFirst(any(Query.class), any(org.springframework.data.mongodb.core.query.Update.class), eq(EventDocument.class));
		var result = send("PUT", "/api/events/opening-event/guests/" + guest.getId(),
				"{\"name\":\"Changed\",\"email\":\"" + guest.getEmail() + "\"}", authenticated());
		assertThat(result.statusCode()).isEqualTo(409);
		assertThat(mongo.getCollection("events").find(new org.bson.Document("_id", before.getId())).first()).isEqualTo(document);
	}

	@Test
	void editingAGuestRemovedAfterTheReadReturns404() throws Exception {
		var before = events.findById("opening-event").orElseThrow(); var target = before.getGuests().getLast();
		doAnswer(invocation -> {
			mongo.updateFirst(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("id").is(before.getId())),
					new org.springframework.data.mongodb.core.query.Update().pull("guests", new org.bson.Document("id", target.getId())).inc("version", 1), EventDocument.class);
			return invocation.callRealMethod();
		}).when(guests).updateGuest(eq(before.getId()), eq(before.getHostId()), anyLong(), any(Guest.class));
		assertThat(send("PUT", "/api/events/opening-event/guests/" + target.getId(),
				"{\"name\":\"Changed\",\"email\":\"" + target.getEmail() + "\"}", authenticated()).statusCode()).isEqualTo(404);
	}

	@Test
	void concurrentEditsReturningToTheOldEmailResetTheCurrentReservation() throws Exception {
		concurrentEditsOfOneGuestUseTheCurrentEmail(true);
	}

	@Test
	void concurrentEditsKeepingTheNewEmailPreserveTheCurrentReservation() throws Exception {
		concurrentEditsOfOneGuestUseTheCurrentEmail(false);
	}

	private void concurrentEditsOfOneGuestUseTheCurrentEmail(boolean returnToOld) throws Exception {
		var before = events.findById("opening-event").orElseThrow(); var target = before.getGuests().getLast();
		var headers = authenticated(); var barrier = new java.util.concurrent.CyclicBarrier(2);
		var firstWritten = new java.util.concurrent.CountDownLatch(1);
		doAnswer(invocation -> {
			Guest replacement = invocation.getArgument(3);
			barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
			if (replacement.getName().equals("First")) {
				try {
					Object result = invocation.callRealMethod();
					mongo.updateFirst(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("id").is(before.getId()).and("guests.id").is(target.getId())),
							new org.springframework.data.mongodb.core.query.Update().set("guests.$.emailSendCount", 2)
									.set("guests.$.lastEmailSentAt", java.time.Instant.EPOCH).inc("version", 1), EventDocument.class);
					return result;
				} finally { firstWritten.countDown(); }
			}
			assertThat(firstWritten.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
			return invocation.callRealMethod();
		}).when(guests).updateGuest(eq(before.getId()), eq(before.getHostId()), anyLong(), any(Guest.class));
		String lastEmail = returnToOld ? target.getEmail() : "new-current@example.com";
		try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> send("PUT", "/api/events/opening-event/guests/" + target.getId(),
					"{\"name\":\"First\",\"email\":\"new-current@example.com\"}", headers));
			var last = executor.submit(() -> send("PUT", "/api/events/opening-event/guests/" + target.getId(),
					"{\"name\":\"Last\",\"email\":\"" + lastEmail + "\"}", headers));
			assertThat(java.util.List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS).statusCode(),
					last.get(20, java.util.concurrent.TimeUnit.SECONDS).statusCode())).containsExactly(200, 200);
		}
		var after = events.findById(before.getId()).orElseThrow(); var stored = after.getGuest(target.getId());
		assertThat(stored.getName()).isEqualTo("Last"); assertThat(stored.getEmail()).isEqualTo(lastEmail);
		assertThat(stored.getEmailSendCount()).isEqualTo(returnToOld ? 0 : 2);
		assertThat(stored.getLastEmailSentAt()).isEqualTo(returnToOld ? null : java.time.Instant.EPOCH);
		assertThat(after.getVersion()).isEqualTo(before.getVersion() + 3);
	}

	private void insertResponse(com.gtog.event.domain.model.Event event, Guest guest) {
		mongo.insert(new com.gtog.event.infrastructure.out.persistence.GuestResponseDocument(event.getId(), guest.getToken(),
				event.getResponseOptions().getFirst().id(), null, null, java.time.Instant.EPOCH, java.time.Instant.EPOCH));
	}

	private org.mockito.stubbing.Answer<Object> openingBeforeWrite(long version, String token) {
		return invocation -> {
			var opened = send("GET", "/api/invitations/" + token, "", Map.of());
			assertThat(opened.statusCode()).isEqualTo(200);
			assertThat(events.findById("opening-event").orElseThrow().getVersion()).isEqualTo(version + 1);
			return invocation.callRealMethod();
		};
	}

	private Map<String, String> authenticated() throws Exception {
		String csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var login = send("POST", "/api/auth/login",
				"{\"email\":\"opening-host@example.com\",\"password\":\"password12\"}",
				Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf));
		assertThat(login.statusCode()).isEqualTo(200);
		return Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(login, "JSESSIONID"),
				"X-XSRF-TOKEN", csrf);
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
