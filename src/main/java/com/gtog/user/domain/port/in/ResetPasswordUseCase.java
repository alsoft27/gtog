package com.gtog.user.domain.port.in;

public interface ResetPasswordUseCase {
	void reset(String token, String newPassword);
}
