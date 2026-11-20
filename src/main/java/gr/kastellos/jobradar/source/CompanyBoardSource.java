package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.Provider;
import gr.kastellos.jobradar.domain.TrackedCompany;
import gr.kastellos.jobradar.domain.TrackedCompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * A job board with one list per company (Workable, Greenhouse...). Which companies to read comes from the
 * database on every run, so a company added in the dashboard is fetched next time without a restart.
 */
public abstract class CompanyBoardSource implements JobSource {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final TrackedCompanyRepository companies;

    protected CompanyBoardSource(TrackedCompanyRepository companies) {
        this.companies = companies;
    }

    public abstract Provider provider();

    /** One company's jobs. Throws when the board does not know the company (e.g. HTTP 404). */
    public abstract List<JobPosting> fetchCompany(String slug);

    /**
     * Extra work for a fetch run only, not when a company is being added: e.g. reading the full ads of a board
     * whose list leaves them out. Must not throw for one bad posting.
     */
    protected List<JobPosting> complete(String slug, List<JobPosting> postings) {
        return postings;
    }

    @Override
    public String name() {
        return provider().sourceName();
    }

    /** One read per tracked company: one bad slug must not hide the other companies' jobs. */
    @Override
    public List<BoardRead> fetch() {
        List<BoardRead> reads = new ArrayList<>();
        for (TrackedCompany company : companies.findByProvider(provider())) {
            try {
                List<JobPosting> postings = new ArrayList<>();
                for (JobPosting posting : complete(company.getSlug(), fetchCompany(company.getSlug()))) {
                    postings.add(named(posting, company));
                }
                reads.add(BoardRead.ok(company.board(), postings));
            } catch (RuntimeException e) {
                log.warn("{} company '{}' failed: {}", provider().displayName(), company.getSlug(), e.getMessage());
                reads.add(BoardRead.failed(company.board(), e));
            }
        }
        return reads;
    }

    /**
     * For boards whose list leaves the ad out: {@code read} the full ad of each posting {@code worth} it, at most
     * {@code max} per company. A posting whose ad cannot be read stays as the list gave it.
     */
    protected List<JobPosting> readAds(String slug, List<JobPosting> postings, Predicate<JobPosting> worth,
                                       UnaryOperator<JobPosting> read, int max) {
        List<JobPosting> out = new ArrayList<>(postings.size());
        int tried = 0;
        int failed = 0;
        for (JobPosting p : postings) {
            if (tried >= max || !worth.test(p)) {
                out.add(p);
                continue;
            }
            tried++;
            try {
                out.add(read.apply(p));
            } catch (RuntimeException e) {
                failed++;
                out.add(p);
            }
        }
        if (failed > 0) {
            log.warn("{} company '{}': {} of {} full ads could not be read; scored on title only",
                    provider().displayName(), slug, failed, tried);
        }
        return out;
    }

    /**
     * Lever's API has no company name (and Greenhouse sometimes leaves it out), so the posting carries the slug,
     * e.g. "kaizen-gaming". The name saved with the tracked company reads better.
     */
    private static JobPosting named(JobPosting p, TrackedCompany company) {
        if (p.company() != null && !p.company().equals(company.getSlug())) {
            return p;
        }
        return new JobPosting(p.source(), p.externalId(), company.getName(), p.title(), p.location(), p.remote(),
                p.url(), p.description(), p.postedAt());
    }
}
