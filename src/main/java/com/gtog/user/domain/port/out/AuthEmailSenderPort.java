package com.gtog.user.domain.port.out;

import com.gtog.user.domain.model.AuthEmail;

public interface AuthEmailSenderPort { boolean send(AuthEmail email); }
