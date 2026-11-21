package com.aitesting.shared.ai;

/**
 * A single-turn request to the model.
 *
 * @param system    optional system prompt; null or blank means none is sent
 * @param user      the user message
 * @param model     model identifier
 * @param maxTokens maximum tokens the model may generate
 */
public record LlmRequest(String system, String user, String model, int maxTokens) {
}
