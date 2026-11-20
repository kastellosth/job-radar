package gr.kastellos.jobradar.web;

import gr.kastellos.jobradar.domain.BoardHealth;
import gr.kastellos.jobradar.domain.TrackedCompany;
import gr.kastellos.jobradar.service.CompanyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private static final Logger log = LoggerFactory.getLogger(CompanyController.class);

    private final CompanyService companies;

    public CompanyController(CompanyService companies) {
        this.companies = companies;
    }

    /**
     * @param lastReadAt   the last read of its board that worked; null before the first fetch
     * @param jobs         jobs that read listed
     * @param failingSince the first failed read since then; null while it works
     * @param error        why the last read failed
     */
    public record CompanyView(long id, String provider, String slug, String name,
                              Instant lastReadAt, Integer jobs, Instant failingSince, String error) {
        static CompanyView of(TrackedCompany c, BoardHealth h) {
            return new CompanyView(c.getId(), c.getProvider().displayName(), c.getSlug(), c.getName(),
                    h == null ? null : h.getLastOkAt(),
                    h == null ? null : h.getJobCount(),
                    h == null ? null : h.getFailingSince(),
                    h == null ? null : h.getLastError());
        }
    }

    public record AddCompany(@NotBlank String url) {
    }

    public record AddedCompany(CompanyView company, int openJobs) {
    }

    @GetMapping
    public List<CompanyView> list() {
        return companies.list().stream().map(t -> CompanyView.of(t.company(), t.health())).toList();
    }

    /** Example body: {"url": "https://apply.workable.com/learnworlds"} */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddedCompany add(@RequestBody @Valid AddCompany request) {
        CompanyService.Added added = companies.add(request.url());
        return new AddedCompany(CompanyView.of(added.company(), null), added.openJobs());
    }

    /**
     * Stops reading the company's board. Its jobs stay in the database with their statuses, but leave the
     * dashboard once they have not been listed for radar.hide-unlisted-after (jobs he applied to stay).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id) {
        companies.remove(id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badLink(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(CompanyService.AlreadyTrackedException.class)
    ProblemDetail alreadyTracked(CompanyService.AlreadyTrackedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** The job board itself failed (down, 5xx, timeout): not the user's fault, so not a 400. */
    @ExceptionHandler(RestClientException.class)
    ProblemDetail boardUnavailable(RestClientException e) {
        log.warn("Job board did not answer while adding a company: {}", e.toString());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The job board did not answer, try again later");
    }
}
