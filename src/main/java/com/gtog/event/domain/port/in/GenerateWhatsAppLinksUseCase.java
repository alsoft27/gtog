package com.gtog.event.domain.port.in;

import java.util.List;
import com.gtog.event.domain.model.WhatsAppLinkResult;

public interface GenerateWhatsAppLinksUseCase {
	List<WhatsAppLinkResult> generate(GenerateWhatsAppLinksCommand command);
}
