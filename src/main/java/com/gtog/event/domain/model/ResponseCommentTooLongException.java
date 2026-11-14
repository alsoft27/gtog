package com.gtog.event.domain.model;

public class ResponseCommentTooLongException extends GuestResponseException {
	public ResponseCommentTooLongException() { super("Response comment must not exceed 500 characters"); }
}
