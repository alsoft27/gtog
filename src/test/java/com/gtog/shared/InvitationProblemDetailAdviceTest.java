package com.gtog.shared;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

class InvitationProblemDetailAdviceTest {
	private final InvitationProblemDetailAdvice advice = new InvitationProblemDetailAdvice();

	@Test
	void replacesEveryPotentiallySensitiveFieldIncludingExtensionsOnAFutureProblem() {
		ProblemDetail original = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "secret-token");
		original.setTitle("secret-token"); original.setType(URI.create("/secret-token"));
		original.setInstance(URI.create("/api/invitations/secret-token/response"));
		original.setProperty("rejectedValue", "secret-token"); original.setProperty("path", "/secret-token");
		var response = new ServletServerHttpResponse(new MockHttpServletResponse());
		var sanitized = (ProblemDetail) advice.beforeBodyWrite(original, null, MediaType.APPLICATION_PROBLEM_JSON,
				null, new ServletServerHttpRequest(new MockHttpServletRequest("PUT", "/api/invitations/secret-token/response")), response);
		assertThat(sanitized.getStatus()).isEqualTo(409);
		assertThat(sanitized.getDetail()).isEqualTo("Invitation request conflicts with the current state");
		assertThat(sanitized.getTitle()).isEqualTo("Conflict");
		assertThat(sanitized.getType()).isEqualTo(URI.create("about:blank"));
		assertThat(sanitized.getInstance()).isEqualTo(URI.create("/api/invitations"));
		assertThat(sanitized.getProperties()).isNull();
		assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
	}

	@Test
	void leavesHostProblemsAndTheirHeadersUntouched() {
		ProblemDetail original = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Host domain detail");
		original.setInstance(URI.create("/api/events/event-id")); original.setProperty("code", "CAPACITY_EXCEEDED");
		var response = new ServletServerHttpResponse(new MockHttpServletResponse());
		assertThat(advice.beforeBodyWrite(original, null, MediaType.APPLICATION_PROBLEM_JSON, null,
				new ServletServerHttpRequest(new MockHttpServletRequest("PUT", "/api/events/event-id")), response)).isSameAs(original);
		assertThat(original.getInstance()).isEqualTo(URI.create("/api/events/event-id"));
		assertThat(original.getProperties()).containsEntry("code", "CAPACITY_EXCEEDED");
		assertThat(response.getHeaders().isEmpty()).isTrue();
	}

	@Test
	void leavesSuccessfulInvitationBodiesUntouched() {
		Object body = new Object();
		var response = new ServletServerHttpResponse(new MockHttpServletResponse());
		assertThat(advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null,
				new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/api/invitations/secret-token")), response)).isSameAs(body);
		assertThat(response.getHeaders().isEmpty()).isTrue();
	}
}
