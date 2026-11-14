package com.gtog.event.domain.model;

public class ResponseCommentNotAllowedException extends GuestResponseException {
	public ResponseCommentNotAllowedException() { super("Comments are not allowed for this event"); }
}
