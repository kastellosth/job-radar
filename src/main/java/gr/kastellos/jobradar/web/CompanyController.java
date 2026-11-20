package gr.kastellos.jobradar.web;

import gr.kastellos.jobradar.domain.TrackedCompany;
import gr.kastellos.jobradar.service.CompanyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companies;

    public CompanyController(CompanyService companies) {
        this.companies = companies;
    }

    public record CompanyView(long id, String provider, String slug, String name) {
        static CompanyView of(TrackedCompany c) {
            return new CompanyView(c.getId(), c.getProvider().displayName(), c.getSlug(), c.getName());
        }
    }

    public record AddCompany(@NotBlank String url) {
    }

    public record AddedCompany(CompanyView company, int openJobs) {
    }

    @GetMapping
    public List<CompanyView> list() {
        return companies.list().stream().map(CompanyView::of).toList();
    }

    /** Example body: {"url": "https://apply.workable.com/learnworlds"} */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddedCompany add(@RequestBody @Valid AddCompany request) {
        CompanyService.Added added = companies.add(request.url());
        return new AddedCompany(CompanyView.of(added.company()), added.openJobs());
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
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The job board did not answer, try again later");
    }
}
