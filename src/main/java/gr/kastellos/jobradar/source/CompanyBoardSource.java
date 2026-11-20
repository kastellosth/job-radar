package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.Provider;
import gr.kastellos.jobradar.domain.TrackedCompany;
import gr.kastellos.jobradar.domain.TrackedCompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    public List<JobPosting> fetch() {
        List<JobPosting> all = new ArrayList<>();
        for (TrackedCompany company : companies.findByProvider(provider())) {
            try {
                for (JobPosting posting : complete(company.getSlug(), fetchCompany(company.getSlug()))) {
                    all.add(named(posting, company));
                }
            } catch (RuntimeException e) {
                // One bad slug must not hide the other companies' jobs.
                log.warn("{} company '{}' failed: {}", provider().displayName(), company.getSlug(), e.getMessage());
            }
        }
        return all;
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
