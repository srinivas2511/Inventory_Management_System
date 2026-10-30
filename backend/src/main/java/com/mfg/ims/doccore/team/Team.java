package com.mfg.ims.doccore.team;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "teams", schema = "ims")
public class Team extends BaseEntity {

    @Column(name = "team_code", nullable = false, length = 20)
    private String teamCode;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_settings", columnDefinition = "jsonb")
    private String notificationSettings = "{}";

    @Column(name = "module_access", columnDefinition = "text[]")
    private String[] moduleAccess = new String[0];

    public Team() {}

    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String v) { this.teamCode = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public String getNotificationSettings() { return notificationSettings; }
    public void setNotificationSettings(String v) { this.notificationSettings = v; }
    public String[] getModuleAccess() { return moduleAccess; }
    public void setModuleAccess(String[] v) { this.moduleAccess = v; }
}
