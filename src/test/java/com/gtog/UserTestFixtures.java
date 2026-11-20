package com.gtog;

import com.gtog.user.infrastructure.out.persistence.UserDocument;

public final class UserTestFixtures {
	private UserTestFixtures() {}
	public static UserDocument verified(String id, String name, String email, String hash, String zone) {
		UserDocument user = new UserDocument(id, name, email, hash, zone); user.setEmailVerified(true); return user;
	}
}
