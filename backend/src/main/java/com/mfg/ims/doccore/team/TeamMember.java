package com.mfg.ims.doccore.team;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "team_members", schema = "ims")
public class TeamMember {

    @EmbeddedId
    private TeamMemberId id;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    public TeamMember() {}
    public TeamMember(Long teamId, Long userId) {
        this.id = new TeamMemberId(teamId, userId);
    }

    public TeamMemberId getId() { return id; }
    public void setId(TeamMemberId v) { this.id = v; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant v) { this.joinedAt = v; }
}
