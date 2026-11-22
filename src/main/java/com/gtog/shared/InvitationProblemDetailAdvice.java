package com.gtog.shared;

import java.net.URI;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class InvitationProblemDetailAdvice implements ResponseBodyAdvice<Object> {

	// Solo textos constantes del contrato público; cualquier detalle nuevo recibe uno genérico.
	private static final Set<String> SAFE_DETAILS = Set.of(
			"Invitation not found", "Calendar is not available for a cancelled event",
			"Unable to generate invitation calendar", "Unable to store guest response",
			"Event is not accepting responses at this time", "Response changes are not allowed",
			"Response option does not belong to this event", "Comments are not allowed for this event",
			"Response comment must not exceed 500 characters", "Response email is invalid",
			"Invalid request body", "Authentication required", "Access denied", "Resource not found",
			"Method not allowed", "Not acceptable", "Request body too large", "Unsupported media type",
			"Internal server error", "Service temporarily unavailable", "Invalid request");

	@Override
	public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
		// ResponseEntity<Object> también puede contener ProblemDetail: importa el cuerpo, no el tipo declarado.
		return true;
	}

	@Override
	public @Nullable Object beforeBodyWrite(@Nullable Object body, MethodParameter returnType, MediaType contentType,
			Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request, ServerHttpResponse response) {
		if (!(body instanceof ProblemDetail problem) || !request.getURI().getPath().startsWith("/api/invitations/")) {
			return body;
		}
		String detail = problem.getDetail();
		ProblemDetail sanitized = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(problem.getStatus()),
				detail != null && SAFE_DETAILS.contains(detail) ? detail : genericDetail(problem.getStatus()));
		// Un cuerpo nuevo elimina también title, type y extensiones que pudieran repetir datos de la petición.
		sanitized.setInstance(URI.create("/api/invitations"));
		sanitized.setType(URI.create("about:blank"));
		response.getHeaders().setCacheControl("no-store");
		response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		return sanitized;
	}

	private static String genericDetail(int status) {
		return switch (status) {
			case 400 -> "Invalid request body";
			case 401 -> "Authentication required";
			case 403 -> "Access denied";
			case 404 -> "Invitation not found";
			case 405 -> "Method not allowed";
			case 406 -> "Not acceptable";
			case 409 -> "Invitation request conflicts with the current state";
			case 415 -> "Unsupported media type";
			case 422 -> "Invalid invitation request";
			default -> status >= 500 ? "Internal server error" : "Invalid request";
		};
	}
}
