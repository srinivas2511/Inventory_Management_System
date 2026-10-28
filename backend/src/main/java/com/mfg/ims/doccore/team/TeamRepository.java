package com.mfg.ims.doccore.team;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByCompanyIdAndActiveTrueOrderByName(Long companyId);
    boolean existsByCompanyIdAndTeamCode(Long companyId, String teamCode);
    Optional<Team> findByCompanyIdAndTeamCode(Long companyId, String teamCode);
}
