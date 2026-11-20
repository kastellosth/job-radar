package gr.kastellos.jobradar.source;

import java.util.List;

/**
 * One job-board provider. Implementations only fetch and map; they never score or store.
 * Each board it reads (one per tracked company, or one for the whole source) comes back as its own BoardRead, a
 * failed one included, so the ingest can tell a board that is down from one without jobs.
 */
public interface JobSource {

    String name();

    List<BoardRead> fetch();
}
