package com.mfg.ims.mrp.dto;

import java.util.List;

public record ConvertAllResultDto(int converted, List<Long> proposalIds) {}
