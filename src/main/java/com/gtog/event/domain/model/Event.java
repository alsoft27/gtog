package com.gtog.event.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.gtog.event.domain.port.out.PublicInvitationTokenGeneratorPort;

public class Event {

	private final String id;
	private final String hostId;
	private String title;
	private String description;
	private EventSchedule schedule;
	private Modality modality;
	private EventStatus status;
	private List<ResponseOption> responseOptions;
	private boolean allowComment;
	private boolean allowResponseChange;
	private LocalDateTime responseDeadline;
	private Venue venue;
	private OnlineAccess onlineAccess;
	private Instant cancelledAt;
	private String cancellationReason;
	private final Long version;
	private List<Guest> guests = List.of();
	private PublicInvitation publicInvitation = new PublicInvitation(false, null);

	private Event(String id, String hostId, String title, String description, EventSchedule schedule,
			Modality modality, EventStatus status,
			List<ResponseOption> responseOptions, boolean allowComment, boolean allowResponseChange,
			LocalDateTime responseDeadline, Venue venue, OnlineAccess onlineAccess, Instant cancelledAt,
			String cancellationReason, Long version) {
		this.id = id;
		this.hostId = hostId;
		this.title = title;
		this.description = description;
		this.schedule = schedule;
		this.modality = modality;
		this.status = status;
		this.responseOptions = responseOptions;
		this.allowComment = allowComment;
		this.allowResponseChange = allowResponseChange;
		this.responseDeadline = responseDeadline;
		this.venue = venue;
		this.onlineAccess = onlineAccess;
		this.cancelledAt = cancelledAt;
		this.cancellationReason = cancellationReason;
		this.version = version;
	}

	// Punto de entrada para crear un evento nuevo: valida todas las reglas, genera el id y aplica los
	// valores por defecto. Un builder en vez de un factory method con una decena de parametros posicionales,
	// porque esta lista ya ha cambiado de forma tres rebanadas seguidas y solo va a seguir creciendo.
	public static Builder builder() {
		return new Builder();
	}

	// Punto de entrada para rehidratar un evento ya persistido: no repite ninguna validacion.
	public static ReconstituteBuilder reconstituteBuilder() {
		return new ReconstituteBuilder();
	}

	public Guest addGuest(Guest guest) {
		ensureGuestsEditable();
		validateGuestUniqueness(guest, null);
		List<Guest> updated = new ArrayList<>(guests);
		updated.add(guest);
		guests = List.copyOf(updated);
		return guest;
	}

	public Guest updateGuest(String guestId, Guest replacement) {
		ensureGuestsEditable();
		Guest existing = getGuest(guestId);
		if (!existing.getId().equals(replacement.getId()) || !existing.getToken().equals(replacement.getToken())) {
			throw new IllegalArgumentException("Guest identity and token cannot be changed");
		}
		validateGuestUniqueness(replacement, guestId);
		guests = guests.stream().map(guest -> guest.getId().equals(guestId) ? replacement : guest).toList();
		return replacement;
	}

	public Guest removeGuest(String guestId) {
		ensureGuestsEditable();
		Guest guest = getGuest(guestId);
		guests = guests.stream().filter(existing -> !existing.getId().equals(guestId)).toList();
		return guest;
	}

	public Guest getGuest(String guestId) {
		return guests.stream().filter(guest -> guest.getId().equals(guestId)).findFirst()
				.orElseThrow(GuestNotFoundException::new);
	}

	public List<Guest> getGuests() {
		return List.copyOf(guests);
	}

	public PublicInvitation getPublicInvitation() { return publicInvitation; }
	public boolean isPublicInvitationEnabled() { return publicInvitation.enabled(); }
	private void restorePublicInvitation(PublicInvitation value) {
		PublicInvitation resolved = value == null ? new PublicInvitation(false, null) : value;
		if ((status == EventStatus.DRAFT && resolved.token() != null)
				|| (status == EventStatus.PUBLISHED && resolved.enabled() != (resolved.token() != null))) {
			throw new InvalidPublicInvitationStateException();
		}
		this.publicInvitation = resolved;
	}
	public long publicJoinedCount() { return guests.stream().filter(guest -> guest.getSource() == GuestSource.PUBLIC_LINK).count(); }
	public boolean hasGuestContact(Guest candidate) { return guests.stream().anyMatch(candidate::sharesContact); }
	public Guest discardConflictingPublicContact(Guest candidate) { return hasGuestContact(candidate) ? candidate.withoutContact() : candidate; }
	public boolean canJoinPublicInvitation(Instant now, PublicJoinLimits limits) {
		return isPublicInvitationEnabled() && responseAcceptancePolicy(now).canRespond(false)
				&& publicJoinedCount() < limits.maxPublicGuests() && guests.size() < limits.maxPerEvent();
	}
	public void ensurePublicInvitationEditable() {
		if (status != EventStatus.DRAFT && status != EventStatus.PUBLISHED) { throw new PublicInvitationNotEditableException(); }
	}

