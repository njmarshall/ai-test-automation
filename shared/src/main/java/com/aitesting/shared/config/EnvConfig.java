package com.aitesting.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * EnvConfig loads configuration from:
 *   1. System environment variables (highest priority — CI/CD)
 *   2. env-specific .properties file (e.g. config/staging.properties)
 *   3. Default config/default.properties (fallback)
 *
 * Usage:
 *   String baseUrl = EnvConfig.get("BASE_URL");
 *   int timeout    = EnvConfig.getInt("REQUEST_TIMEOUT_MS", 5000);
 */
public class EnvConfig {

    private static final Logger log = LoggerFactory.getLogger(EnvConfig.class);
    private static final Properties props = new Properties();

    static {
        // Determine active environment: ENV_NAME env var or "default"
        String env = System.getenv().getOrDefault("ENV_NAME", "default");
        loadProperties("config/default.properties");
        if (!"default".equals(env)) {
            loadProperties("config/" + env + ".properties");
        }
        log.info("EnvConfig initialised for environment: {}", env);
    }

    private static void loadProperties(String resourcePath) {
        try (InputStream is = EnvConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                props.load(is);
                log.debug("Loaded properties from: {}", resourcePath);
            } else {
                log.debug("Properties file not found (skipping): {}", resourcePath);
            }
        } catch (IOException e) {
            log.warn("Could not load properties file: {} — {}", resourcePath, e.getMessage());
        }
    }

    /**
     * Returns value: env var > properties file > null.
     */
    public static String get(String key) {
        String envVal = System.getenv(key);
        if (envVal != null && !envVal.isBlank()) return envVal;
        return props.getProperty(key);
    }

    /**
     * Returns value with a fallback default.
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return (value != null) ? value : defaultValue;
    }

    /**
     * Returns integer value with a fallback default.
     */
    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Could not parse '{}' as integer for key '{}', using default {}", value, key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Returns boolean value with a fallback default.
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value.trim());
    }

    /** Prevent instantiation — this is a static utility class. */
    private EnvConfig() {}
}
