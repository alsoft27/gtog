package com.gtog.event.application;

import com.gtog.event.domain.model.*;
import static com.gtog.event.application.InvitationMessageFormatter.*;

public final class WhatsAppInvitationComposer {
	private final InvitationLinkFactory links;
	public WhatsAppInvitationComposer(String publicBaseUrl) { this.links = new InvitationLinkFactory(publicBaseUrl); }

	public WhatsAppInvitationLink compose(Event event, Guest guest, String hostName) {
		String venue = event.getVenue() == null ? null : abbreviate(venue(event.getVenue()), 100);
		String platform = event.getModality() == Modality.ONLINE ? event.getOnlineAccess().platform() : null;
		String message = renderBody(singleLine(guest.getName()), singleLine(hostName), abbreviate(event.getTitle(), 100),
				scheduleAndLocation(event.getStartsAt(), event.getEndsAt(), event.getTimeZone(), venue, platform), links.forGuest(guest.getToken()));
		return WhatsAppInvitationLink.create(guest.getPhoneE164(), message);
	}

	// Solo recibe texto público ya formateado y el enlace de respuesta, nunca OnlineAccess.
	private static String renderBody(String guestName, String hostName, String title, String schedule, String invitationUrl) {
		return "Hola " + guestName + ",\n\n" + hostName + " te invita a " + title + "\n" + schedule + "\nResponde desde: " + invitationUrl;
	}
}
