package com.gtog.event.infrastructure.out.persistence;

import java.util.List;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.out.*;
import static com.gtog.event.GuestTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = { "app.auth.auto-verify-email=false", "app.guests.max-per-event=3" })
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GuestBatchPersistenceIntegrationTest {
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final EventGuestRepositoryPort guests;
	GuestBatchPersistenceIntegrationTest(MongoTemplate mongo, EventRepositoryPort events, EventGuestRepositoryPort guests) {
		this.mongo = mongo; this.events = events; this.guests = guests;
	}
	@BeforeEach void setUp() { mongo.remove(new Query(), EventDocument.class); events.save(event("batch-event", EventStatus.PUBLISHED, null, guest(1))); }
	private Event current() { return events.findById("batch-event").orElseThrow(); }
	private Document document() { return mongo.getCollection("events").find(new Document("_id", "batch-event")).first(); }
	@Test void pushesTheWholeBatchInOrderAndIncrementsVersionExactlyOnce() {
		long before = current().getVersion(); var first = guest(2); var second = guest(3);
		guests.addGuests("batch-event", "host-1", List.of(first, second));
		assertThat(current().getGuests()).extracting(Guest::getId).containsExactly(current().getGuests().getFirst().getId(), first.getId(), second.getId());
		assertThat(current().getVersion()).isEqualTo(before + 1);
	}
	@Test void duplicateRejectionLeavesTheEntireBsonAndVersionIdentical() {
		Document before = document();
		var duplicate = Guest.create("Duplicate", "guest1@example.com", null, token(99), phone -> phone);
		var exception = catchThrowableOfType(() -> guests.addGuests("batch-event", "host-1", List.of(guest(2), duplicate)), GuestBatchRejectedException.class);
		assertThat(exception.isConflict()).isTrue(); assertThat(exception.getErrors()).containsExactly(new GuestBatchError(1, GuestErrorCode.DUPLICATE_IN_EVENT));
		assertThat(document()).isEqualTo(before);
	}
	@Test void capacityRejectionLeavesTheEntireBsonAndVersionIdentical() {
		Document before = document();
		assertThatThrownBy(() -> guests.addGuests("batch-event", "host-1", List.of(guest(2), guest(3), guest(4)))).isInstanceOf(GuestCapacityExceededException.class);
		assertThat(document()).isEqualTo(before);
	}
	@Test void concurrentBatchesSharingAnEmailHaveExactlyOneSuccessAndOneConflict() throws Exception {
		var first = Guest.create("A", "shared@example.com", null, token(2), phone -> phone);
		var second = Guest.create("B", "shared@example.com", null, token(3), phone -> phone);
		assertThat(concurrently(() -> guests.addGuests("batch-event", "host-1", List.of(first)),
				() -> guests.addGuests("batch-event", "host-1", List.of(second)))).containsExactlyInAnyOrder(201, 409);
		assertThat(current().getGuests().stream().filter(guest -> "shared@example.com".equals(guest.getEmail())).count()).isEqualTo(1);
	}
	@Test void concurrentBatchesCannotExceedTheCapacity() throws Exception {
		assertThat(concurrently(() -> guests.addGuests("batch-event", "host-1", List.of(guest(2), guest(3))),
				() -> guests.addGuests("batch-event", "host-1", List.of(guest(4), guest(5))))).containsExactlyInAnyOrder(201, 409);
		assertThat(current().getGuests()).hasSize(3);
	}
	@Test void aBatchAndTheFirstOpeningPreserveBothWrites() throws Exception {
		var before = current(); var guest = before.getGuests().getFirst(); var now = java.time.Instant.parse("2027-01-01T10:00:00Z");
		assertThat(concurrently(() -> guests.addGuests("batch-event", "host-1", List.of(guest(2), guest(3))),
				() -> assertThat(guests.markGuestOpened("batch-event", guest.getToken(), now)).isPresent())).containsExactly(201, 201);
		assertThat(current().getGuests()).hasSize(3); assertThat(current().getGuest(guest.getId()).getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
		assertThat(current().getGuest(guest.getId()).getOpenedAt()).isEqualTo(now); assertThat(current().getVersion()).isEqualTo(before.getVersion() + 2);
	}
	@Test void eventsWithoutAGuestsArrayCanReceiveABatch() {
		mongo.updateFirst(Query.query(Criteria.where("id").is("batch-event")), new Update().unset("guests"), EventDocument.class);
		guests.addGuests("batch-event", "host-1", List.of(guest(2), guest(3))); assertThat(current().getGuests()).hasSize(2);
	}
	private List<Integer> concurrently(Runnable first, Runnable second) throws Exception {
		var barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var a = executor.submit(() -> attempt(barrier, first)); var b = executor.submit(() -> attempt(barrier, second));
			return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
		}
	}
	private int attempt(CyclicBarrier barrier, Runnable action) throws Exception {
		barrier.await(10, TimeUnit.SECONDS);
		try { action.run(); return 201; }
		catch (GuestBatchRejectedException exception) { assertThat(exception.isConflict()).isTrue(); return 409; }
		catch (GuestCapacityExceededException exception) { return 409; }
	}
}
