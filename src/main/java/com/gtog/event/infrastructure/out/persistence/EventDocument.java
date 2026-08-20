package com.gtog.event.infrastructure.out.persistence;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("events")
public class EventDocument {

	@Id
	private final String id;
	private final String hostId;
	private final String title;
	private final String description;
	private final LocalDateTime startsAt;
	private final LocalDateTime endsAt;
	private final String timeZone;
	private final String modality;
	private final String status;
	private final List<ResponseOptionDocument> responseOptions;
	private final boolean allowComment;
	private final boolean allowResponseChange;
	private final LocalDateTime responseDeadline;
	@Version
	private final Long version;

	public EventDocument(String id, String hostId, String title, String description, LocalDateTime startsAt,
			LocalDateTime endsAt, String timeZone, String modality, String status,
			List<ResponseOptionDocument> responseOptions, boolean allowComment, boolean allowResponseChange,
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

	public String getModality() {
		return modality;
	}

	public String getStatus() {
		return status;
	}

	public List<ResponseOptionDocument> getResponseOptions() {
		return responseOptions;
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
