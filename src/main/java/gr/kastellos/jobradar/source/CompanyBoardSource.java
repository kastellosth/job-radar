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

    @Override
    public String name() {
        return provider().sourceName();
    }

    @Override
    public List<JobPosting> fetch() {
        List<JobPosting> all = new ArrayList<>();
        for (TrackedCompany company : companies.findByProvider(provider())) {
            try {
                all.addAll(fetchCompany(company.getSlug()));
            } catch (RuntimeException e) {
                // One bad slug must not hide the other companies' jobs.
                log.warn("{} company '{}' failed: {}", provider().displayName(), company.getSlug(), e.getMessage());
            }
        }
        return all;
    }
}
