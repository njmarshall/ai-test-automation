package com.aitesting.api.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Pet — POJO matching the PetStore API /pet schema.
 *
 * Using Lombok @Data removes boilerplate getters/setters/toString.
 * Using @Builder enables readable object construction in tests:
 *
 *   Pet pet = Pet.builder()
 *       .id(123L)
 *       .name("Buddy")
 *       .status("available")
 *       .build();
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Pet {

    private Long   id;
    private String name;
    private String status;   // "available" | "pending" | "sold"

    private Category      category;
    private List<Tag>     tags;
    private List<String>  photoUrls;

    // ── Nested types ─────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Category {
        private Long   id;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tag {
        private Long   id;
        private String name;
    }
}
