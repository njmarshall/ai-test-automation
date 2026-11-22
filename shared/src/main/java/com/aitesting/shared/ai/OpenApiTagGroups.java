package com.aitesting.shared.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Splits an OpenAPI / Swagger JSON spec into one smaller spec per tag, so
 * {@link TestGenerator} can ask for one test class per group.
 *
 * Each path goes to the group of its first tag: the first tag of the first
 * operation (get, post, ...) that has one. Paths with no tag go to {@code "default"}. Every group spec keeps all
 * other top-level keys (info, host, basePath, definitions, components, ...)
 * so models can still be resolved; only {@code paths} and {@code tags} are
 * narrowed to the group. Groups are returned in the order they first appear.
 */
final class OpenApiTagGroups {

    static final String UNTAGGED = "default";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    /** One tag's slice of the spec. */
    record Group(String tag, int pathCount, String specJson) {

        /** Test class name the model is asked for, e.g. "pet" becomes PetApiGeneratedTests. */
        String className() {
            StringBuilder sb = new StringBuilder();
            for (String word : tag.split("[^A-Za-z0-9]+")) {
                if (word.isEmpty()) continue;
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            if (sb.isEmpty() || !Character.isJavaIdentifierStart(sb.charAt(0))) sb.insert(0, "Tag");
            return sb + "ApiGeneratedTests";
        }

        /** Tag made safe for a file name. */
        String fileSafeTag() {
            return tag.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_");
        }
    }

    private OpenApiTagGroups() {}

    /**
     * @return one group per tag, or an empty list if the spec is not JSON or has no paths
     */
    static List<Group> byFirstTag(String specContent) {
        JsonNode root;
        try {
            root = MAPPER.readTree(specContent);
        } catch (JsonProcessingException e) {
            return List.of();
        }
        if (root == null || !root.isObject() || !root.path("paths").isObject() || root.path("paths").isEmpty()) {
            return List.of();
        }

        Map<String, ObjectNode> pathsByTag = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> paths = root.get("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> path = paths.next();
            pathsByTag.computeIfAbsent(firstTag(path.getValue()), t -> MAPPER.createObjectNode())
                    .set(path.getKey(), path.getValue());
        }

        List<Group> groups = new ArrayList<>();
        for (Map.Entry<String, ObjectNode> entry : pathsByTag.entrySet()) {
            ObjectNode spec = ((ObjectNode) root).deepCopy();
            spec.set("paths", entry.getValue());
            if (spec.path("tags").isArray()) {
                ArrayNode tags = MAPPER.createArrayNode();
                spec.get("tags").forEach(t -> {
                    if (entry.getKey().equals(t.path("name").asText())) tags.add(t);
                });
                spec.set("tags", tags);
            }
            try {
                groups.add(new Group(entry.getKey(), entry.getValue().size(), MAPPER.writeValueAsString(spec)));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Could not write group spec for tag " + entry.getKey(), e);
            }
        }
        return groups;
    }

    /** First tag of the path's first HTTP operation that has one, else {@link #UNTAGGED}. */
    private static String firstTag(JsonNode pathItem) {
        Iterator<Map.Entry<String, JsonNode>> ops = pathItem.fields();
        while (ops.hasNext()) {
            Map.Entry<String, JsonNode> op = ops.next();
            if (!HTTP_METHODS.contains(op.getKey())) continue;
            JsonNode tags = op.getValue().path("tags");
            if (tags.isArray() && !tags.isEmpty() && !tags.get(0).asText().isBlank()) {
                return tags.get(0).asText();
            }
        }
        return UNTAGGED;
    }
}
