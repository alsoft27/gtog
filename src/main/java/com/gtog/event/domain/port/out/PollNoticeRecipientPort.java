package com.gtog.event.domain.port.out;

import java.util.Optional;

public interface PollNoticeRecipientPort {
	Optional<String> findVerifiedEmail(String hostId);
}
