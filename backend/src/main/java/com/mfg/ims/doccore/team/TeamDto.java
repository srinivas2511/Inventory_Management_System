package com.mfg.ims.doccore.team;

import java.util.Arrays;
import java.util.List;

public record TeamDto(Long id, String teamCode, String code, String name, boolean active,
                      String teamType, String description, List<TeamMemberDto> members,
                      String notificationSettings, List<String> moduleAccess) {
    public static TeamDto from(Team t) {
        return new TeamDto(t.getId(), t.getTeamCode(), t.getTeamCode(), t.getName(), t.isActive(),
                null, null, null,
                t.getNotificationSettings() != null ? t.getNotificationSettings() : "{}",
                t.getModuleAccess() != null ? Arrays.asList(t.getModuleAccess()) : List.of());
    }
}
