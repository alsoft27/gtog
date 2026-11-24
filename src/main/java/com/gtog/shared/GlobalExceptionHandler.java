package com.gtog.shared;

import com.gtog.event.domain.model.PublicInvitationNotEditableException;
import com.gtog.event.domain.model.PublicJoinConflictException;
import com.gtog.event.domain.model.PublicJoinValidationException;

import java.net.URI;
import com.gtog.event.domain.model.GuestResponseException;
import com.gtog.event.domain.model.GuestResponseStorageException;
import com.gtog.event.domain.model.ResponseNotAcceptedException;
import com.gtog.event.domain.model.ResponseChangeNotAllowedException;
import com.gtog.event.domain.model.ResponseOptionDeletionNotAllowedException;
import com.gtog.event.domain.model.ResponseOptionAttendanceChangeNotAllowedException;
import com.gtog.event.domain.model.InvitationCalendarNotAvailableException;
import com.gtog.event.domain.model.InvitationCalendarGenerationException;
import org.springframework.http.MediaType;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gtog.event.domain.model.EventDomainException;
import com.gtog.event.domain.model.EventScheduleConflictException;
import com.gtog.event.domain.model.StalePollRevisionException;
import com.gtog.event.domain.model.EventInvitationSendingNotAllowedException;
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
import com.gtog.user.domain.model.InvalidAuthTokenException;
import com.gtog.user.domain.model.AuthAccountUnavailableException;
import com.gtog.event.domain.model.EmailNotVerifiedException;

