package com.gtog.event.domain.model;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Event {

	private final String id;
	private final String hostId;
	private final String title;
	private final String description;
	private final LocalDateTime startsAt;
	private final LocalDateTime endsAt;
	private final String timeZone;
	private final Modality modality;
	private EventStatus status;
	private List<ResponseOption> responseOptions;
	private boolean allowComment;
	private boolean allowResponseChange;
	private LocalDateTime responseDeadline;
	private Venue venue;
	private OnlineAccess onlineAccess;
	private final Long version;

	private Event(String id, String hostId, String title, String description, LocalDateTime startsAt,
			LocalDateTime endsAt, String timeZone, Modality modality, EventStatus status,
			List<ResponseOption> responseOptions, boolean allowComment, boolean allowResponseChange,
			LocalDateTime responseDeadline, Venue venue, OnlineAccess onlineAccess, Long version) {
		this.id = id;
		this.hostId = hostId;
		this.title = title;
		this.description = description;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.timeZone = timeZone;
		this.modality = modality;
		this.status = status;
		this.responseOptions = responseOptions;
		this.allowComment = allowComment;
		this.allowResponseChange = allowResponseChange;
		this.responseDeadline = responseDeadline;
		this.venue = venue;
		this.onlineAccess = onlineAccess;
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

	public void replaceResponseOptions(List<ResponseOptionEdit> edits, boolean allowComment,
			boolean allowResponseChange, LocalDateTime responseDeadline) {
		if (status != EventStatus.DRAFT) {
			throw new EventNotEditableException(id);
		}
		validateResponseDeadline(responseDeadline, startsAt);
		List<ResponseOption> merged = edits.stream().map(this::resolveEdit).toList();
		validateResponseOptions(merged);
		this.responseOptions = merged;
		this.allowComment = allowComment;
		this.allowResponseChange = allowResponseChange;
		this.responseDeadline = responseDeadline;
	}

	public void replaceVenue(Venue venue) {
		if (status != EventStatus.DRAFT) {
			throw new EventNotEditableException(id);
		}
		if (modality != Modality.IN_PERSON) {
			throw new UnexpectedVenueException();
		}
		this.venue = venue;
	}

	public void replaceOnlineAccess(OnlineAccess onlineAccess) {
		if (status != EventStatus.DRAFT) {
			throw new EventNotEditableException(id);
		}
		if (modality != Modality.ONLINE) {
			throw new UnexpectedOnlineAccessException();
		}
		this.onlineAccess = onlineAccess;
	}

	// Vacio si el evento no tiene acceso en linea (presencial) o si la regla de LinkVisibility no deja verlo
	// todavia: asi quien llama no puede olvidarse de comprobar un booleano antes de leer el enlace.
	public Optional<OnlineAccess> visibleOnlineAccess(boolean guestHasConfirmed, Instant now) {
		if (onlineAccess == null) {
			return Optional.empty();
		}
		boolean visible = switch (onlineAccess.linkVisibility()) {
			case ALWAYS -> true;
			case ON_CONFIRMATION -> guestHasConfirmed;
			case HOURS_BEFORE -> !now.isBefore(startsAtInstant().minus(onlineAccess.hoursBefore(), ChronoUnit.HOURS));
		};
		return visible ? Optional.of(onlineAccess) : Optional.empty();
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

	private static void validateResponseDeadline(LocalDateTime responseDeadline, LocalDateTime startsAt) {
		if (responseDeadline != null && responseDeadline.isAfter(startsAt)) {
			throw new InvalidResponseDeadlineException(responseDeadline, startsAt);
		}
	}

	private static boolean resolveAllowComment(Boolean allowComment) {
		return allowComment != null && allowComment;
	}

	private static boolean resolveAllowResponseChange(Boolean allowResponseChange) {
		return allowResponseChange == null || allowResponseChange;
	}

	private static ZoneId parseTimeZone(String timeZone) {
		try {
			return ZoneId.of(timeZone);
		} catch (DateTimeException e) {
			throw new InvalidTimeZoneException(timeZone);
		}
	}

	// El instante se calcula al vuelo a partir de la hora local: si cambian las reglas horarias de la zona
	// antes de la fecha del evento, este cálculo siempre refleja la hora local prevista, no un instante congelado.
	public Instant startsAtInstant() {
		return startsAt.atZone(ZoneId.of(timeZone)).toInstant();
	}

	public Instant endsAtInstant() {
		return endsAt.atZone(ZoneId.of(timeZone)).toInstant();
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
		return startsAt;
	}

	public LocalDateTime getEndsAt() {
		return endsAt;
	}

	public String getTimeZone() {
		return timeZone;
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
			ZoneId zoneId = parseTimeZone(timeZone);
			if (!endsAt.atZone(zoneId).toInstant().isAfter(startsAt.atZone(zoneId).toInstant())) {
				throw new InvalidEventPeriodException(startsAt, endsAt);
			}
			validateResponseDeadline(responseDeadline, startsAt);
			validateModalityInvariant(modality, venue, onlineAccess);
			List<ResponseOption> resolvedResponseOptions = buildInitialResponseOptions(responseOptions);
			validateResponseOptions(resolvedResponseOptions);
			return new Event(UUID.randomUUID().toString(), hostId, title, description, startsAt, endsAt, timeZone,
					modality, EventStatus.DRAFT, resolvedResponseOptions, resolveAllowComment(allowComment),
					resolveAllowResponseChange(allowResponseChange), responseDeadline, venue, onlineAccess, null);
		}
	}

	public static final class ReconstituteBuilder {

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
		private Long version;

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

		public ReconstituteBuilder version(Long version) {
			this.version = version;
			return this;
		}

		public Event build() {
			return new Event(id, hostId, title, description, startsAt, endsAt, timeZone, modality, status,
					responseOptions, allowComment, allowResponseChange, responseDeadline, venue, onlineAccess,
					version);
		}
	}
}
