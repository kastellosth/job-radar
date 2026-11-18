package gr.kastellos.jobradar.domain;

import java.util.List;

public record MatchResult(int score, Bucket bucket, List<String> reasons) {
}
