package com.mfg.ims.doccore.team;

import java.time.Instant;

public record TeamMemberDto(Long userId, Instant joinedAt) {}
