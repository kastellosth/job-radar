package gr.kastellos.jobradar.support;

import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.source.BoardRead;
import gr.kastellos.jobradar.source.JobSource;

import java.util.List;

/** A JobSource with one board, named like the source, that returns what the test tells it to or fails. */
public class StubSource implements JobSource {

    private final String name;
    private volatile List<JobPosting> postings = List.of();
    private volatile RuntimeException failure;

    public StubSource(String name) {
        this.name = name;
    }

    public void willReturn(JobPosting... postings) {
        this.postings = List.of(postings);
        this.failure = null;
    }

    public void willFail(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public String name() {
        return name;
    }

    /** Like the real sources: a failure comes back as a failed read of the board, not as an exception. */
    @Override
    public List<BoardRead> fetch() {
        return List.of(failure != null ? BoardRead.failed(name, failure) : BoardRead.ok(name, postings));
    }
}
