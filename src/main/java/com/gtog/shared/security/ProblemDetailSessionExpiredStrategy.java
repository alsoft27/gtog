package com.gtog.shared.security;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailSessionExpiredStrategy implements SessionInformationExpiredStrategy {
	private final SecurityProblemWriter writer;

	public ProblemDetailSessionExpiredStrategy(SecurityProblemWriter writer) { this.writer = writer; }

	@Override public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
		writer.write(event.getResponse(), HttpStatus.UNAUTHORIZED, "Authentication required");
	}
}
