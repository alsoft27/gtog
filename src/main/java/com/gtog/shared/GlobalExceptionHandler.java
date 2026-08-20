package com.gtog.shared;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gtog.event.domain.model.EventDomainException;
import com.gtog.event.domain.model.EventNotEditableException;
import com.gtog.event.domain.model.EventNotFoundException;

// Todas las excepciones de dominio heredan de EventDomainException; el dominio no sabe de codigos HTTP.
// Este advice es el unico responsable de mapear cada una a su codigo: los @ExceptionHandler mas especificos
// (404, 409) ganan sobre el generico de EventDomainException (422) para el resto de subtipos.
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(EventDomainException.class)
	public ProblemDetail handleEventDomainException(EventDomainException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
	}

	@ExceptionHandler(EventNotFoundException.class)
	public ProblemDetail handleEventNotFoundException(EventNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(EventNotEditableException.class)
	public ProblemDetail handleEventNotEditableException(EventNotEditableException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}
}
