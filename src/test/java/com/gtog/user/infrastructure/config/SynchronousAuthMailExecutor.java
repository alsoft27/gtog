package com.gtog.user.infrastructure.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("authMailExecutor")
@Profile("test")
public class SynchronousAuthMailExecutor implements Executor {
	@Override public void execute(Runnable command) { command.run(); }
}
