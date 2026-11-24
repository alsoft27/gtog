package com.gtog.event.application;

import static com.gtog.event.application.InvitationMessageFormatter.singleLine;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationEmail;
import com.gtog.event.domain.model.Modality;

public final class InvitationEmailComposer {
	private final InvitationLinkFactory links;

	public InvitationEmailComposer(String publicBaseUrl) {
		this.links = new InvitationLinkFactory(publicBaseUrl);
	}

	public InvitationEmail compose(Event event, Guest guest, String hostName) {
		String host = singleLine(hostName);
		String title = singleLine(event.getTitle());
		String platform = event.getModality() == Modality.ONLINE ? event.getOnlineAccess().platform() : null;
		return new InvitationEmail(guest.getEmail(), title, renderBody(host, singleLine(guest.getName()), title,
				InvitationMessageFormatter.scheduleAndLocation(event.getSchedule(), event.getResponseDeadline(),
						event.getVenue() == null ? null : InvitationMessageFormatter.venue(event.getVenue()), platform),
				links.forGuest(guest.getToken())));
	}

	// El renderizador solo recibe texto público ya formateado, nunca OnlineAccess ni secretos de la sala.
	private static String renderBody(String host, String guestName, String title, String publicSchedule, String invitationUrl) {
		StringBuilder body = new StringBuilder("Hola ").append(guestName).append(",\n\n")
				.append(host).append(" te invita a ").append(title).append(".\n")
				.append(publicSchedule);
		body.append("\nResponde desde: ").append(invitationUrl).append("\n\n")
				.append("Este mensaje no admite respuestas: responde desde el enlace.\n\n")
				.append("Enviado por ").append(host).append(" a través de gtog. Si no esperabas esta invitación, ignórala.");
		return body.toString();
	}
}
