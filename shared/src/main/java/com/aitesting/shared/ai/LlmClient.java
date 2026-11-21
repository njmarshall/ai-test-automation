package com.aitesting.shared.ai;

/**
 * Seam between {@link TestGenerator} and the model provider.
 *
 * Production code uses {@link AnthropicLlmClient}. Unit tests pass a fake so
 * no real API is called.
 *
 * Implementations wrap I/O failures in {@link java.io.UncheckedIOException};
 * {@link TestGenerator} unwraps them back into {@link java.io.IOException}.
 */
public interface LlmClient {
    LlmResponse complete(LlmRequest request);
}
