package com.gtog.user.domain.port.out;

import java.time.Instant;
import java.util.Optional;
import com.gtog.user.domain.model.AuthToken;
import com.gtog.user.domain.model.AuthTokenPurpose;

public interface AuthTokenRepositoryPort {
	void replace(AuthToken token);
	Optional<AuthToken> consume(String hash, AuthTokenPurpose purpose, Instant now);
}
