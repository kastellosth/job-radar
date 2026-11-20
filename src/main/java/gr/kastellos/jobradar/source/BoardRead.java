package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.domain.JobPosting;

import java.util.List;

/**
 * One read of one board: a tracked company's job list, or Remotive. Either the postings it listed, or why it failed.
 * The ingest keeps each board's health from these, so a board that fails is not mistaken for one with no jobs.
 *
 * @param board key of the board, e.g. "workable:learnworlds" or "remotive" (see TrackedCompany#board)
 * @param error null when the read worked
 */
public record BoardRead(String board, List<JobPosting> postings, String error) {

    public static BoardRead ok(String board, List<JobPosting> postings) {
        return new BoardRead(board, List.copyOf(postings), null);
    }

    public static BoardRead failed(String board, RuntimeException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return new BoardRead(board, List.of(), message);
    }

    public boolean failed() {
        return error != null;
    }
}
