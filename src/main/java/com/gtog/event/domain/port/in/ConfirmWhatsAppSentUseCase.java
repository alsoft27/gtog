package com.gtog.event.domain.port.in;

import java.util.List;
import com.gtog.event.domain.model.WhatsAppConfirmationResult;

public interface ConfirmWhatsAppSentUseCase {
	List<WhatsAppConfirmationResult> confirm(ConfirmWhatsAppSentCommand command);
}
