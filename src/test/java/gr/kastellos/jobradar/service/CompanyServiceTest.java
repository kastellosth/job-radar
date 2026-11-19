package gr.kastellos.jobradar.service;

import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.Provider;
import gr.kastellos.jobradar.domain.TrackedCompanyRepository;
import gr.kastellos.jobradar.source.CompanyBoardSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CompanyServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

    private final TrackedCompanyRepository repo = mock(TrackedCompanyRepository.class);
    private final CompanyBoardSource workable = mock(CompanyBoardSource.class);
    private final CompanyService service;

    CompanyServiceTest() {
        when(workable.provider()).thenReturn(Provider.WORKABLE);
        when(repo.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        service = new CompanyService(repo, List.of(workable), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static JobPosting job(String company) {
        return new JobPosting("workable", "1", company, "Junior Java Developer", "Athens", false, "https://x", "", null);
    }

    @Test
    void addsACompanyAfterItsBoardConfirmsItAndTakesTheNameFromTheJobs() {
        when(workable.fetchCompany("learnworlds")).thenReturn(List.of(job("LearnWorlds"), job("LearnWorlds")));

        CompanyService.Added added = service.add("https://apply.workable.com/learnworlds/");

        assertThat(added.openJobs()).isEqualTo(2);
        assertThat(added.company().getName()).isEqualTo("LearnWorlds");
        assertThat(added.company().getProvider()).isEqualTo(Provider.WORKABLE);
        assertThat(added.company().getSlug()).isEqualTo("learnworlds");
        assertThat(added.company().getAddedAt()).isEqualTo(NOW);
    }

    @Test
    void aCompanyWithNoOpenJobsIsStillAddedUnderItsSlug() {
        when(workable.fetchCompany("quiet")).thenReturn(List.of());

        CompanyService.Added added = service.add("apply.workable.com/quiet");

        assertThat(added.openJobs()).isZero();
        assertThat(added.company().getName()).isEqualTo("quiet");
    }

    @Test
    void aCompanyTheBoardDoesNotKnowIsRejectedAndNotSaved() {
        when(workable.fetchCompany("nope")).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThatThrownBy(() -> service.add("https://apply.workable.com/nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workable has no company 'nope'");
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void anAlreadyTrackedCompanyIsRejectedBeforeCallingTheBoard() {
        when(repo.existsByProviderAndSlugIgnoreCase(Provider.WORKABLE, "learnworlds")).thenReturn(true);

        assertThatThrownBy(() -> service.add("https://apply.workable.com/LearnWorlds"))
                .isInstanceOf(CompanyService.AlreadyTrackedException.class);
        verify(workable, never()).fetchCompany(any());
    }

    @Test
    void twoRequestsAddingTheSameCompanyAtOnceGiveAConflictNotAServerError() {
        when(workable.fetchCompany("learnworlds")).thenReturn(List.of(job("LearnWorlds")));
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("tracked_company_provider_slug_uq"));

        assertThatThrownBy(() -> service.add("https://apply.workable.com/learnworlds"))
                .isInstanceOf(CompanyService.AlreadyTrackedException.class);
    }

    @Test
    void anUnsupportedLinkIsRejectedWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> service.add("https://www.linkedin.com/company/acme"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repo);
    }

    @Test
    void removingAnUnknownCompanyIsNotFound() {
        when(repo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.remove(99L)).isInstanceOf(java.util.NoSuchElementException.class);
        verify(repo, never()).deleteById(any());
    }
}
