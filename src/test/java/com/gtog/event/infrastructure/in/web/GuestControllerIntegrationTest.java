package com.gtog.event.infrastructure.in.web;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import com.gtog.event.domain.model.DeliveryChannel;
import com.gtog.event.domain.model.DuplicateGuestTokenException;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventConcurrentModificationException;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationStatus;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.EventGuestRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.event.infrastructure.out.persistence.EventMongoRepository;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.gtog.user.infrastructure.out.persistence.UserMongoRepository;

import static com.gtog.event.GuestTestFixtures.event;
import static com.gtog.event.GuestTestFixtures.guest;
import static com.gtog.event.GuestTestFixtures.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@com.gtog.AuthBypassDisabled
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@WithUserDetails(value = "host@example.com", setupBefore = TestExecutionEvent.TEST_EXECUTION)
class GuestControllerIntegrationTest {

	private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode("password12");
	private static final String BASE = "/api/events/event-1/guests";

	private final MockMvc mockMvc;
	private final EventRepositoryPort events;
	private final EventGuestRepositoryPort guests;
	private final EventMongoRepository eventDocuments;
	private final UserMongoRepository users;
	private final MongoTemplate mongoTemplate;

	GuestControllerIntegrationTest(MockMvc mockMvc, EventRepositoryPort events, EventGuestRepositoryPort guests,
			EventMongoRepository eventDocuments, UserMongoRepository users, MongoTemplate mongoTemplate) {
		this.mockMvc = mockMvc;
		this.events = events;
		this.guests = guests;
		this.eventDocuments = eventDocuments;
		this.users = users;
		this.mongoTemplate = mongoTemplate;
	}

