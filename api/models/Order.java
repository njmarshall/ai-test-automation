package com.aitesting.api.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Order — POJO matching the PetStore API /store/order schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Order {

    private Long    id;
    private Long    petId;
    private Integer quantity;
    private String  shipDate;
    private String  status;    // "placed" | "approved" | "delivered"
    private Boolean complete;
}
