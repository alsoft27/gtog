package com.gtog.user.infrastructure.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import com.gtog.user.infrastructure.config.AuthProperties;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.gtog.user.infrastructure.out.persistence.AuthTokenDocument;
import com.gtog.event.infrastructure.out.mail.CapturingEmailSenderAdapter;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = { "app.auth.auto-verify-email=true", "app.public-base-url=http://localhost:4200" })
@ActiveProfiles("test") @AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LocalVerificationBypassIntegrationTest {
	private final MockMvc mvc;
	private final MongoTemplate mongo;
	private final CapturingEmailSenderAdapter mail;
	private final AuthProperties properties;
	LocalVerificationBypassIntegrationTest(MockMvc mvc, MongoTemplate mongo, CapturingEmailSenderAdapter mail, AuthProperties properties) {
		this.mvc = mvc; this.mongo = mongo; this.mail = mail; this.properties = properties;
	}
	@BeforeEach void setUp() { mongo.remove(new Query(), UserDocument.class); mongo.remove(new Query(), AuthTokenDocument.class); mail.reset(); }
	@Test void explicitBypassVerifiesTheNewAccountWithoutTokenOrMail() throws Exception {
		assertThat(properties.isAutoVerifyEmail()).isTrue();
		var csrf = mvc.perform(get("/actuator/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		mvc.perform(post("/api/auth/register").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
				.content("{\"name\":\"Local\",\"email\":\"local@example.com\",\"rawPassword\":\"password12\",\"timeZone\":\"UTC\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.emailVerified").value(true));
		assertThat(mongo.findAll(UserDocument.class)).singleElement().satisfies(user -> assertThat(user.isEmailVerified()).isTrue());
		assertThat(mongo.count(new Query(), AuthTokenDocument.class)).isZero(); assertThat(mail.attempts()).isEmpty();
	}
}
