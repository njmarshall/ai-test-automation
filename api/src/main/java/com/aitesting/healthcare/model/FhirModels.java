package com.aitesting.healthcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * FhirModels — FHIR R4 resource POJOs for Healthcare capstone.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * FHIR R4 STANDARD
 * ═══════════════════════════════════════════════════════════════════════
 *
 * HL7 FHIR (Fast Healthcare Interoperability Resources) R4 is the
 * dominant REST API standard for healthcare data exchange.
 * Used by Epic, Cerner, Kaiser, Anthem and all major EHR systems.
 *
 * Resources covered:
 *   Patient      → demographics, identifiers (MRN)
 *   Encounter    → visits, admissions, appointments
 *   Claim        → insurance billing submissions
 *   PriorAuth    → pre-approval requests (async pattern)
 *
 * HIPAA compliance:
 *   All test data uses synthetic values — never real PHI.
 *   IDs prefixed with "TEST-" to clearly identify test records.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * CODING SYSTEMS
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   ICD-10  → diagnosis codes    (e.g. J18.9 = Pneumonia)
 *   CPT     → procedure codes    (e.g. 99213 = Office visit)
 *   SNOMED  → clinical concepts
 *   LOINC   → lab observations
 *   NPI     → provider identifier
 */
public class FhirModels {

    // ── Patient ───────────────────────────────────────────────────────────────

    /**
     * FHIR R4 Patient resource.
     * Maps to: GET/POST /Patient
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Patient {
        private String       id;
        private String       resourceType;   // always "Patient"
        private List<Name>   name;
        private String       birthDate;      // YYYY-MM-DD
        private String       gender;         // male|female|other|unknown
        private List<Identifier> identifier; // MRN, SSN (masked)
        private List<Address>    address;
        private List<Telecom>    telecom;    // phone, email

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class Name {
            private String       use;       // official|nickname
            private String       family;    // last name
            private List<String> given;     // first, middle
        }

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class Identifier {
            private String system; // MRN system URL
            private String value;  // MRN value
        }

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class Address {
            private String city;
            private String state;
            private String postalCode;
            private String country;
        }

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class Telecom {
            private String system; // phone|email|fax
            private String value;
            private String use;    // home|work|mobile
        }
    }

    // ── Encounter ─────────────────────────────────────────────────────────────

    /**
     * FHIR R4 Encounter resource.
     * Maps to: GET/POST /Encounter
     * Represents a visit, admission, or appointment.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Encounter {
        private String       id;
        private String       resourceType;   // always "Encounter"
        private String       status;         // planned|in-progress|finished
        private EncounterClass classCode;    // AMB|IMP|EMER
        private Reference    subject;        // Patient reference
        private Period       period;         // start/end times
        private List<Diagnosis> diagnosis;
        private List<Participant> participant; // treating provider

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class EncounterClass {
            private String system;
            private String code;    // AMB=ambulatory, IMP=inpatient
            private String display;
        }

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class Diagnosis {
            private Reference condition;
            private Integer   rank;
        }

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class Participant {
            private Reference individual; // Practitioner reference
        }
    }

    // ── Claim ─────────────────────────────────────────────────────────────────

    /**
     * FHIR R4 Claim resource.
     * Maps to: POST /Claim
     * insurance billing submission.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Claim {
        private String       id;
        private String       resourceType;   // always "Claim"
        private String       status;         // active|cancelled|draft
        private CodeableConcept type;        // professional|institutional
        private String       use;            // claim|preauthorization
        private Reference    patient;
        private Reference    provider;       // NPI reference
        private Reference    insurer;
        private List<Diagnosis> diagnosis;
        private List<Item>   item;
        private Money        total;

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class Diagnosis {
            private Integer       sequence;
            private CodeableConcept diagnosisCodeableConcept; // ICD-10
        }

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class Item {
            private Integer       sequence;
            private CodeableConcept productOrService;  // CPT code
            private Money         unitPrice;
            private Double        quantity;
        }
    }

    // ── Prior Authorization ───────────────────────────────────────────────────

    /**
     * FHIR R4 Prior Authorization request.
     * Maps to: POST /Claim (use=preauthorization)
     * Async pattern — decision comes back via polling.
     *
     * Status flow:
     *   draft → active → approved | denied | pended
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriorAuth {
        private String       id;
        private String       resourceType;
        private String       status;    // draft|active|approved|denied|pended
        private String       use;       // always "preauthorization"
        private Reference    patient;
        private Reference    provider;
        private Reference    insurer;
        private List<Item>   item;      // requested procedures (CPT)
        private String       created;   // ISO-8601 timestamp
        private String       outcome;   // queued|complete|error|partial

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class Item {
            private Integer       sequence;
            private CodeableConcept productOrService; // CPT code
            private Integer       quantity;
        }
    }

    // ── Shared FHIR types ─────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Reference {
        private String reference; // e.g. "Patient/TEST-12345678"
        private String display;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CodeableConcept {
        private List<Coding> coding;
        private String       text;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Coding {
        private String system;  // code system URL
        private String code;    // the code value
        private String display; // human readable
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Period {
        private String start; // ISO-8601
        private String end;   // ISO-8601
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Money {
        private Double value;
        private String currency; // USD
    }
}
