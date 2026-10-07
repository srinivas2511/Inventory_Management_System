package com.springmfg.ims.production.dto;

import jakarta.validation.constraints.Size;

/** Closing with issued material still unreconciled needs a reason; the remainder is then written off as scrap. */
public record CloseOrderRequest(@Size(max = 500) String writeOffReason) {
}
