package com.gtog.event.domain.model;

public class ResponseChangeNotAllowedException extends GuestResponseException {
	public ResponseChangeNotAllowedException() { super("Response changes are not allowed"); }
}
