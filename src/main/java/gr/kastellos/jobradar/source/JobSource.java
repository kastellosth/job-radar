package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.domain.JobPosting;

import java.util.List;

/**
 * One job-board provider. Implementations only fetch and map; they never score or store.
 * A failing source must throw, so the ingest service can log it and carry on with the others.
 */
public interface JobSource {

    String name();

    List<JobPosting> fetch();
}
