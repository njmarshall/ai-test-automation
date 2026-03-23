package com.aitesting.api.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * InsuranceModels groups all POJOs for the Insurance Quote capstone.
 *
 * Mirrors a realistic insurance REST API:
 *   POST /quotes          → QuoteRequest  → QuoteResponse
 *   POST /policies        → PolicyRequest → PolicyResponse
 *   GET  /quotes/{id}
 *   GET  /policies/{id}
 */
public class InsuranceModels {

    // ── Quote ─────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteRequest {
        private Applicant      applicant;
        private Vehicle        vehicle;
        private String         coverageType;   // "liability" | "comprehensive" | "collision"
        private Integer        deductible;     // 250 | 500 | 1000 | 2000
        private List<String>   addOns;         // "roadside", "rental", "gap"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteResponse {
        private String  quoteId;
        private Double  monthlyPremium;
        private Double  annualPremium;
        private String  status;         // "approved" | "referred" | "declined"
        private String  expiresAt;      // ISO-8601
        private String  coverageType;
        private Integer deductible;
    }

    // ── Policy ────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PolicyRequest {
        private String quoteId;         // bind from an existing approved quote
        private String paymentMethod;   // "annual" | "monthly"
        private String effectiveDate;   // ISO-8601 date
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PolicyResponse {
        private String  policyId;
        private String  status;         // "active" | "pending" | "cancelled"
        private String  effectiveDate;
        private String  expirationDate;
        private Double  premium;
    }

    // ── Applicant ─────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Applicant {
        private String  firstName;
        private String  lastName;
        private String  dateOfBirth;    // "YYYY-MM-DD"
        private String  licenseNumber;
        private Integer yearsLicensed;
        private Integer accidentsLast3Years;
        private String  zipCode;
    }

    // ── Vehicle ───────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Vehicle {
        private Integer year;
        private String  make;
        private String  model;
        private String  vin;
        private Integer annualMileage;
        private String  primaryUse;     // "commute" | "pleasure" | "business"
        private Boolean antiTheft;
        private Boolean safetyFeatures;
    }
}
