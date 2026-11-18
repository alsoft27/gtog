package com.gtog.event.application;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.gtog.event.domain.model.*;

import static com.gtog.event.InvitationTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

class InvitationEmailComposerTest {
	private final InvitationEmailComposer composer = new InvitationEmailComposer("https://gtog.example.com/");
	private final Guest guest = guest(101, InvitationStatus.NOT_SENT, null);

	@Test
	void theMessageUsesTheEventLocalDatesAndFixedSpanishText() {
		var message = composer.compose(event(EventStatus.PUBLISHED, null, guest), guest, "Anfitrión");
		assertThat(message.recipient()).isEqualTo(guest.getEmail());
		assertThat(message.subject()).isEqualTo("Public event");
		assertThat(message.body()).contains("Hola " + guest.getName(), "Anfitrión te invita a Public event.",
				"Inicio: 01/01/2027 18:00", "Fin: 01/01/2027 20:00", "Zona horaria: Europe/Madrid",
				"Lugar: Sala — Direccion", "https://gtog.example.com/r/" + guest.getToken(),
				"Este mensaje no admite respuestas: responde desde el enlace.",
				"Enviado por Anfitrión a través de gtog. Si no esperabas esta invitación, ignórala.");
	}

	@Test
	void onlineMessagesOnlyIncludeThePlatformEvenWhenTheLinkIsAlwaysVisible() {
		var message = composer.compose(event(EventStatus.PUBLISHED, LinkVisibility.ALWAYS, guest), guest, "Host");
		assertThat(message.body()).contains("Plataforma: Zoom").doesNotContain(URL, PASSWORD, ROOM, INSTRUCTIONS, "Lugar:");
	}

	@Test
	void otherGuestsAndTheResponseEmailAreNeverIncluded() {
		Guest other = guest(102, InvitationStatus.NOT_SENT, null);
		var message = composer.compose(event(EventStatus.PUBLISHED, null, guest, other), guest, "Host");
		assertThat(message.subject() + message.body()).doesNotContain(other.getName(), other.getEmail(), other.getToken(),
				guest.getEmail(), guest.getPhoneE164(), "respuesta-privada@example.com");
	}

	@Test
	void controlCharactersInTheTitleAndNamesAreReplacedWithSpaces() {
		Guest named = Guest.reconstitute("id", " Ana\r\nBcc: nobody ", "ana@example.com", null, guest.getToken(),
				InvitationStatus.NOT_SENT, null, null, null);
		Event event = Event.reconstituteBuilder().id("event").hostId("host").title(" Fiesta\r\n\tX\u2028Y ")
				.startsAt(LocalDateTime.of(2027, 1, 1, 18, 0)).endsAt(LocalDateTime.of(2027, 1, 1, 20, 0))
				.timeZone("Europe/Madrid").modality(Modality.IN_PERSON).status(EventStatus.PUBLISHED)
				.venue(new Venue("Sala", "Direccion", 0.0, 0.0, "place", null)).responseOptions(List.of()).guests(List.of(named)).build();
		var message = composer.compose(event, named, " Host\r\nInjected ");
		assertThat(message.subject()).isEqualTo("Fiesta   X Y").doesNotContain("\r", "\n", "\t", "\u2028");
		assertThat(message.body()).startsWith("Hola Ana  Bcc: nobody,\n\nHost  Injected te invita a Fiesta   X Y.")
				.doesNotContain("\r", "\t", "\u2028");
	}

	@ParameterizedTest
	@ValueSource(strings = { "file:///private", "https://user:secret@example.com", "https://example.com?token=secret", "https://example.com/#token", "/relative" })
	void theBaseUrlCannotContainCredentialsQueryOrFragment(String base) {
		assertThatIllegalArgumentException().isThrownBy(() -> new InvitationEmailComposer(base));
	}

	@Test
	void emailDiagnosticsDoNotExposeTheRecipientOrTheLink() {
		assertThat(composer.compose(event(EventStatus.PUBLISHED, null, guest), guest, "Host").toString())
				.doesNotContain(guest.getEmail(), guest.getToken());
	}
}
