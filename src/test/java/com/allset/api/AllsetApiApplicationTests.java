package com.allset.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"jwt-secret=test-secret-test-secret-test-secret-1234",
		"cpf-encryption-key=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
		"database-url=jdbc:postgresql://placeholder/test",
		"db-user=test",
		"db-pass=test",
		"redis-host=localhost",
		"redis-port=6379",
		"port=8080",
		"user-purge-cron=0 0 2 * * *",
		"push-token-prune-cron=0 0 3 * * *",
		"review-publication-cron=0 0 * * * *",
		"resend-api-key=test-key",
		"email-from=test@example.com",
		"minio.endpoint=http://test:9000",
		"minio.public-endpoint=http://test:9000",
		"minio.access-key=test",
		"minio.secret-key=testsecret",
		"minio.auto-create-buckets=false"
})
class AllsetApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
