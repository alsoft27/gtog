package com.gtog.shared;

import java.net.URI;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class MvcExceptionHandler extends ResponseEntityExceptionHandler {
	private static final Logger LOG = LoggerFactory.getLogger(MvcExceptionHandler.class);

	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (request instanceof ServletWebRequest servlet && servlet.getResponse() != null && servlet.getResponse().isCommitted()) {
			return null;
		}
		// Nunca reutilizar el body de ErrorResponse: puede llevar ruta, valores rechazados o extensiones con el token.
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail(status.value()));
		String path = request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : "";
		boolean invitation = path.equals("/api/invitations") || path.startsWith("/api/invitations/");
		problem.setInstance(URI.create(invitation ? "/api/invitations" : "/api/errors"));
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.putAll(headers);
		responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		responseHeaders.setCacheControl("no-store");
		return new ResponseEntity<>(problem, responseHeaders, status);
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		// Se conserva el 400 "Invalid request body" sin exponer el mensaje original de Jackson.
		return handleExceptionInternal(exception, null, headers, status, request);
	}

	@ExceptionHandler(Exception.class)
	public @Nullable ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
		String correlationId = UUID.randomUUID().toString();
		// Sin mensaje, causa, stack trace ni URL: cualquiera de ellos puede contener la credencial del invitado.
		LOG.error("Unhandled exception type={} correlationId={}", exception.getClass().getName(), correlationId);
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Correlation-ID", correlationId);
		return handleExceptionInternal(exception, null, headers, HttpStatus.INTERNAL_SERVER_ERROR, request);
	}

	private static String detail(int status) {
		return switch (status) {
			case 400 -> "Invalid request body";
			case 401 -> "Authentication required";
			case 403 -> "Access denied";
			case 404 -> "Resource not found";
			case 405 -> "Method not allowed";
			case 406 -> "Not acceptable";
			case 413 -> "Request body too large";
			case 415 -> "Unsupported media type";
			case 500 -> "Internal server error";
			case 503 -> "Service temporarily unavailable";
			default -> status >= 500 ? "Internal server error" : "Invalid request";
		};
	}
}
