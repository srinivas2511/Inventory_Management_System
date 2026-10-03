package com.springmfg.ims.crm.dto;

public record ConvertLeadResultDto(LeadDto lead, Long customerId, String customerCode, OpportunityDto opportunity) {}
