package com.springmfg.ims.crm.dto;

import java.util.List;

public record CrmCustomerDto(Long id, String customerCode, String name, String status, List<TagDto> tags) {}
