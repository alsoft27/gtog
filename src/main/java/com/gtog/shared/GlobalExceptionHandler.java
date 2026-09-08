package com.gtog.shared;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gtog.event.domain.model.EventDomainException;
import com.gtog.event.domain.model.EventNotCancellableException;
import com.gtog.event.domain.model.EventNotEditableException;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.model.EventNotPublishableException;
import com.gtog.event.domain.model.ModalityConflictException;
import com.gtog.user.domain.model.EmailAlreadyRegisteredException;
import com.gtog.user.domain.model.UserDomainException;

// Todas las excepciones de dominio heredan de EventDomainException o UserDomainException; el dominio no sabe de codigos HTTP.
// Este advice es el unico responsable de mapear cada una a su codigo: los @ExceptionHandler mas especificos
// (404, 409) ganan sobre el generico de *DomainException (422) para el resto de subtipos.
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

	@ExceptionHandler(EventNotPublishableException.class)
	public ProblemDetail handleEventNotPublishableException(EventNotPublishableException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(EventNotCancellableException.class)
	public ProblemDetail handleEventNotCancellableException(EventNotCancellableException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(ModalityConflictException.class)
	public ProblemDetail handleModalityConflictException(ModalityConflictException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(UserDomainException.class)
	public ProblemDetail handleUserDomainException(UserDomainException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	public ProblemDetail handleEmailAlreadyRegisteredException(EmailAlreadyRegisteredException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ProblemDetail handleBadCredentials(BadCredentialsException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
	}
}
