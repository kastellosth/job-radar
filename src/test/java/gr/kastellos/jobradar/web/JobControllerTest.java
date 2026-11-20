package gr.kastellos.jobradar.web;

import gr.kastellos.jobradar.config.AppConfig;
import gr.kastellos.jobradar.domain.Bucket;
import gr.kastellos.jobradar.domain.Job;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.JobRepository;
import gr.kastellos.jobradar.domain.MatchResult;
import gr.kastellos.jobradar.domain.Pay;
import gr.kastellos.jobradar.service.IngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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

/** JobController behind the API key filter, with the service and repository mocked. */
@WebMvcTest(JobController.class)
@Import(AppConfig.class)
@TestPropertySource(properties = "radar.api-key=test-key")
class JobControllerTest {

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
    void secondFetchWhileOneIsRunningIsAConflict() throws Exception {
        when(ingest.run()).thenThrow(new IngestService.AlreadyRunningException());

        mvc.perform(post("/api/ingest").header("X-Api-Key", "test-key"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "A fetch is already running. Its new jobs will show up when it finishes."));
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
        when(jobs.dashboard(anyCollection(), anyCollection(), any())).thenReturn(List.of());

        mvc.perform(get("/api/jobs")).andExpect(status().isOk());
    }

    @Test
    void answersWithWhatTheDashboardShowsAndNothingInternal() throws Exception {
        Job job = Job.from(new JobPosting("workable", "ABC123", "Acme", "Junior Java Developer", "Athens", false,
                "https://acme.example/1", "Java", Instant.parse("2026-11-16T09:00:00Z")),
                new MatchResult(10, Bucket.STRONG, List.of("+3 java")), Instant.parse("2026-11-18T08:00:00Z"));
        job.paid(new Pay(1200, 1500, "EUR", Pay.Period.MONTH, "Μικτές αποδοχές 1.200 - 1.500 €"));
        job.listedOn("workable:acme");
        ReflectionTestUtils.setField(job, "id", 7L);
        when(jobs.dashboard(anyCollection(), anyCollection(), any())).thenReturn(List.of(job));

        mvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.STRONG[0].id").value(7))
                .andExpect(jsonPath("$.STRONG[0].title").value("Junior Java Developer"))
                .andExpect(jsonPath("$.STRONG[0].payMin").value(1200))
                .andExpect(jsonPath("$.STRONG[0].payPeriod").value("MONTH"))
                .andExpect(jsonPath("$.STRONG[0].fingerprint").doesNotExist())
                .andExpect(jsonPath("$.STRONG[0].externalId").doesNotExist())
                .andExpect(jsonPath("$.STRONG[0].board").doesNotExist())
                .andExpect(jsonPath("$.GOOD").isEmpty());
    }
}
