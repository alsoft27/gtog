package com.gtog.event.domain.port.in;

import com.gtog.event.domain.model.PublicInvitationPreview;

public interface PreviewPublicInvitationUseCase {
	PublicInvitationPreview preview(String joinToken, String previousGuestToken);
}
