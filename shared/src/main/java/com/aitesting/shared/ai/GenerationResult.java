package com.aitesting.shared.ai;

/**
 * How many generated code blocks {@link TestGenerator} wrote and how many it rejected.
 * A reply that is rejected as a whole (cut off at max_tokens, or with no code blocks)
 * counts as one rejection.
 *
 * @param accepted code blocks written to disk
 * @param rejected code blocks (or whole replies) skipped
 */
public record GenerationResult(int accepted, int rejected) {

    public static final GenerationResult NONE = new GenerationResult(0, 0);

    /** Sum of this result and another. */
    public GenerationResult plus(GenerationResult other) {
        return new GenerationResult(accepted + other.accepted, rejected + other.rejected);
    }
}
