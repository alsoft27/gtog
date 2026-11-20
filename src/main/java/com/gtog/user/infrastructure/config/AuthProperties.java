package com.gtog.user.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public class AuthProperties {
	private boolean autoVerifyEmail;
	private Duration verificationTtl = Duration.ofHours(24);
	private Duration mailCooldown = Duration.ofMinutes(5);
	private int mailMaxPerDay = 5;
	private String verifyEmailPath = "/verify-email";
	private final MailExecutor mailExecutor = new MailExecutor();
	public boolean isAutoVerifyEmail() { return autoVerifyEmail; }
	public void setAutoVerifyEmail(boolean value) { autoVerifyEmail = value; }
	public Duration getVerificationTtl() { return verificationTtl; }
	public void setVerificationTtl(Duration value) { verificationTtl = value; }
	public Duration getMailCooldown() { return mailCooldown; }
	public void setMailCooldown(Duration value) { mailCooldown = value; }
	public int getMailMaxPerDay() { return mailMaxPerDay; }
	public void setMailMaxPerDay(int value) { mailMaxPerDay = value; }
	public String getVerifyEmailPath() { return verifyEmailPath; }
	public void setVerifyEmailPath(String value) { verifyEmailPath = value; }
	public MailExecutor getMailExecutor() { return mailExecutor; }
	public static class MailExecutor {
		private int coreSize = 2;
		private int maxSize = 4;
		private int queueCapacity = 100;
		public int getCoreSize() { return coreSize; }
		public void setCoreSize(int value) { coreSize = value; }
		public int getMaxSize() { return maxSize; }
		public void setMaxSize(int value) { maxSize = value; }
		public int getQueueCapacity() { return queueCapacity; }
		public void setQueueCapacity(int value) { queueCapacity = value; }
	}
}
