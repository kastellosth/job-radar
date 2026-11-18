package gr.kastellos.jobradar.support;

import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.source.JobSource;

import java.util.List;

/** A JobSource that returns what the test tells it to, or fails like a provider that is down. */
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

    @Override
    public List<JobPosting> fetch() {
        if (failure != null) {
            throw failure;
        }
        return postings;
    }
}