	public void ensureGuestsEditable() {
		if (status == EventStatus.FINISHED || status == EventStatus.CANCELLED) {
			throw new EventGuestsNotEditableException();
		}
	}

	private void validateGuestUniqueness(Guest candidate, String excludedGuestId) {
		if (publicInvitation.token() != null && publicInvitation.token().equals(candidate.getToken())) { throw new DuplicateGuestTokenException(); }
		for (Guest existing : guests) {
			if (existing.getId().equals(excludedGuestId)) {
				continue;
			}
			if (existing.getToken().equals(candidate.getToken())) {
				throw new DuplicateGuestTokenException();
			}
			if (candidate.sameEmail(existing)) {
				throw new DuplicateGuestEmailException();
			}
			if (candidate.samePhone(existing)) {
				throw new DuplicateGuestPhoneException();
			}
		}
	}

	public void replaceCandidateDates(List<CandidateDateEdit> edits, Long expectedRevision, DatePollLimits limits) {
		if (status != EventStatus.DRAFT && status != EventStatus.PUBLISHED) { throw new EventNotEditableException(id); }
		if (!(schedule instanceof DatePollSchedule poll)) { throw new EventScheduleConflictException(); }
		this.schedule = poll.replaceCandidates(edits, expectedRevision, limits);
	}

	public void replaceResponseOptions(List<ResponseOptionEdit> edits, Set<String> answeredOptionIds) {
		if (status != EventStatus.DRAFT && status != EventStatus.PUBLISHED) {
			throw new EventNotEditableException(id);
		}
		if (getEventType() == EventType.DATE_POLL) { throw new EventScheduleConflictException(); }
		List<ResponseOption> merged = edits.stream().map(this::resolveEdit).toList();
		if (status == EventStatus.PUBLISHED) {
			for (ResponseOption option : responseOptions) {
				if (!answeredOptionIds.contains(option.id())) { continue; }
				var replacements = merged.stream().filter(candidate -> candidate.id().equals(option.id())).toList();
				if (replacements.isEmpty()) { throw new ResponseOptionDeletionNotAllowedException(); }
				if (replacements.stream().anyMatch(candidate -> candidate.countsAsAttendance() != option.countsAsAttendance())) {
					throw new ResponseOptionAttendanceChangeNotAllowedException();
				}
			}
		}
		validateResponseOptions(merged);
		this.responseOptions = merged;
	}

	public void replaceVenue(Venue venue) {
		if (status != EventStatus.DRAFT) {
			throw new EventNotEditableException(id);
		}
		if (modality != Modality.IN_PERSON) {
			throw new ModalityConflictException("Event %s is ONLINE and does not accept a venue".formatted(id));
		}
		this.venue = venue;
	}

	public void replaceOnlineAccess(OnlineAccess onlineAccess) {
		if (status != EventStatus.DRAFT) {
			throw new EventNotEditableException(id);
		}
		if (modality != Modality.ONLINE) {
			throw new ModalityConflictException("Event %s is IN_PERSON and does not accept online access".formatted(id));
		}
		this.onlineAccess = onlineAccess;
	}

	public void publish() {
		// Compatibilidad para publicar eventos sin intención activa; los demás necesitan el puerto explícito.
		publish(() -> { throw new InvalidPublicInvitationStateException(); });
	}

