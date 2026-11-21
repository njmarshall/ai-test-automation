package com.aitesting.shared.ai;

/**
 * The model's reply.
 *
 * @param text         text of the first content block
 * @param stopReason   why generation stopped (e.g. "end_turn", "max_tokens"); may be null
 * @param inputTokens  prompt tokens billed
 * @param outputTokens generated tokens billed
 */
public record LlmResponse(String text, String stopReason, int inputTokens, int outputTokens) {
}
