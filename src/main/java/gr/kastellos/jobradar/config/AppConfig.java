package gr.kastellos.jobradar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** Injected instead of calling Instant.now() directly, so tests can freeze time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
