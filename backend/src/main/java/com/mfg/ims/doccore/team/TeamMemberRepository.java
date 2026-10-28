package com.mfg.ims.doccore.team;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {

    @Query("SELECT m FROM TeamMember m WHERE m.id.teamId = :teamId")
    List<TeamMember> findByTeamId(Long teamId);

    @Modifying
    @Query("DELETE FROM TeamMember m WHERE m.id.teamId = :teamId AND m.id.userId = :userId")
    void removeByTeamIdAndUserId(Long teamId, Long userId);

    boolean existsById(TeamMemberId id);
}
