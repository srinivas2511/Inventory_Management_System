package com.springmfg.ims.production.dto;

import jakarta.validation.constraints.Size;

/** Assigns an operator to an operation (or to every open operation of an order); a null operator unassigns. */
public record AssignOperatorRequest(Long operatorId, @Size(max = 300) String reason) {
}
