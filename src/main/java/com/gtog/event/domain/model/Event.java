package com.gtog.event.domain.model;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
	private final Long version;

	private Event(String id, String hostId, String title, String description, LocalDateTime startsAt,
			LocalDateTime endsAt, String timeZone, Modality modality, EventStatus status,
			List<ResponseOption> responseOptions, boolean allowComment, boolean allowResponseChange,
			LocalDateTime responseDeadline, Long version) {
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
		this.version = version;
	}

	public static Event create(String hostId, String title, String description, LocalDateTime startsAt,
			LocalDateTime endsAt, String timeZone, Modality modality, List<ResponseOptionDraft> responseOptionDrafts,
			Boolean allowComment, Boolean allowResponseChange, LocalDateTime responseDeadline) {
		if (title == null || title.isBlank()) {
			throw new BlankEventTitleException();
		}
		ZoneId zoneId = parseTimeZone(timeZone);
		if (!endsAt.atZone(zoneId).toInstant().isAfter(startsAt.atZone(zoneId).toInstant())) {
			throw new InvalidEventPeriodException(startsAt, endsAt);
		}
		validateResponseDeadline(responseDeadline, startsAt);
		List<ResponseOption> responseOptions = buildInitialResponseOptions(responseOptionDrafts);
		validateResponseOptions(responseOptions);
		return new Event(UUID.randomUUID().toString(), hostId, title, description, startsAt, endsAt, timeZone,
				modality, EventStatus.DRAFT, responseOptions, resolveAllowComment(allowComment),
				resolveAllowResponseChange(allowResponseChange), responseDeadline, null);
	}

	// Rehidrata un evento ya persistido: los datos vienen validados desde su creación, no se repite la validación aquí.
	public static Event reconstitute(String id, String hostId, String title, String description,
			LocalDateTime startsAt, LocalDateTime endsAt, String timeZone, Modality modality, EventStatus status,
			List<ResponseOption> responseOptions, boolean allowComment, boolean allowResponseChange,
			LocalDateTime responseDeadline, Long version) {
		return new Event(id, hostId, title, description, startsAt, endsAt, timeZone, modality, status,
				responseOptions, allowComment, allowResponseChange, responseDeadline, version);
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

	public Long getVersion() {
		return version;
	}
}
