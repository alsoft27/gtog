package com.gtog.user.domain.port.in;

import com.gtog.user.domain.model.User;

public interface GetCurrentUserUseCase { User get(String userId); }