	@BeforeEach
	void setUp() {
		eventDocuments.deleteAll();
		mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(),
				com.gtog.event.infrastructure.out.persistence.GuestResponseDocument.class);
		users.deleteAll();
		for (int i = 1; i <= 2; i++) {
			UserDocument host = new UserDocument();
			host.setId("host-" + i);
			host.setEmail(i == 1 ? "host@example.com" : "other@example.com");
			host.setName("Host " + i);
			host.setPasswordHash(PASSWORD_HASH);
			host.setTimeZone("Europe/Madrid");
		host.setEmailVerified(true);
			users.save(host);
		}
		events.save(event("event-1", EventStatus.DRAFT, null));
	}

	@Test
	void createsAndListsAnInvitationLinkWithoutExposingARawToken() throws Exception {
		String result = mockMvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact(" Ana ", " ANA@Example.COM ", "+34 612 345 678")))
				.andExpect(status().isCreated()).andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.name").value("Ana"))
				.andExpect(jsonPath("$.email").value("ana@example.com"))
				.andExpect(jsonPath("$.phoneE164").value("+34612345678"))
				.andExpect(jsonPath("$.invitationStatus").value("NOT_SENT"))
				.andExpect(jsonPath("$.deliveryChannel").isEmpty())
				.andExpect(jsonPath("$.sentAt").isEmpty()).andExpect(jsonPath("$.openedAt").isEmpty())
				.andExpect(jsonPath("$.token").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(result, "$.id");
		String path = JsonPath.read(result, "$.invitationPath");
		Guest stored = events.findById("event-1").orElseThrow().getGuest(id);
		assertThat(stored.getToken()).matches("[A-Za-z0-9_-]{43}");
		assertThat(path).isEqualTo("/r/" + stored.getToken());
		mockMvc.perform(get(BASE)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].invitationPath").value(path)).andExpect(jsonPath("$[0].token").doesNotExist());
		String detail = mockMvc.perform(get("/api/events/event-1")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String summaries = mockMvc.perform(get("/api/events")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(detail).doesNotContain(stored.getToken(), "invitationPath");
		assertThat(summaries).doesNotContain(stored.getToken(), "invitationPath");
	}

	@Test
	void editsPositionallyWithoutChangingTokenOrDeliveryHistoryAndThenDeletes() throws Exception {
		Guest original = Guest.reconstitute("guest-1", "Ana", "ana@example.com", null, token(1),
				InvitationStatus.OPENED, DeliveryChannel.EMAIL, Instant.parse("2026-11-14T09:00:00Z"),
				Instant.parse("2026-11-14T10:00:00Z"));
		Event seeded = events.findById("event-1").orElseThrow();
		seeded.addGuest(original);
		seeded.addGuest(guest(2));
		events.save(seeded);
		Long version = events.findById("event-1").orElseThrow().getVersion();
		mockMvc.perform(put(BASE + "/guest-1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Ana Maria", null, "+34 612 345 678")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Ana Maria"))
				.andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.invitationPath").doesNotExist());
		Event edited = events.findById("event-1").orElseThrow();
		Guest stored = edited.getGuest("guest-1");
		assertThat(stored.getToken()).isEqualTo(original.getToken());
		assertThat(stored.getEmail()).isNull();
		assertThat(stored.getPhoneE164()).isEqualTo("+34612345678");
		assertThat(stored.getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
		assertThat(stored.getDeliveryChannel()).isEqualTo(DeliveryChannel.EMAIL);
		assertThat(stored.getSentAt()).isEqualTo(original.getSentAt());
		assertThat(stored.getOpenedAt()).isEqualTo(original.getOpenedAt());
		assertThat(edited.getVersion()).isEqualTo(version + 1);
		mockMvc.perform(delete(BASE + "/guest-1").with(csrf())).andExpect(status().isNoContent());
		Event removed = events.findById("event-1").orElseThrow();
		assertThat(removed.getGuests()).extracting(Guest::getEmail).containsExactly("guest2@example.com");
		assertThat(removed.getVersion()).isEqualTo(version + 2);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"name\":\"\",\"email\":\"ana@example.com\"}",
			"{\"name\":\"Ana\"}",
			"{\"name\":\"Ana\",\"email\":\"invalid\",\"phone\":\"+34612345678\"}",
			"{\"name\":\"Ana\",\"phone\":\"612345678\"}",
			"{\"name\":\"Ana\",\"phone\":\"+34123\"}",
			"{\"name\":\"Ana\",\"phone\":\"+34612345678 ext. 12\"}"
	})
	void rejectsInvalidGuestDataThroughTheDomain(String body) throws Exception {
		mockMvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.status").value(422));
		assertThat(events.findById("event-1").orElseThrow().getGuests()).isEmpty();
	}

	@Test
	void rejectsDuplicateEmailAndPhoneAfterNormalization() throws Exception {
		addThroughHttp("Ana", "ana@example.com", "+34612345678");
		mockMvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Other", " ANA@EXAMPLE.COM ", null))).andExpect(status().isConflict());
		mockMvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Other", "other@example.com", "+34 612 345 678"))).andExpect(status().isConflict());
		assertThat(events.findById("event-1").orElseThrow().getGuests()).hasSize(1);
	}

	@Test
	void validatesContactsAndDuplicatesWhenEditing() throws Exception {
		String first = addThroughHttp("Ana", "ana@example.com", null);
		addThroughHttp("Other", "other@example.com", null);
		mockMvc.perform(put(BASE + "/" + first).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Ana", "other@example.com", null))).andExpect(status().isConflict());
		mockMvc.perform(put(BASE + "/" + first).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Ana", null, null))).andExpect(status().isUnprocessableEntity());
		mockMvc.perform(put(BASE + "/" + first).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Changed", " ANA@EXAMPLE.COM ", null))).andExpect(status().isOk());
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"DRAFT", "PUBLISHED"})
	void permitsAllGuestOperationsWhileTheEventIsOpen(EventStatus status) throws Exception {
		changeStatus(status);
		String id = addThroughHttp("Ana", "ana@example.com", null);
		mockMvc.perform(put(BASE + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Changed", "ana@example.com", null))).andExpect(status().isOk());
		mockMvc.perform(delete(BASE + "/" + id).with(csrf()).param("confirmDeleteResponse", "true"))
				.andExpect(status().isNoContent());
		mockMvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(content().json("[]"));
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"FINISHED", "CANCELLED"})
	void blocksAllMutationsButAllowsListingInClosedEvents(EventStatus status) throws Exception {
		String id = addThroughHttp("Ana", "ana@example.com", null);
		changeStatus(status);
		mockMvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Other", "other@example.com", null))).andExpect(status().isConflict());
		mockMvc.perform(put(BASE + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Changed", "ana@example.com", null))).andExpect(status().isConflict());
		mockMvc.perform(delete(BASE + "/" + id).with(csrf())).andExpect(status().isConflict());
		mockMvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	@WithUserDetails(value = "other@example.com", setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void concealsOtherHostsEventsFromAllFourEndpoints() throws Exception {
		assertFourEndpointsReturn404(BASE, "missing");
	}

	@Test
	void returns404ForUnknownEventsAndGuests() throws Exception {
		assertFourEndpointsReturn404("/api/events/missing/guests", "missing");
		mockMvc.perform(put(BASE + "/missing").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Ana", "ana@example.com", null))).andExpect(status().isNotFound());
		mockMvc.perform(delete(BASE + "/missing").with(csrf())).andExpect(status().isNotFound());
	}

	@Test
	@WithAnonymousUser
	void requiresAuthenticationForAllFourEndpoints() throws Exception {
		mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Ana", "ana@example.com", null))).andExpect(status().isUnauthorized());
		mockMvc.perform(put(BASE + "/missing").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Ana", "ana@example.com", null))).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(BASE + "/missing").with(csrf())).andExpect(status().isUnauthorized());
	}

	@Test
	void requiresCsrfForAllGuestWrites() throws Exception {
		mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Ana", "ana@example.com", null))).andExpect(status().isForbidden());
		mockMvc.perform(put(BASE + "/missing").contentType(MediaType.APPLICATION_JSON)
				.content(contact("Ana", "ana@example.com", null))).andExpect(status().isForbidden());
		mockMvc.perform(delete(BASE + "/missing")).andExpect(status().isForbidden());
	}

	@Test
	void uniqueIndexRejectsTheSameTokenAcrossEventsWithoutLeakingItInTheError() {
		events.save(event("event-2", EventStatus.DRAFT, null));
		Guest first = guest(1);
		guests.addGuest("event-1", "host-1", 0L, first);
		Guest collision = Guest.create("Other", "other@example.com", null, first.getToken(), phone -> phone);
		assertThatThrownBy(() -> guests.addGuest("event-2", "host-1", 0L, collision))
				.isInstanceOf(DuplicateGuestTokenException.class).hasMessageNotContaining(first.getToken());
		assertThat(events.findById("event-2").orElseThrow().getGuests()).isEmpty();
	}

	@Test
	void concurrentSameTokenInsertionsInOneEventCannotBothSucceed() throws Exception {
		Guest first = guest(1);
		Guest second = Guest.create("Other", "other@example.com", null, first.getToken(), phone -> phone);
		List<Boolean> results = concurrently(
				() -> guests.addGuest("event-1", "host-1", 0L, first),
				() -> guests.addGuest("event-1", "host-1", 0L, second));
		assertThat(results).containsExactlyInAnyOrder(true, false);
		assertThat(events.findById("event-1").orElseThrow().getGuests()).hasSize(1);
	}

	@Test
	void concurrentDuplicateContactsCannotBothBeInserted() throws Exception {
		Guest first = guest(1);
		Guest second = Guest.create("Other", "GUEST1@EXAMPLE.COM", null, token(2), phone -> phone);
		List<Boolean> results = concurrently(
				() -> guests.addGuest("event-1", "host-1", 0L, first),
				() -> guests.addGuest("event-1", "host-1", 0L, second));
		assertThat(results).containsExactlyInAnyOrder(true, false);
		assertThat(events.findById("event-1").orElseThrow().getGuests()).hasSize(1);
	}

	@Test
	void concurrentEditsOfDifferentGuestsNeitherBlockNorOverwriteEachOther() throws Exception {
		// El 409 anterior dependia de la version esperada del evento; no era una regla de negocio.
		Guest first = guest(1);
		Guest second = guest(2);
		Event seeded = events.findById("event-1").orElseThrow();
		seeded.addGuest(first);
		seeded.addGuest(second);
		Event stored = events.save(seeded);
		var principal = new com.gtog.user.infrastructure.out.security.AuthenticatedUser(
				"host-1", "Host", "host@example.com", PASSWORD_HASH, "Europe/Madrid", true);
		CyclicBarrier barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var a = executor.submit(() -> {
				barrier.await(10, TimeUnit.SECONDS);
				return mockMvc.perform(put(BASE + "/" + first.getId())
						.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(principal))
						.with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(contact("Changed 1", "changed1@example.com", "+34612345678"))).andReturn().getResponse().getStatus();
			});
			var b = executor.submit(() -> {
				barrier.await(10, TimeUnit.SECONDS);
				return mockMvc.perform(put(BASE + "/" + second.getId())
						.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(principal))
						.with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(contact("Changed 2", "changed2@example.com", "+34698765432"))).andReturn().getResponse().getStatus();
			});
			assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactly(200, 200);
		}
		Event finalEvent = events.findById("event-1").orElseThrow();
		assertThat(finalEvent.getGuests()).extracting(Guest::getId).containsExactly(first.getId(), second.getId());
		assertThat(finalEvent.getGuests()).extracting(Guest::getName).containsExactly("Changed 1", "Changed 2");
		assertThat(finalEvent.getGuests()).extracting(Guest::getToken).containsExactly(first.getToken(), second.getToken());
		assertThat(finalEvent.getGuest(first.getId()).getEmail()).isEqualTo("changed1@example.com");
		assertThat(finalEvent.getGuest(first.getId()).getPhoneE164()).isEqualTo("+34612345678");
		assertThat(finalEvent.getGuest(second.getId()).getEmail()).isEqualTo("changed2@example.com");
		assertThat(finalEvent.getGuest(second.getId()).getPhoneE164()).isEqualTo("+34698765432");
		assertThat(finalEvent.getVersion()).isEqualTo(stored.getVersion() + 2);
	}

	@Test
	void staleFullEventSavesCannotEraseAnAtomicGuestWrite() {
		Event stale = events.findById("event-1").orElseThrow();
		guests.addGuest("event-1", "host-1", stale.getVersion(), guest(1));
		assertThatThrownBy(() -> events.save(stale)).isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(events.findById("event-1").orElseThrow().getGuests()).hasSize(1);
	}

	@Test
	void legacyEventsWithoutAGuestsFieldRemainReadableAndCanReceiveGuests() throws Exception {
		mongoTemplate.updateFirst(Query.query(where("id").is("event-1")), new Update().unset("guests"), EventDocument.class);
		assertThat(events.findById("event-1").orElseThrow().getGuests()).isEmpty();
		mockMvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(content().json("[]"));
		addThroughHttp("Ana", null, "+34612345678");
		assertThat(events.findById("event-1").orElseThrow().getGuests()).hasSize(1);
	}

	@Test
	void documentsTheFourGuestEndpointsAndTheirResponseSchemas() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/events/{id}/guests'].post.responses['201']").exists())
				.andExpect(jsonPath("$.paths['/api/events/{id}/guests'].get.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/api/events/{id}/guests/{guestId}'].put.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/api/events/{id}/guests/{guestId}'].delete.responses['204']").exists())
				.andExpect(jsonPath("$.components.schemas.GuestInvitationResponse.properties.invitationPath").exists())
				.andExpect(jsonPath("$.components.schemas.GuestResponse.properties.token").doesNotExist());
	}

	private void assertFourEndpointsReturn404(String base, String guestId) throws Exception {
		mockMvc.perform(get(base)).andExpect(status().isNotFound());
		mockMvc.perform(post(base).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Ana", "ana@example.com", null))).andExpect(status().isNotFound());
		mockMvc.perform(put(base + "/" + guestId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact("Ana", "ana@example.com", null))).andExpect(status().isNotFound());
		mockMvc.perform(delete(base + "/" + guestId).with(csrf())).andExpect(status().isNotFound());
	}

	private String addThroughHttp(String name, String email, String phone) throws Exception {
		String result = mockMvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(contact(name, email, phone))).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(result, "$.id");
	}

	private void changeStatus(EventStatus status) {
		mongoTemplate.updateFirst(Query.query(where("id").is("event-1")), new Update().set("status", status.name()), EventDocument.class);
	}

	private String contact(String name, String email, String phone) {
		return "{\"name\":\"%s\",\"email\":%s,\"phone\":%s}".formatted(name,
				email == null ? "null" : "\"" + email + "\"", phone == null ? "null" : "\"" + phone + "\"");
	}

	private List<Boolean> concurrently(Runnable first, Runnable second) throws Exception {
		CyclicBarrier barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			Callable<Boolean> runFirst = () -> attemptAfterBarrier(barrier, first);
			Callable<Boolean> runSecond = () -> attemptAfterBarrier(barrier, second);
			var a = executor.submit(runFirst);
			var b = executor.submit(runSecond);
			return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
		}
	}

	private boolean attemptAfterBarrier(CyclicBarrier barrier, Runnable operation) throws Exception {
		barrier.await(10, TimeUnit.SECONDS);
		try {
			operation.run();
			return true;
		} catch (EventConcurrentModificationException e) {
			return false;
		}
	}
}