	public void publish(PublicInvitationTokenGeneratorPort tokens) {
		if (status != EventStatus.DRAFT) {
			throw new EventNotPublishableException(id);
		}
		if (modality == Modality.IN_PERSON && venue == null) {
			throw new IncompleteEventForPublishException("the event has no venue");
		}
		if (modality == Modality.ONLINE && onlineAccess == null) {
			throw new IncompleteEventForPublishException("the event has no online access");
		}
		if (getEventType() == EventType.FIXED_DATE && responseOptions.size() < 2) {
			throw new IncompleteEventForPublishException("at least two response options are required");
		}
		PublicInvitation publishedLink = publicInvitation;
		if (publicInvitation.enabled()) {
			String token = tokens.generate();
			if (id.equals(token) || guests.stream().anyMatch(guest -> guest.getToken().equals(token))) {
				throw new PublicInvitationStorageException();
			}
			publishedLink = new PublicInvitation(true, token);
			if (token == null) { throw new InvalidPublicInvitationTokenException(); }
		}
		this.status = EventStatus.PUBLISHED;
		restorePublicInvitation(publishedLink);
	}

	public void cancel(String reason, Instant now) {
		if (status != EventStatus.PUBLISHED) {
			throw new EventNotCancellableException(id);
		}
		this.status = EventStatus.CANCELLED;
		this.cancelledAt = now;
		this.cancellationReason = reason;
	}

	public boolean acceptsResponses(Instant now) {
		return status == EventStatus.PUBLISHED && !schedule.hasEnded(now)
				&& !responseDeadlinePassed(now);
	}

	public boolean pollResultsVisible(Instant now) {
		return getEventType() == EventType.DATE_POLL && status != EventStatus.DRAFT && responseDeadlinePassed(now);
	}

	private boolean responseDeadlinePassed(Instant now) {
		return responseDeadline != null && now.isAfter(responseDeadline.atZone(ZoneId.of(getTimeZone())).toInstant());
	}

	public void requireAcceptingResponses(Instant now) {
		if (!responseAcceptancePolicy(now).canRespond(false)) { throw new ResponseNotAcceptedException(); }
	}

	public ResponseAcceptancePolicy responseAcceptancePolicy(Instant now) {
		return new ResponseAcceptancePolicy(acceptsResponses(now), allowResponseChange);
	}

	public void changeResponseDeadline(LocalDateTime responseDeadline, Instant now) {
		if (status != EventStatus.DRAFT && !(status == EventStatus.PUBLISHED && (schedule instanceof DatePollSchedule || now.isBefore(startsAtInstant())))) {
			throw new ResponseDeadlineNotEditableException();
		}
		schedule.validateResponseDeadline(responseDeadline);
		this.responseDeadline = responseDeadline;
	}

	public void edit(EventEdit edit) {
		if (status != EventStatus.DRAFT) {
			throw new EventNotEditableException(id);
		}
		if (edit.title() == null || edit.title().isBlank()) {
			throw new BlankEventTitleException();
		}
		if (edit.eventType() != null && edit.eventType() != getEventType()) { throw new IncompatibleEventScheduleException(); }
		EventSchedule updatedSchedule;
		if (schedule instanceof DatePollSchedule poll) {
			if (edit.startsAt() != null || edit.endsAt() != null) { throw new IncompatibleEventScheduleException(); }
			updatedSchedule = poll.withTimeZone(edit.timeZone());
		} else {
			updatedSchedule = FixedDateSchedule.create(edit.startsAt(), edit.endsAt(), edit.timeZone());
		}
		updatedSchedule.validateResponseDeadline(edit.responseDeadline());

		Modality newModality = edit.modality();
		if (newModality != this.modality) {
			if (newModality == Modality.IN_PERSON) {
				if (edit.venue() == null) {
					throw new ModalityChangedWithoutLocationException();
				}
				this.venue = edit.venue();
				this.onlineAccess = null;
			}
			else {
				if (edit.onlineAccess() == null) {
					throw new ModalityChangedWithoutLocationException();
				}
				this.onlineAccess = edit.onlineAccess();
				this.venue = null;
			}
		}
		else {
			if (this.modality == Modality.IN_PERSON && edit.venue() != null) {
				this.venue = edit.venue();
			}
			else if (this.modality == Modality.ONLINE && edit.onlineAccess() != null) {
				this.onlineAccess = edit.onlineAccess();
			}
		}

		this.title = edit.title();
		this.description = edit.description();
		this.schedule = updatedSchedule;
		this.modality = newModality;
		this.allowComment = edit.allowComment();
		this.allowResponseChange = edit.allowResponseChange();
		this.responseDeadline = edit.responseDeadline();
	}

