package com.gtog.event.infrastructure.out.calendar;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.TzId;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.Version;
import com.gtog.event.domain.model.InvitationCalendarData;
import com.gtog.event.domain.model.InvitationCalendarGenerationException;
import com.gtog.event.domain.port.out.InvitationCalendarRendererPort;

@Component
public class ICal4jInvitationCalendarAdapter implements InvitationCalendarRendererPort {
	@Override
	public byte[] render(InvitationCalendarData data) {
		try {
			var zone = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(data.timeZone());
			if (zone == null) { throw new InvitationCalendarGenerationException(); }
			Calendar calendar = new Calendar();
			calendar.add(new ProdId("-//gtog//Event Calendar//EN"));
			calendar.add(new Version(new ParameterList(), "2.0"));
			calendar.add(zone.getVTimeZone());
			// Sin DTSTAMP automatico: el instante procede siempre del Clock de aplicacion.
			VEvent event = new VEvent(false);
			event.add(new Uid(data.eventId() + "@gtog"));
			event.add(new DtStamp(data.generatedAt()));
			event.add(new Summary(normalizeLines(data.title())));
			event.add(new DtStart<>(new ParameterList(List.of(new TzId(zone.getID()))), data.startsAt()));
			event.add(new DtEnd<>(new ParameterList(List.of(new TzId(zone.getID()))), data.endsAt()));
			if (data.description() != null) { event.add(new Description(normalizeLines(data.description()))); }
			if (data.venue() != null) {
				event.add(new Location(normalizeLines(data.venue().placeName() + ", " + data.venue().address())));
			} else if (data.onlineUrl() != null) {
				event.add(new Location(data.onlineUrl()));
				event.add(new Url(URI.create(data.onlineUrl())));
			}
			calendar.add(event);
			return foldUtf8(calendar.toString()).getBytes(StandardCharsets.UTF_8);
		} catch (RuntimeException failure) {
			// No conservar causas ni mensajes del serializador que puedan contener datos del evento.
			throw new InvitationCalendarGenerationException();
		}
	}

	private String normalizeLines(String value) { return value.replace("\r\n", "\n").replace('\r', '\n'); }

	private String foldUtf8(String value) {
		// FoldingWriter de iCal4j cuenta caracteres; RFC 5545 limita a 75 octetos UTF-8.
		StringBuilder folded = new StringBuilder();
		for (String line : value.split("\r\n")) {
			int octets = 0;
			for (int offset = 0; offset < line.length();) {
				int point = line.codePointAt(offset);
				int width = point <= 0x7f ? 1 : point <= 0x7ff ? 2 : point <= 0xffff ? 3 : 4;
				if (octets + width > 75) {
					folded.append("\r\n ");
					octets = 1;
				}
				folded.appendCodePoint(point);
				octets += width;
				offset += Character.charCount(point);
			}
			folded.append("\r\n");
		}
		return folded.toString();
	}
}
