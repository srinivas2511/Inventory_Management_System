package com.mfg.ims.doccore.team;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "teams", schema = "ims")
public class Team extends BaseEntity {

    @Column(name = "team_code", nullable = false, length = 20)
    private String teamCode;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Team() {}

    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String v) { this.teamCode = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