	// Vacio si el evento no tiene acceso en linea (presencial) o si la regla de LinkVisibility no deja verlo
	// todavia: asi quien llama no puede olvidarse de comprobar un booleano antes de leer el enlace.
	public Optional<OnlineAccess> visibleOnlineAccess(boolean guestHasConfirmed, Instant now) {
		if (status != EventStatus.PUBLISHED || onlineAccess == null) {
			return Optional.empty();
		}
		boolean visible = switch (onlineAccess.linkVisibility()) {
			case ALWAYS -> true;
			case ON_CONFIRMATION -> guestHasConfirmed;
			case HOURS_BEFORE -> startsAtInstant() != null && !now.isBefore(startsAtInstant().minus(onlineAccess.hoursBefore(), ChronoUnit.HOURS));
		};
		return visible ? Optional.of(onlineAccess) : Optional.empty();
	}

	private static void validatePollFields(LocalDateTime startsAt, LocalDateTime endsAt, List<?> options) {
		if (startsAt != null || endsAt != null || (options != null && !options.isEmpty())) {
			throw new IncompatibleEventScheduleException();
		}
	}

	private static void validateModalityInvariant(Modality modality, Venue venue, OnlineAccess onlineAccess) {
		if (modality == Modality.IN_PERSON) {
			if (venue == null) {
				throw new MissingVenueException();
			}
			if (onlineAccess != null) {
				throw new UnexpectedOnlineAccessException();
			}
		}
		else {
			if (onlineAccess == null) {
				throw new MissingOnlineAccessException();
			}
			if (venue != null) {
				throw new UnexpectedVenueException();
			}
		}
	}

	private ResponseOption resolveEdit(ResponseOptionEdit edit) {
		if (edit.id() == null) {
			return ResponseOption.create(edit.label(), edit.countsAsAttendance());
		}
		return responseOptions.stream()
				.filter(existing -> existing.id().equals(edit.id()))
				.findFirst()
				.map(existing -> new ResponseOption(existing.id(), edit.label(), edit.countsAsAttendance()))
				.orElseThrow(() -> new UnknownResponseOptionIdException(edit.id()));
	}

	private static List<ResponseOption> buildInitialResponseOptions(List<ResponseOptionDraft> drafts) {
		if (drafts == null || drafts.isEmpty()) {
			return List.of(ResponseOption.create("Asisto", true), ResponseOption.create("No asisto", false));
		}
		return drafts.stream().map(draft -> ResponseOption.create(draft.label(), draft.countsAsAttendance())).toList();
	}

	private static void validateResponseOptions(List<ResponseOption> options) {
		if (options.size() < 2 || options.size() > 5) {
			throw new InvalidResponseOptionCountException(options.size());
		}
		if (options.stream().noneMatch(ResponseOption::countsAsAttendance)) {
			throw new NoAttendanceResponseOptionException();
		}
		long distinctLabels = options.stream().map(ResponseOption::label).distinct().count();
		if (distinctLabels != options.size()) {
			throw new DuplicateResponseOptionLabelException();
		}
	}

	private static boolean resolveAllowComment(Boolean allowComment) {
		return allowComment != null && allowComment;
	}

	private static boolean resolveAllowResponseChange(Boolean allowResponseChange) {
		return allowResponseChange == null || allowResponseChange;
	}

	// El instante se calcula al vuelo a partir de la hora local: si cambian las reglas horarias de la zona
	// antes de la fecha del evento, este cálculo siempre refleja la hora local prevista, no un instante congelado.
	public Instant startsAtInstant() {
		return schedule instanceof FixedDateSchedule fixed ? fixed.startsAtInstant() : null;
	}

	public Instant endsAtInstant() {
		return schedule instanceof FixedDateSchedule fixed ? fixed.endsAtInstant() : null;
	}

	public String getId() {
		return id;
	}

