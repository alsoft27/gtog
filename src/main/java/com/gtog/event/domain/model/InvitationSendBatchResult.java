package com.gtog.event.domain.model;

import java.util.List;

public record InvitationSendBatchResult(List<GuestEmailSendResult> results, HostSendQuota dailyLimit) {
	public InvitationSendBatchResult { results = List.copyOf(results); }
}
