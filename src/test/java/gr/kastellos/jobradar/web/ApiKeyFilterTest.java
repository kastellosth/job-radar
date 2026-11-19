package gr.kastellos.jobradar.web;

import gr.kastellos.jobradar.domain.JobRepository;
import gr.kastellos.jobradar.service.IngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The filter in front of the real controller, with the service and repository mocked. */
@WebMvcTest(JobController.class)
@TestPropertySource(properties = "radar.api-key=test-key")
class ApiKeyFilterTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private IngestService ingest;
    @MockitoBean
    private JobRepository jobs;

    @Test
    void postWithoutKeyIsRefusedWithAProblemDetail() throws Exception {
        mvc.perform(post("/api/ingest"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "ApiKey header=\"X-Api-Key\""))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Missing or wrong X-Api-Key header"))
                .andExpect(jsonPath("$.instance").value("/api/ingest"));

        verify(ingest, never()).run();
    }

    @Test
    void postWithWrongKeyIsRefused() throws Exception {
        mvc.perform(post("/api/ingest").header("X-Api-Key", "test-kez"))
                .andExpect(status().isUnauthorized());

        verify(ingest, never()).run();
    }

    @Test
    void postWithRightKeyRunsTheIngest() throws Exception {
        when(ingest.run()).thenReturn(new IngestService.IngestReport(1, 1, 1, 0));

        mvc.perform(post("/api/ingest").header("X-Api-Key", "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(1));
    }

    @Test
    void patchWithoutKeyIsRefusedBeforeTheJobIsLookedUp() throws Exception {
        mvc.perform(patch("/api/jobs/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPLIED\"}"))
                .andExpect(status().isUnauthorized());

        verify(jobs, never()).findById(1L);
    }

    @Test
    void patchWithRightKeyReachesTheController() throws Exception {
        mvc.perform(patch("/api/jobs/1/status")
                        .header("X-Api-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPLIED\"}"))
                .andExpect(status().isNotFound());

        verify(jobs).findById(1L);
    }

    @Test
    void readingJobsNeedsNoKey() throws Exception {
        when(jobs.findByBucketInAndStatusInOrderByScoreDescFirstSeenAtDesc(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        mvc.perform(get("/api/jobs")).andExpect(status().isOk());
    }
}
