package com.mfg.ims.mrp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DiscardProposalRequest(@NotBlank @Size(max = 300) String reason) {}
