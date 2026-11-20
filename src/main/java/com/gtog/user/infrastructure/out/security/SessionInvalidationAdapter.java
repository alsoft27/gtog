package com.gtog.user.infrastructure.out.security;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import com.gtog.user.domain.port.out.SessionInvalidationPort;

@Component
public class SessionInvalidationAdapter implements SessionInvalidationPort {
	private final SessionRegistry registry;

	public SessionInvalidationAdapter(SessionRegistry registry) { this.registry = registry; }

	@Override public void invalidateAll(String userId) {
		// El filtro de sesiones expiradas invalida HttpSession y devuelve 401 en el siguiente acceso.
		for (Object principal : registry.getAllPrincipals()) {
			if (principal instanceof AuthenticatedUser user && user.getUserId().equals(userId)) {
				registry.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
			}
		}
	}
}
