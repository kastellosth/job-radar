package gr.kastellos.jobradar.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Requests that change data (anything but GET/HEAD/OPTIONS under /api) need the X-Api-Key header.
 * One shared secret for one user, so a filter is enough: Spring Security would bring sessions, CSRF and
 * a login page this app has no use for.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);
    private static final Set<String> READ_ONLY = Set.of("GET", "HEAD", "OPTIONS");

    /** Null when no key is configured: then every write is refused, never allowed. */
    private final byte[] expected;
    private final ObjectMapper json;

    public ApiKeyFilter(@Value("${radar.api-key:}") String apiKey, ObjectMapper json) {
        this.expected = apiKey == null || apiKey.isBlank() ? null : apiKey.getBytes(StandardCharsets.UTF_8);
        this.json = json;
        if (expected == null) {
            log.warn("API_KEY is not set: all POST/PATCH requests will be refused with 401");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || READ_ONLY.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (matches(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Missing or wrong " + HEADER + " header");
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "ApiKey header=\"" + HEADER + "\"");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }

    /** Constant-time comparison, so response timing does not reveal how much of a guess was right. */
    private boolean matches(String given) {
        return expected != null && given != null
                && MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8));
    }
}
