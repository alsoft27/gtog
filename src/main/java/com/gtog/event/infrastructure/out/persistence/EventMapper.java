package com.gtog.event.infrastructure.out.persistence;

import org.springframework.stereotype.Component;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.Modality;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.ResponseOption;

@Component
public class EventMapper {

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
				event.getVersion());
	}

	public Event toDomain(EventDocument document) {
		return Event.reconstitute(
				document.getId(),
				document.getHostId(),
				document.getTitle(),
				document.getDescription(),
				document.getStartsAt(),
				document.getEndsAt(),
				document.getTimeZone(),
				Modality.valueOf(document.getModality()),
				EventStatus.valueOf(document.getStatus()),
				document.getResponseOptions().stream().map(this::toDomain).toList(),
				document.isAllowComment(),
				document.isAllowResponseChange(),
				document.getResponseDeadline(),
				document.getVersion());
	}

	private ResponseOptionDocument toDocument(ResponseOption responseOption) {
		return new ResponseOptionDocument(responseOption.id(), responseOption.label(),
				responseOption.countsAsAttendance());
	}

	private ResponseOption toDomain(ResponseOptionDocument document) {
		return new ResponseOption(document.getId(), document.getLabel(), document.isCountsAsAttendance());
	}
}