	public String getHostId() {
		return hostId;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public LocalDateTime getStartsAt() {
		return schedule instanceof FixedDateSchedule fixed ? fixed.startsAt() : null;
	}

	public LocalDateTime getEndsAt() {
		return schedule instanceof FixedDateSchedule fixed ? fixed.endsAt() : null;
	}

	public EventType getEventType() { return schedule.type(); }

	public EventSchedule getSchedule() { return schedule; }

	public String getTimeZone() {
		return schedule.timeZone();
	}

	public Modality getModality() {
		return modality;
	}

	public EventStatus getStatus() {
		return status;
	}

	public List<ResponseOption> getResponseOptions() {
		return List.copyOf(responseOptions);
	}

	public boolean isAllowComment() {
		return allowComment;
	}

	public boolean isAllowResponseChange() {
		return allowResponseChange;
	}

	public LocalDateTime getResponseDeadline() {
		return responseDeadline;
	}

	public Venue getVenue() {
		return venue;
	}

	public OnlineAccess getOnlineAccess() {
		return onlineAccess;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public String getCancellationReason() {
		return cancellationReason;
	}

	public Long getVersion() {
		return version;
	}

	public static final class Builder {

		private String hostId;
		private String title;
		private String description;
		private LocalDateTime startsAt;
		private LocalDateTime endsAt;
		private String timeZone;
		private Modality modality;
		private List<ResponseOptionDraft> responseOptions;
		private Boolean allowComment;
		private Boolean allowResponseChange;
		private LocalDateTime responseDeadline;
		private Venue venue;
		private OnlineAccess onlineAccess;
		private EventType eventType;
		private DatePollDraft datePoll;
		private DatePollLimits datePollLimits = DatePollLimits.defaults();
		private PublicInvitation publicInvitation;

		public Builder eventType(EventType value) { this.eventType = value; return this; }
		public Builder datePoll(DatePollDraft value) { this.datePoll = value; return this; }
		public Builder datePollLimits(DatePollLimits value) { this.datePollLimits = value; return this; }

		public Builder publicInvitation(PublicInvitation value) { this.publicInvitation = value; return this; }
		public Builder publicInvitationEnabled(boolean enabled) { return publicInvitation(new PublicInvitation(enabled, null)); }

		private Builder() {
		}

		public Builder hostId(String hostId) {
			this.hostId = hostId;
			return this;
		}

		public Builder title(String title) {
			this.title = title;
			return this;
		}

		public Builder description(String description) {
			this.description = description;
			return this;
		}

		public Builder startsAt(LocalDateTime startsAt) {
			this.startsAt = startsAt;
			return this;
		}

		public Builder endsAt(LocalDateTime endsAt) {
			this.endsAt = endsAt;
			return this;
		}

		public Builder timeZone(String timeZone) {
			this.timeZone = timeZone;
			return this;
		}

		public Builder modality(Modality modality) {
			this.modality = modality;
			return this;
		}

		public Builder responseOptions(List<ResponseOptionDraft> responseOptions) {
			this.responseOptions = responseOptions;
			return this;
		}

		public Builder allowComment(Boolean allowComment) {
			this.allowComment = allowComment;
			return this;
		}

		public Builder allowResponseChange(Boolean allowResponseChange) {
			this.allowResponseChange = allowResponseChange;
			return this;
		}

		public Builder responseDeadline(LocalDateTime responseDeadline) {
			this.responseDeadline = responseDeadline;
			return this;
		}

		public Builder venue(Venue venue) {
			this.venue = venue;
			return this;
		}

		public Builder onlineAccess(OnlineAccess onlineAccess) {
			this.onlineAccess = onlineAccess;
			return this;
		}

		public Event build() {
			if (title == null || title.isBlank()) {
				throw new BlankEventTitleException();
			}
			EventSchedule schedule;
			if (eventType == EventType.DATE_POLL) {
				validatePollFields(startsAt, endsAt, responseOptions);
				schedule = DatePollSchedule.create(datePoll, timeZone, datePollLimits);
			} else {
				if (datePoll != null) { throw new IncompatibleEventScheduleException(); }
				schedule = FixedDateSchedule.create(startsAt, endsAt, timeZone);
			}
			schedule.validateResponseDeadline(responseDeadline);
			validateModalityInvariant(modality, venue, onlineAccess);
			List<ResponseOption> resolvedResponseOptions = schedule.type() == EventType.DATE_POLL ? List.of() : buildInitialResponseOptions(responseOptions);
			if (schedule.type() == EventType.FIXED_DATE) { validateResponseOptions(resolvedResponseOptions); }
			Event event = new Event(UUID.randomUUID().toString(), hostId, title, description, schedule,
					modality, EventStatus.DRAFT, resolvedResponseOptions, resolveAllowComment(allowComment),
					resolveAllowResponseChange(allowResponseChange), responseDeadline, venue, onlineAccess, null, null,
					null);
			event.restorePublicInvitation(publicInvitation);
			return event;
		}
	}

	public static final class ReconstituteBuilder {

		private EventSchedule schedule;
		public ReconstituteBuilder schedule(EventSchedule value) { this.schedule = value; return this; }
		private String id;
		private String hostId;
		private String title;
		private String description;
		private LocalDateTime startsAt;
		private LocalDateTime endsAt;
		private String timeZone;
		private Modality modality;
		private EventStatus status;
		private List<ResponseOption> responseOptions;
		private boolean allowComment;
		private boolean allowResponseChange;
		private LocalDateTime responseDeadline;
		private Venue venue;
		private OnlineAccess onlineAccess;
		private Instant cancelledAt;
		private String cancellationReason;
		private Long version;
		private List<Guest> guests = List.of();
		private PublicInvitation publicInvitation;

		public ReconstituteBuilder publicInvitation(PublicInvitation value) { this.publicInvitation = value; return this; }

		private ReconstituteBuilder() {
		}

		public ReconstituteBuilder id(String id) {
			this.id = id;
			return this;
		}

		public ReconstituteBuilder hostId(String hostId) {
			this.hostId = hostId;
			return this;
		}

		public ReconstituteBuilder title(String title) {
			this.title = title;
			return this;
		}

		public ReconstituteBuilder description(String description) {
			this.description = description;
			return this;
		}

		public ReconstituteBuilder startsAt(LocalDateTime startsAt) {
			this.startsAt = startsAt;
			return this;
		}

		public ReconstituteBuilder endsAt(LocalDateTime endsAt) {
			this.endsAt = endsAt;
			return this;
		}

		public ReconstituteBuilder timeZone(String timeZone) {
			this.timeZone = timeZone;
			return this;
		}

		public ReconstituteBuilder modality(Modality modality) {
			this.modality = modality;
			return this;
		}

		public ReconstituteBuilder status(EventStatus status) {
			this.status = status;
			return this;
		}

		public ReconstituteBuilder responseOptions(List<ResponseOption> responseOptions) {
			this.responseOptions = responseOptions;
			return this;
		}

		public ReconstituteBuilder allowComment(boolean allowComment) {
			this.allowComment = allowComment;
			return this;
		}

		public ReconstituteBuilder allowResponseChange(boolean allowResponseChange) {
			this.allowResponseChange = allowResponseChange;
			return this;
		}

		public ReconstituteBuilder responseDeadline(LocalDateTime responseDeadline) {
			this.responseDeadline = responseDeadline;
			return this;
		}

		public ReconstituteBuilder venue(Venue venue) {
			this.venue = venue;
			return this;
		}

		public ReconstituteBuilder onlineAccess(OnlineAccess onlineAccess) {
			this.onlineAccess = onlineAccess;
			return this;
		}

		public ReconstituteBuilder cancelledAt(Instant cancelledAt) {
			this.cancelledAt = cancelledAt;
			return this;
		}

		public ReconstituteBuilder cancellationReason(String cancellationReason) {
			this.cancellationReason = cancellationReason;
			return this;
		}

		public ReconstituteBuilder guests(List<Guest> guests) {
			this.guests = guests == null ? List.of() : List.copyOf(guests);
			return this;
		}

		public ReconstituteBuilder version(Long version) {
			this.version = version;
			return this;
		}

		public Event build() {
			Event event = new Event(id, hostId, title, description, schedule == null ? FixedDateSchedule.reconstitute(startsAt, endsAt, timeZone) : schedule, modality, status,
					responseOptions, allowComment, allowResponseChange, responseDeadline, venue, onlineAccess,
					cancelledAt, cancellationReason, version);
			if (schedule instanceof DatePollSchedule) { validatePollFields(startsAt, endsAt, responseOptions); schedule.validateResponseDeadline(responseDeadline); }
			event.guests = guests;
			event.restorePublicInvitation(publicInvitation);
			return event;
		}
	}
}
