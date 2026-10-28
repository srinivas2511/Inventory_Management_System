package com.mfg.ims.doccore.team;

import java.util.List;

public record TeamDto(Long id, String teamCode, String code, String name, boolean active,
                      String teamType, String description, List<TeamMemberDto> members) {
    public static TeamDto from(Team t) {
        return new TeamDto(t.getId(), t.getTeamCode(), t.getTeamCode(), t.getName(), t.isActive(),
                null, null, null);
    }
}
