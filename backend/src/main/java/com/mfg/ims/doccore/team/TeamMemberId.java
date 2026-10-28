package com.mfg.ims.doccore.team;

import java.io.Serializable;
import java.util.Objects;

public class TeamMemberId implements Serializable {
    private Long teamId;
    private Long userId;

    public TeamMemberId() {}
    public TeamMemberId(Long teamId, Long userId) { this.teamId = teamId; this.userId = userId; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long v) { this.teamId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamMemberId x)) return false;
        return Objects.equals(teamId, x.teamId) && Objects.equals(userId, x.userId);
    }
    @Override public int hashCode() { return Objects.hash(teamId, userId); }
}