// Todas las excepciones de dominio heredan de EventDomainException o UserDomainException; el dominio no sabe de codigos HTTP.
// Este advice traduce las excepciones de dominio: los @ExceptionHandler mas especificos
// (404, 409) ganan sobre el generico de *DomainException (422) para el resto de subtipos.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(StoredDataIntegrityException.class)
	public ResponseEntity<ProblemDetail> handleStoredDataIntegrity(StoredDataIntegrityException exception) {
		String correlationId = java.util.UUID.randomUUID().toString();
		LOG.error("STORED_DATA_INTEGRITY_FAILURE type={} correlationId={} eventId={}",
				exception.getFailureType(), correlationId, exception.getEventId());
		return ResponseEntity.internalServerError().header("X-Correlation-ID", correlationId)
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"));
	}

	@ExceptionHandler(com.gtog.event.domain.model.GuestCapacityExceededException.class)
	public ResponseEntity<ProblemDetail> handleGuestCapacity(com.gtog.event.domain.model.GuestCapacityExceededException exception, jakarta.servlet.http.HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Guest capacity exceeded");
		problem.setProperty("code", "CAPACITY_EXCEEDED");
		return isGuestBatch(request) ? batchProblem(problem) : ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}
	@ExceptionHandler(com.gtog.event.domain.model.GuestBatchRejectedException.class)
	public ResponseEntity<ProblemDetail> handleGuestBatch(com.gtog.event.domain.model.GuestBatchRejectedException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.isConflict() ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY, "Guest batch rejected");
		problem.setProperty("errors", exception.getErrors().stream().map(error -> java.util.Map.of("index", error.index(), "code", error.code().name())).toList());
		return batchProblem(problem);
	}
	private boolean isGuestBatch(jakarta.servlet.http.HttpServletRequest request) {
		return request.getRequestURI().startsWith("/api/events/") && request.getRequestURI().endsWith("/guests/batch");
	}
	private ResponseEntity<ProblemDetail> batchProblem(ProblemDetail problem) {
		problem.setInstance(URI.create("/api/events/guests/batch"));
		return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).cacheControl(CacheControl.noStore()).body(problem);
	}
	@ExceptionHandler(EmailNotVerifiedException.class)
	public ResponseEntity<ProblemDetail> handleEmailNotVerified(EmailNotVerifiedException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Email verification required");
		problem.setInstance(URI.create("/api/events")); problem.setProperty("code", "EMAIL_NOT_VERIFIED");
		return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_PROBLEM_JSON).cacheControl(CacheControl.noStore()).body(problem);
	}
	@ExceptionHandler(InvalidAuthTokenException.class)
	public ResponseEntity<ProblemDetail> handleAuthToken(InvalidAuthTokenException exception) { return authProblem(HttpStatus.BAD_REQUEST, "Invalid or expired token"); }
	@ExceptionHandler(AuthAccountUnavailableException.class)
	public ResponseEntity<ProblemDetail> handleAuthAccount(AuthAccountUnavailableException exception) { return authProblem(HttpStatus.UNAUTHORIZED, "Authentication required"); }
	private ResponseEntity<ProblemDetail> authProblem(HttpStatus status, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail); problem.setInstance(URI.create("/api/auth"));
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).cacheControl(CacheControl.noStore()).body(problem);
	}

	@ExceptionHandler(InvitationNotFoundException.class)
	public ResponseEntity<ProblemDetail> handleInvitationNotFound(InvitationNotFoundException exception) {
		return invitationProblem(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(InvitationCalendarNotAvailableException.class)
	public ResponseEntity<ProblemDetail> handleCancelledCalendar(InvitationCalendarNotAvailableException exception) {
		return invitationProblem(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(InvitationCalendarGenerationException.class)
	public ResponseEntity<ProblemDetail> handleCalendarGeneration(InvitationCalendarGenerationException exception) {
		return invitationProblem(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
	}

	@ExceptionHandler(GuestResponseException.class)
	public ResponseEntity<ProblemDetail> handleGuestResponse(GuestResponseException exception) {
		HttpStatus status = exception instanceof ResponseNotAcceptedException || exception instanceof ResponseChangeNotAllowedException
				? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
		return invitationProblem(status, exception.getMessage());
	}

	@ExceptionHandler(GuestResponseStorageException.class)
	public ResponseEntity<ProblemDetail> handleResponseStorage(GuestResponseStorageException exception) {
		return invitationProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store guest response");
	}

	private ResponseEntity<ProblemDetail> invitationProblem(HttpStatus status, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setInstance(URI.create("/api/invitations"));
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.cacheControl(CacheControl.noStore()).body(problem);
	}

	@ExceptionHandler(EventDomainException.class)
	public ProblemDetail handleEventDomainException(EventDomainException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
	}

	@ExceptionHandler(EventNotFoundException.class)
	public ResponseEntity<ProblemDetail> handleEventNotFoundException(EventNotFoundException exception, jakarta.servlet.http.HttpServletRequest request) {
		if (isGuestBatch(request)) { return batchProblem(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Event not found")); }
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage()));
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

	@ExceptionHandler(PublicJoinConflictException.class)
	public ProblemDetail handlePublicJoinConflict(PublicJoinConflictException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Public invitation request conflicts with the current state");
		problem.setProperty("code", exception.getCode().name());
		return problem;
	}

	@ExceptionHandler(PublicJoinValidationException.class)
	public ProblemDetail handlePublicJoinValidation(PublicJoinValidationException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid public invitation registration");
		problem.setProperty("errors", exception.getErrors().stream().map(error -> java.util.Map.of("field", error.field(), "code", error.code().name())).toList());
		return problem;
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
	public ResponseEntity<ProblemDetail> handleGuestConflict(EventDomainException exception, jakarta.servlet.http.HttpServletRequest request) {
		if (isGuestBatch(request)) { return batchProblem(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Guest batch rejected")); }
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage()));
	}

	@ExceptionHandler({StalePollRevisionException.class, EventScheduleConflictException.class, PublicInvitationNotEditableException.class, EventInvitationSendingNotAllowedException.class, ResponseDeadlineNotEditableException.class, ResponseOptionDeletionNotAllowedException.class,
			ResponseOptionAttendanceChangeNotAllowedException.class})
	public ProblemDetail handleEventConfigurationConflict(EventDomainException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Event changed concurrently; reload and retry");
	}

	@ExceptionHandler(UserDomainException.class)
	public ResponseEntity<ProblemDetail> handleUserDomainException(UserDomainException exception, jakarta.servlet.http.HttpServletRequest request) {
		String path = request.getRequestURI();
		if (path.equals("/api/auth/forgot-password") || path.equals("/api/auth/reset-password")) {
			return authProblem(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
		}
		return ResponseEntity.unprocessableContent().body(ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage()));
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
