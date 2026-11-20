package gr.kastellos.jobradar.service;

import gr.kastellos.jobradar.domain.BoardHealth;
import gr.kastellos.jobradar.domain.BoardHealthRepository;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.Provider;
import gr.kastellos.jobradar.domain.TrackedCompany;
import gr.kastellos.jobradar.domain.TrackedCompanyRepository;
import gr.kastellos.jobradar.source.CareersLink;
import gr.kastellos.jobradar.source.CompanyBoardSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Which companies are tracked. A company is only added after its job board confirms it exists. */
@Service
public class CompanyService {

    private final TrackedCompanyRepository companies;
    private final BoardHealthRepository health;
    private final Map<Provider, CompanyBoardSource> boards;
    private final Clock clock;

    public CompanyService(TrackedCompanyRepository companies, BoardHealthRepository health,
                          List<CompanyBoardSource> boards, Clock clock) {
        this.companies = companies;
        this.health = health;
        this.boards = boards.stream().collect(Collectors.toMap(CompanyBoardSource::provider, Function.identity()));
        this.clock = clock;
    }

    /** A company was added; openJobs is how many jobs its board lists right now. */
    public record Added(TrackedCompany company, int openJobs) {
    }

    /** Thrown when the company is already tracked. */
    public static class AlreadyTrackedException extends RuntimeException {
        public AlreadyTrackedException(String message) {
            super(message);
        }
    }

    /** A tracked company and how the reads of its board went; health is null before its first fetch. */
    public record Tracked(TrackedCompany company, BoardHealth health) {
    }

    @Transactional(readOnly = true)
    public List<Tracked> list() {
        Map<String, BoardHealth> byBoard = health.findAll().stream()
                .collect(Collectors.toMap(BoardHealth::getBoard, Function.identity()));
        return companies.findAllByOrderByNameAsc().stream()
                .map(c -> new Tracked(c, byBoard.get(c.board())))
                .toList();
    }

    /**
     * Not one transaction around the whole method on purpose: the job board is called over HTTP first,
     * and a slow board must not hold a database connection.
     *
     * @throws IllegalArgumentException the link is not a supported careers page, or the board does not know the company
     * @throws AlreadyTrackedException  the company is already tracked
     */
    public Added add(String url) {
        CareersLink link = CareersLink.parse(url);
        String label = link.provider().displayName() + " company '" + link.slug() + "'";
        if (companies.existsByProviderAndSlugIgnoreCase(link.provider(), link.slug())) {
            throw new AlreadyTrackedException(label + " is already tracked");
        }

        List<JobPosting> jobs;
        try {
            jobs = boards.get(link.provider()).fetchCompany(link.slug());
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException(link.provider().displayName() + " has no company '" + link.slug() + "'");
        }
        String name = jobs.stream()
                .map(JobPosting::company)
                .filter(n -> n != null && !n.equals(link.slug()))
                .findFirst()
                .orElse(link.defaultName());

        try {
            TrackedCompany saved = companies.saveAndFlush(
                    TrackedCompany.of(link.provider(), link.slug(), name, clock.instant()));
            return new Added(saved, jobs.size());
        } catch (DataIntegrityViolationException e) {
            // Added by another request between the check and the insert; the unique index caught it.
            throw new AlreadyTrackedException(label + " is already tracked");
        }
    }

    /** Its jobs stay; with its health gone they leave the dashboard once unlisted for radar.hide-unlisted-after. */
    @Transactional
    public void remove(long id) {
        TrackedCompany company = companies.findById(id)
                .orElseThrow(() -> new NoSuchElementException("company " + id + " not found"));
        companies.delete(company);
        health.deleteById(company.board());
    }
}
