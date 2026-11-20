package gr.kastellos.jobradar.web;

import gr.kastellos.jobradar.domain.BoardHealth;
import gr.kastellos.jobradar.domain.Provider;
import gr.kastellos.jobradar.domain.TrackedCompany;
import gr.kastellos.jobradar.service.CompanyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyController.class)
@TestPropertySource(properties = "radar.api-key=test-key")
class CompanyControllerTest {

    private static final String LINK = "{\"url\":\"https://apply.workable.com/learnworlds\"}";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private CompanyService companies;

    private static TrackedCompany learnWorlds() {
        TrackedCompany c = TrackedCompany.of(Provider.WORKABLE, "learnworlds", "LearnWorlds", Instant.EPOCH);
        ReflectionTestUtils.setField(c, "id", 7L);
        return c;
    }

    private static MockHttpServletRequestBuilder addWithKey(String body) {
        return post("/api/companies").header("X-Api-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void listingNeedsNoKey() throws Exception {
        BoardHealth health = BoardHealth.of("workable:learnworlds");
        health.read(12, Instant.parse("2026-11-18T06:00:00Z"));
        health.failed("503 Service Unavailable", Instant.parse("2026-11-18T16:00:00Z"));
        when(companies.list()).thenReturn(List.of(new CompanyService.Tracked(learnWorlds(), health)));

        mvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].provider").value("Workable"))
                .andExpect(jsonPath("$[0].name").value("LearnWorlds"))
                .andExpect(jsonPath("$[0].lastReadAt").value("2026-11-18T06:00:00Z"))
                .andExpect(jsonPath("$[0].jobs").value(12))
                .andExpect(jsonPath("$[0].failingSince").value("2026-11-18T16:00:00Z"))
                .andExpect(jsonPath("$[0].error").value("503 Service Unavailable"));
    }

    @Test
    void addingNeedsTheKey() throws Exception {
        mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON).content(LINK))
                .andExpect(status().isUnauthorized());

        verify(companies, never()).add(anyString());
    }

    @Test
    void addingReturns201WithTheCompanyAndItsOpenJobs() throws Exception {
        when(companies.add("https://apply.workable.com/learnworlds"))
                .thenReturn(new CompanyService.Added(learnWorlds(), 9));

        mvc.perform(addWithKey(LINK))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company.slug").value("learnworlds"))
                .andExpect(jsonPath("$.openJobs").value(9));
    }

    @Test
    void anEmptyLinkIs400() throws Exception {
        mvc.perform(addWithKey("{\"url\":\" \"}")).andExpect(status().isBadRequest());

        verify(companies, never()).add(anyString());
    }

    @Test
    void aLinkTheServiceRejectsIs400WithItsMessage() throws Exception {
        when(companies.add(anyString())).thenThrow(new IllegalArgumentException("Workable has no company 'nope'"));

        mvc.perform(addWithKey(LINK))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Workable has no company 'nope'"));
    }

    @Test
    void anAlreadyTrackedCompanyIs409() throws Exception {
        when(companies.add(anyString())).thenThrow(new CompanyService.AlreadyTrackedException("already tracked"));

        mvc.perform(addWithKey(LINK)).andExpect(status().isConflict());
    }

    @Test
    void aJobBoardThatDoesNotAnswerIs502() throws Exception {
        when(companies.add(anyString())).thenThrow(new ResourceAccessException("timeout"));

        mvc.perform(addWithKey(LINK))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("The job board did not answer, try again later"));
    }

    @Test
    void removingNeedsTheKeyAndAnswers204Or404() throws Exception {
        mvc.perform(delete("/api/companies/7")).andExpect(status().isUnauthorized());
        verify(companies, never()).remove(anyLong());

        mvc.perform(delete("/api/companies/7").header("X-Api-Key", "test-key")).andExpect(status().isNoContent());
        verify(companies).remove(7L);

        doThrow(new NoSuchElementException("company 8 not found")).when(companies).remove(8L);
        mvc.perform(delete("/api/companies/8").header("X-Api-Key", "test-key")).andExpect(status().isNotFound());
    }
}
