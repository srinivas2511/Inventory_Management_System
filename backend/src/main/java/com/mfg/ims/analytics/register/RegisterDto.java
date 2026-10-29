package com.mfg.ims.analytics.register;

import java.util.List;
import java.util.Map;

public record RegisterDto(
        String code,
        String name,
        List<Map<String, Object>> rows
) {}
