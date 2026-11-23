package com.gtog.event.infrastructure.out.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.gtog.event.application.PublicInvitationService;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.*;
import com.gtog.event.domain.port.out.*;
import static com.gtog.event.GuestTestFixtures.token;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "app.auth.auto-verify-email=false")
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PublicInvitationPublicationIntegrationTest {
	private static final Instant NOW = Instant.parse("2027-01-01T10:00:00Z");
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final PublishEventUseCase publisher;
	private final PublicInvitationService intentions;
	@MockitoBean private Clock clock;
	@MockitoSpyBean private PublicInvitationTokenGeneratorPort tokens;
	@MockitoSpyBean private PublicInvitationRepositoryAdapter links;
	@MockitoSpyBean private EventRepositoryAdapter eventWrites;
	PublicInvitationPublicationIntegrationTest(MongoTemplate mongo, EventRepositoryPort events, PublishEventUseCase publisher, PublicInvitationService intentions) {
		this.mongo = mongo; this.events = events; this.publisher = publisher; this.intentions = intentions;
	}
	@BeforeEach void setUp() { mongo.remove(new Query(), EventDocument.class); when(clock.instant()).thenReturn(NOW); }
	private Event draft(boolean enabled) {
		return events.save(Event.builder().hostId("host-1").title("Evento").startsAt(LocalDateTime.of(2027, 1, 1, 18, 0))
				.endsAt(LocalDateTime.of(2027, 1, 1, 20, 0)).timeZone("Europe/Madrid").modality(Modality.IN_PERSON)
				.venue(new Venue("Sala", "Dirección", 0.0, 0.0, "place", null)).publicInvitationEnabled(enabled).build());
	}
	private Event current(String id) { return events.findById(id).orElseThrow(); }
	private Document bson(String id) { return mongo.getCollection("events").find(new Document("_id", id)).first(); }
	@Test void draftCreationAndSwitchingNeverPersistATokenOrCallTheGenerator() {
		Event event = draft(true); clearInvocations(tokens);
		assertThat(bson(event.getId()).get("publicInvitation", Document.class)).containsEntry("enabled", true).doesNotContainKey("token");
		intentions.change("host-1", event.getId(), false); intentions.change("host-1", event.getId(), true);
		assertThat(bson(event.getId()).get("publicInvitation", Document.class)).containsEntry("enabled", true).doesNotContainKey("token");
		verifyNoInteractions(tokens); assertThat(current(event.getId()).getVersion()).isEqualTo(event.getVersion() + 2);
	}
	@Test void publicationStoresTheGeneratedTokenAndStatusInOneVersionedSave() {
		Event event = draft(true); doReturn(token(5000)).when(tokens).generate(); clearInvocations(eventWrites, links);
		publisher.publish("host-1", event.getId());
		Document after = bson(event.getId()); assertThat(after.getString("status")).isEqualTo("PUBLISHED");
		assertThat(after.get("publicInvitation", Document.class)).containsEntry("enabled", true).containsEntry("token", token(5000));
		assertThat(current(event.getId()).getVersion()).isEqualTo(event.getVersion() + 1);
		verify(eventWrites, times(1)).save(argThat(value -> value.getStatus() == EventStatus.PUBLISHED && token(5000).equals(value.getPublicInvitation().token())));
		verify(links, never()).change(any(), any());
	}
	@Test void publicationWithoutIntentionDoesNotGenerateOrPersistAToken() {
		Event event = draft(false); clearInvocations(tokens); publisher.publish("host-1", event.getId());
		assertThat(bson(event.getId()).getString("status")).isEqualTo("PUBLISHED");
		assertThat(bson(event.getId()).get("publicInvitation", Document.class)).containsEntry("enabled", false).doesNotContainKey("token");
		verifyNoInteractions(tokens);
	}
	@Test void mapperReadsThePreviousTokenOnlyBsonWithoutMigratingIt() {
		Event event = draft(false);
		mongo.updateFirst(Query.query(Criteria.where("id").is(event.getId())), new Update().set("status", "PUBLISHED")
				.set("publicInvitation", new Document("token", token(5000))), EventDocument.class);
		Document before = bson(event.getId()); Event loaded = current(event.getId());
		assertThat(loaded.getPublicInvitation()).isEqualTo(new PublicInvitation(true, token(5000)));
		assertThat(bson(event.getId())).isEqualTo(before);
	}
	@Test void aPreviouslyPublishedEventWithoutTheFeatureStaysDisabled() {
		Event event = draft(false); mongo.updateFirst(Query.query(Criteria.where("id").is(event.getId())),
				new Update().set("status", "PUBLISHED").unset("publicInvitation"), EventDocument.class);
		clearInvocations(tokens); Document before = bson(event.getId());
		assertThat(current(event.getId()).getPublicInvitation()).isEqualTo(new PublicInvitation(false, null));
		assertThat(bson(event.getId())).isEqualTo(before); verifyNoInteractions(tokens);
	}
	@Test void disablingAUsedLinkRetainsItsGuestsAndReactivationRevokesTheOldLink() {
		Event event = draft(true); Event published = publisher.publish("host-1", event.getId()); String old = published.getPublicInvitation().token();
		Guest guest = intentions.join(new JoinPublicInvitationCommand(old, null, "Ana", null, null));
		intentions.change("host-1", event.getId(), false);
		assertThat(current(event.getId()).getGuests()).extracting(Guest::getId).containsExactly(guest.getId());
		assertThat(links.findByToken(old)).isEmpty();
		String fresh = intentions.change("host-1", event.getId(), true).orElseThrow().token(); assertThat(fresh).isNotEqualTo(old);
		assertThatThrownBy(() -> intentions.join(new JoinPublicInvitationCommand(old, null, "Ana", null, null))).isInstanceOf(InvitationNotFoundException.class);
	}
	@Test void cancellationKeepsTheLinkResolvableButClosesJoiningAndSwitching() {
		Event event = draft(true); Event published = publisher.publish("host-1", event.getId()); String token = published.getPublicInvitation().token();
		published.cancel("Cancelado", NOW); events.save(published);
		Event cancelled = links.findByToken(token).orElseThrow(); assertThat(cancelled.getPublicInvitation().token()).isEqualTo(token);
		assertThat(cancelled.canJoinPublicInvitation(NOW, new PublicJoinLimits(25, 500))).isFalse();
		var error = catchThrowableOfType(() -> intentions.join(new JoinPublicInvitationCommand(token, null, "Ana", null, null)), PublicJoinConflictException.class);
		assertThat(error.getCode()).isEqualTo(PublicJoinCode.JOIN_CLOSED);
		assertThatThrownBy(() -> intentions.change("host-1", event.getId(), false)).isInstanceOf(PublicInvitationNotEditableException.class);
	}
	@Test void aRealRaceBetweenPublishingAndDraftIntentionHasOneConflictAndAValidBson() throws Exception {
		Event event = draft(false); var barrier = new CyclicBarrier(2); var reads = new AtomicInteger();
		doAnswer(call -> { Object result = call.callRealMethod(); if (reads.incrementAndGet() <= 2) { barrier.await(10, TimeUnit.SECONDS); } return result; })
				.when(eventWrites).findById(event.getId());
		List<Integer> outcomes;
		try (var executor = Executors.newFixedThreadPool(2)) {
			var publish = executor.submit(() -> outcome(() -> publisher.publish("host-1", event.getId())));
			var intent = executor.submit(() -> outcome(() -> intentions.change("host-1", event.getId(), true)));
			outcomes = List.of(publish.get(30, TimeUnit.SECONDS), intent.get(30, TimeUnit.SECONDS));
		}
		assertThat(outcomes).containsExactlyInAnyOrder(200, 409); Document after = bson(event.getId());
		Document link = after.get("publicInvitation", Document.class); assertThat(link).doesNotContainKey("token");
		if ("DRAFT".equals(after.getString("status"))) { assertThat(link.getBoolean("enabled")).isTrue(); }
		else { assertThat(after.getString("status")).isEqualTo("PUBLISHED"); assertThat(link.getBoolean("enabled")).isFalse(); }
	}
	@ParameterizedTest @ValueSource(booleans = { false, true })
	void aDraftIntentionArrivingAfterPublicationIs409AndNeverChangesPublishedState(boolean initiallyEnabled) throws Exception {
		Event event = draft(initiallyEnabled); var entered = new CountDownLatch(1); var published = new CountDownLatch(1);
		doAnswer(call -> { entered.countDown(); if (!published.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Publication did not finish"); } return call.callRealMethod(); })
				.when(links).change(any(), any());
		try (var executor = Executors.newFixedThreadPool(2)) {
			var delayed = executor.submit(() -> outcome(() -> intentions.change("host-1", event.getId(), !initiallyEnabled)));
			assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
			var publication = executor.submit(() -> { try { return publisher.publish("host-1", event.getId()); } finally { published.countDown(); } });
			Event result = publication.get(20, TimeUnit.SECONDS); assertThat(delayed.get(20, TimeUnit.SECONDS)).isEqualTo(409);
			assertThat(current(event.getId()).getPublicInvitation()).isEqualTo(result.getPublicInvitation());
			assertThat(current(event.getId()).getVersion()).isEqualTo(event.getVersion() + 1);
		}
	}
	private static int outcome(Runnable action) {
		try { action.run(); return 200; }
		catch (OptimisticLockingFailureException | EventConcurrentModificationException exception) { return 409; }
	}
}
