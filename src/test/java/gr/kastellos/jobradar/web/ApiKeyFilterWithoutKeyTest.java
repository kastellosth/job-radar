package gr.kastellos.jobradar.web;

import gr.kastellos.jobradar.domain.JobRepository;
import gr.kastellos.jobradar.service.IngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A server started without API_KEY must refuse every write. Its own class (not @Nested), because a nested
 * class would reuse the outer class's MockMvc, which has a key configured.
 */
@WebMvcTest(JobController.class)
@TestPropertySource(properties = "radar.api-key=")
class ApiKeyFilterWithoutKeyTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private IngestService ingest;
    @MockitoBean
    private JobRepository jobs;

    @Test
    void everyWriteIsRefusedEvenWithAnEmptyHeader() throws Exception {
        mvc.perform(post("/api/ingest").header("X-Api-Key", ""))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ingest"))
                .andExpect(status().isUnauthorized());

        verify(ingest, never()).run();
    }
}
