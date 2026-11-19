package com.gtog.event.application;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.gtog.event.domain.model.*;
import static com.gtog.event.InvitationTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

class WhatsAppInvitationComposerTest {
	private final WhatsAppInvitationComposer composer = new WhatsAppInvitationComposer("https://gtog.example.com/");
	private final Guest guest = guest(101, InvitationStatus.NOT_SENT, null);
	private Event withText(String title, String place) {
		var original = event(EventStatus.PUBLISHED, null, guest);
		return Event.reconstituteBuilder().id(original.getId()).hostId(original.getHostId()).title(title)
				.startsAt(original.getStartsAt()).endsAt(original.getEndsAt()).timeZone(original.getTimeZone()).modality(Modality.IN_PERSON)
				.status(EventStatus.PUBLISHED).venue(new Venue(place, "Madrid", 0.0, 0.0, "place", null))
				.responseOptions(original.getResponseOptions()).guests(original.getGuests()).build();
	}
	@ParameterizedTest @ValueSource(strings = { "👨‍👩‍👧‍👦", "🇪🇸", "👍🏽" })
	void theTitleIsCutAtAGraphemeBoundary(String emoji) {
		String text = "a".repeat(96) + emoji + "b".repeat(10);
		var link = composer.compose(withText(text, "Sala"), guest, "Anfitrión");
		assertThat(link.message()).contains("te invita a " + "a".repeat(96) + emoji + "...\n");
	}
	@ParameterizedTest @ValueSource(strings = { "👨‍👩‍👧‍👦", "🇪🇸", "👍🏽" })
	void theCombinedPlaceAndAddressAreCutAtAGraphemeBoundary(String emoji) {
		String text = "a".repeat(96) + emoji + "b".repeat(10);
		var link = composer.compose(withText("Evento", text), guest, "Anfitrión");
		assertThat(link.message()).contains("Lugar: " + "a".repeat(96) + emoji + "...\n");
	}
	@ParameterizedTest @ValueSource(strings = { "Boda de Ana & Luis #1 - C++ 100% 'cena' \"comillas\"", "Reunión mañana 🎉 con café y ñ" })
	void standardDecodingRecoversEveryMessageCharacter(String title) {
		var link = composer.compose(withText(title, "Sala"), guest, "Anfitrión");
		URI uri = URI.create(link.url());
		String encoded = uri.getRawQuery().substring("text=".length());
		assertThat(uri.getHost()).isEqualTo("wa.me"); assertThat(uri.getPath()).isEqualTo("/34612345678");
		assertThat(encoded).doesNotContain("+").contains("%20", "%0A");
		if (title.contains("+")) { assertThat(encoded).contains("%2B"); }
		String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
		assertThat(decoded).isEqualTo(link.message());
		assertThat(decoded.indexOf(guest.getToken())).isGreaterThanOrEqualTo(0).isEqualTo(decoded.lastIndexOf(guest.getToken()));
	}
	@Test void onlineMessagesContainOnlyThePlatformAndNoEmailFooter() {
		var other = guest(202, InvitationStatus.NOT_SENT, null);
		var link = composer.compose(event(EventStatus.PUBLISHED, LinkVisibility.ALWAYS, guest, other), guest, "Anfitrión");
		assertThat(link.message()).contains("Plataforma: Zoom", "Inicio: 01/01/2027 18:00", "Fin: 01/01/2027 20:00", "Zona horaria: Europe/Madrid")
				.doesNotContain(URL, ROOM, PASSWORD, INSTRUCTIONS, "a través de gtog", "no admite respuestas",
						other.getId(), other.getName(), other.getEmail(), other.getPhoneE164(), other.getToken());
		assertThat(link.toString()).doesNotContain(guest.getToken());
	}
	@Test void controlsInNamesTitleAndPlaceAreNeutralized() {
		var named = Guest.reconstitute(guest.getId(), " Ana\r\nPeña ", guest.getEmail(), guest.getPhoneE164(), guest.getToken(), InvitationStatus.NOT_SENT, null, null, null);
		var link = composer.compose(withText(" Evento\r\nTítulo\u0085 ", " Sala\tPrincipal\u009f "), named, " Host\r\nNombre\u2028 ");
		assertThat(link.message()).contains("Hola Ana  Peña", "Host  Nombre te invita a Evento  Título", "Lugar: Sala Principal — Madrid")
				.doesNotContain("\r", "\t", "\u2028", "\u0085", "\u009f");
	}
	@Test void threeHundredCharacterGuestAndHostNamesAreAbbreviatedLikeTheTitle() {
		var named = Guest.reconstitute(guest.getId(), "a".repeat(300), guest.getEmail(), guest.getPhoneE164(), guest.getToken(), InvitationStatus.NOT_SENT, null, null, null);
		var link = composer.compose(withText("Evento", "Sala"), named, "b".repeat(300));
		assertThat(link.message()).startsWith("Hola " + "a".repeat(97) + "...,\n\n" + "b".repeat(97) + "... te invita a Evento\n");
	}
	@ParameterizedTest @ValueSource(strings = { "👨‍👩‍👧‍👦", "🇪🇸", "👍🏽" })
	void guestAndHostNamesPreserveTheCompoundGraphemeAtTheCut(String emoji) {
		String name = "a".repeat(96) + emoji + "b".repeat(300);
		var named = Guest.reconstitute(guest.getId(), name, guest.getEmail(), guest.getPhoneE164(), guest.getToken(), InvitationStatus.NOT_SENT, null, null, null);
		var link = composer.compose(withText("Evento", "Sala"), named, name);
		String shortened = "a".repeat(96) + emoji + "...";
		assertThat(link.message()).startsWith("Hola " + shortened + ",\n\n" + shortened + " te invita a Evento\n");
	}
}
