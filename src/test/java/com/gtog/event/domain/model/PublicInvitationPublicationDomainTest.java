package com.gtog.event.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.gtog.event.domain.port.out.PublicInvitationTokenGeneratorPort;
import static com.gtog.event.GuestTestFixtures.token;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicInvitationPublicationDomainTest {
	private Event.Builder draft(boolean enabled) {
		return Event.builder().hostId("host").title("Evento").startsAt(LocalDateTime.of(2027, 1, 1, 18, 0))
				.endsAt(LocalDateTime.of(2027, 1, 1, 20, 0)).timeZone("Europe/Madrid").modality(Modality.IN_PERSON)
				.venue(new Venue("Sala", "Dirección", 0.0, 0.0, "place", null)).publicInvitationEnabled(enabled);
	}
	@Test void creatingAnEnabledDraftStoresOnlyTheIntention() {
		Event event = draft(true).build(); assertThat(event.isPublicInvitationEnabled()).isTrue();
		assertThat(event.getPublicInvitation().token()).isNull();
	}
	@Test void aTokenCannotBeConstructedInADraft() {
		assertThatThrownBy(() -> draft(true).publicInvitation(new PublicInvitation(true, token(10))).build())
				.isInstanceOf(InvalidPublicInvitationStateException.class);
	}
	@Test void aTokenCannotBeConstructedWithDisabledIntention() {
		assertThatThrownBy(() -> new PublicInvitation(false, token(10))).isInstanceOf(InvalidPublicInvitationStateException.class);
	}
	@Test void anEnabledPublishedEventCannotBeConstructedWithoutAToken() {
		assertThatThrownBy(() -> Event.reconstituteBuilder().status(EventStatus.PUBLISHED)
				.publicInvitation(new PublicInvitation(true, null)).build()).isInstanceOf(InvalidPublicInvitationStateException.class);
	}
	@Test void publishingAnEnabledDraftObtainsTheCredentialFromThePort() {
		Event event = draft(true).build(); PublicInvitationTokenGeneratorPort tokens = mock(PublicInvitationTokenGeneratorPort.class);
		when(tokens.generate()).thenReturn(token(10)); event.publish(tokens);
		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.getPublicInvitation()).isEqualTo(new PublicInvitation(true, token(10))); verify(tokens).generate();
	}
	@Test void publishingWithoutIntentionDoesNotGenerateACredential() {
		Event event = draft(false).build(); PublicInvitationTokenGeneratorPort tokens = mock(PublicInvitationTokenGeneratorPort.class);
		when(tokens.generate()).thenReturn(token(11)); event.publish(tokens);
		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED); assertThat(event.isPublicInvitationEnabled()).isFalse();
		assertThat(event.getPublicInvitation().token()).isNull(); verifyNoInteractions(tokens);
	}
	@Test void cancellationPreservesTheCredentialButClosesAdmission() {
		Event event = draft(true).build(); event.publish(() -> token(10)); event.cancel("Cancelado", Instant.EPOCH);
		assertThat(event.getPublicInvitation()).isEqualTo(new PublicInvitation(true, token(10)));
		assertThat(event.canJoinPublicInvitation(Instant.EPOCH, new PublicJoinLimits(25, 500))).isFalse();
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = { "CANCELLED", "FINISHED" })
	void aClosedEventMayRetainItsCredentialButCannotChangeIt(EventStatus status) {
		Event event = Event.reconstituteBuilder().status(status).publicInvitation(new PublicInvitation(true, token(10))).build();
		assertThat(event.getPublicInvitation().token()).isEqualTo(token(10));
		assertThatThrownBy(event::ensurePublicInvitationEditable).isInstanceOf(PublicInvitationNotEditableException.class);
	}
}
