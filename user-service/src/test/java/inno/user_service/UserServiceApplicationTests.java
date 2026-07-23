package inno.user_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL",
		"spring.liquibase.enabled=false"
})
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
