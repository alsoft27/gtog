package com.gtog.event.infrastructure.out.persistence;

import org.springframework.stereotype.Component;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventType;
import com.gtog.event.domain.model.EventDomainException;
import com.gtog.event.domain.model.PublicInvitation;
import com.gtog.shared.StoredDataIntegrityException;
import com.gtog.event.domain.model.LinkVisibility;
import com.gtog.event.domain.model.Modality;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.OnlineAccess;
import com.gtog.event.domain.model.ResponseOption;
import com.gtog.event.domain.model.Venue;

@Component
public class EventMapper {

	private final GuestMapper guestMapper;

	public EventMapper(GuestMapper guestMapper) {
		this.guestMapper = guestMapper;
	}

	public EventDocument toDocument(Event event) {
		return new EventDocument(
				event.getId(),
				event.getHostId(),
				event.getTitle(),
				event.getDescription(),
				event.getStartsAt(),
				event.getEndsAt(),
				event.getTimeZone(),
				event.getModality().name(),
				event.getStatus().name(),
				event.getResponseOptions().stream().map(this::toDocument).toList(),
				event.isAllowComment(),
				event.isAllowResponseChange(),
				event.getResponseDeadline(),
				toDocument(event.getVenue()),
				toDocument(event.getOnlineAccess()),
				event.getCancelledAt(),
				event.getCancellationReason(),
				event.getVersion(),
				event.getGuests().stream().map(guestMapper::toDocument).toList(),
				new PublicInvitationDocument(event.getPublicInvitation().enabled(), event.getPublicInvitation().token()), event.getEventType().name());
	}

	public Event toDomain(EventDocument document) {
		try {
			// Los documentos anteriores no tenían discriminador. DATE_POLL se habilita en la siguiente rebanada.
			if (document.getEventType() != null && !EventType.FIXED_DATE.name().equals(document.getEventType())) {
				throw new StoredDataIntegrityException(document.getId(), IllegalArgumentException.class);
			}
			return reconstitute(document);
		} catch (EventDomainException exception) {
			throw new StoredDataIntegrityException(document.getId(), exception.getClass());
		}
	}

	private Event reconstitute(EventDocument document) {
		return Event.reconstituteBuilder()
				.id(document.getId())
				.hostId(document.getHostId())
				.title(document.getTitle())
				.description(document.getDescription())
				.startsAt(document.getStartsAt())
				.endsAt(document.getEndsAt())
				.timeZone(document.getTimeZone())
				.modality(Modality.valueOf(document.getModality()))
				.status(EventStatus.valueOf(document.getStatus()))
				.responseOptions(document.getResponseOptions().stream().map(this::toDomain).toList())
				.allowComment(document.isAllowComment())
				.allowResponseChange(document.isAllowResponseChange())
				.responseDeadline(document.getResponseDeadline())
				.venue(toDomain(document.getVenue()))
				.onlineAccess(toDomain(document.getOnlineAccess()))
				.cancelledAt(document.getCancelledAt())
				.cancellationReason(document.getCancellationReason())
				.version(document.getVersion())
				.guests(document.getGuests().stream().map(guestMapper::toDomain).toList())
				.publicInvitation(toDomain(document.getPublicInvitation()))
				.build();
	}

	private PublicInvitation toDomain(PublicInvitationDocument document) {
		if (document == null) { return new PublicInvitation(false, null); }
		boolean enabled = document.enabled() == null ? document.token() != null : document.enabled();
		return new PublicInvitation(enabled, document.token());
	}

	private ResponseOptionDocument toDocument(ResponseOption responseOption) {
		return new ResponseOptionDocument(responseOption.id(), responseOption.label(),
				responseOption.countsAsAttendance());
	}

	private ResponseOption toDomain(ResponseOptionDocument document) {
		return new ResponseOption(document.getId(), document.getLabel(), document.isCountsAsAttendance());
	}

	private VenueDocument toDocument(Venue venue) {
		if (venue == null) {
			return null;
		}
		return new VenueDocument(venue.placeName(), venue.address(), venue.latitude(), venue.longitude(),
				venue.placeId(), venue.directions());
	}

	private Venue toDomain(VenueDocument document) {
		if (document == null) {
			return null;
		}
		return new Venue(document.getPlaceName(), document.getAddress(), document.getLatitude(),
				document.getLongitude(), document.getPlaceId(), document.getDirections());
	}

	private OnlineAccessDocument toDocument(OnlineAccess onlineAccess) {
		if (onlineAccess == null) {
			return null;
		}
		return new OnlineAccessDocument(onlineAccess.platform(), onlineAccess.url(), onlineAccess.roomId(),
				onlineAccess.password(), onlineAccess.instructions(), onlineAccess.linkVisibility().name(),
				onlineAccess.hoursBefore());
	}

	private OnlineAccess toDomain(OnlineAccessDocument document) {
		if (document == null) {
			return null;
		}
		return new OnlineAccess(document.getPlatform(), document.getUrl(), document.getRoomId(),
				document.getPassword(), document.getInstructions(), LinkVisibility.valueOf(document.getLinkVisibility()),
				document.getHoursBefore());
	}
}
