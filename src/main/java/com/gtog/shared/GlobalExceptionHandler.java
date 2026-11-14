package com.gtog.shared;

import java.net.URI;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gtog.event.domain.model.EventDomainException;
import com.gtog.event.domain.model.ResponseDeadlineNotEditableException;
import com.gtog.event.domain.model.InvitationNotFoundException;
import com.gtog.event.domain.model.DuplicateGuestEmailException;
import com.gtog.event.domain.model.DuplicateGuestPhoneException;
import com.gtog.event.domain.model.DuplicateGuestTokenException;
import com.gtog.event.domain.model.EventGuestsNotEditableException;
import com.gtog.event.domain.model.EventConcurrentModificationException;
import com.gtog.event.domain.model.GuestNotFoundException;
import com.gtog.event.domain.model.GuestResponseDeletionConfirmationRequiredException;
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

	@ExceptionHandler(InvitationNotFoundException.class)
	public ResponseEntity<ProblemDetail> handleInvitationNotFound(InvitationNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		// Una instancia constante evita que Spring copie la URI de la peticion y filtre la credencial.
		problem.setInstance(URI.create("/api/invitations"));
		return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(CacheControl.noStore()).body(problem);
	}

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

	@ExceptionHandler(GuestNotFoundException.class)
	public ProblemDetail handleGuestNotFound(GuestNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler({DuplicateGuestEmailException.class, DuplicateGuestPhoneException.class,
			DuplicateGuestTokenException.class, EventGuestsNotEditableException.class,
			EventConcurrentModificationException.class, GuestResponseDeletionConfirmationRequiredException.class})
	public ProblemDetail handleGuestConflict(EventDomainException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(ResponseDeadlineNotEditableException.class)
	public ProblemDetail handleResponseDeadlineNotEditable(ResponseDeadlineNotEditableException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Event changed concurrently; reload and retry");
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
