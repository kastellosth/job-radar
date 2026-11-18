package gr.kastellos.jobradar.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A throw-away PostgreSQL for integration tests. Spring caches the test context,
 * so test classes with the same setup share one container instead of starting their own.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    /** Same image as docker-compose.yml, so tests run against the database the app really uses. */
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}
