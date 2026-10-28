package com.mfg.ims.doccore.team;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;

@Service
public class TeamService {

    private final TeamRepository teamRepo;
    private final TeamMemberRepository memberRepo;

    public TeamService(TeamRepository teamRepo, TeamMemberRepository memberRepo) {
        this.teamRepo = teamRepo;
        this.memberRepo = memberRepo;
    }

    @Transactional(readOnly = true)
    public List<TeamDto> list() {
        return teamRepo.findByCompanyIdAndActiveTrueOrderByName(1L)
                .stream().map(TeamDto::from).toList();
    }

    @Transactional
    public TeamDto create(String teamCode, String name) {
        if (teamRepo.existsByCompanyIdAndTeamCode(1L, teamCode)) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY, "Team code already exists: " + teamCode);
        }
        Team t = new Team();
        t.setTeamCode(teamCode.toUpperCase().trim());
        t.setName(name.trim());
        return TeamDto.from(teamRepo.save(t));
    }

    @Transactional
    public TeamDto update(Long id, String name) {
        Team t = teamRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Team not found: " + id));
        t.setName(name.trim());
        return TeamDto.from(teamRepo.save(t));
    }

    @Transactional
    public void deactivate(Long id) {
        Team t = teamRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Team not found: " + id));
        t.setActive(false);
        teamRepo.save(t);
    }

    @Transactional(readOnly = true)
    public List<TeamMemberDto> listMembers(Long teamId) {
        teamRepo.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found: " + teamId));
        return memberRepo.findByTeamId(teamId).stream()
                .map(m -> new TeamMemberDto(m.getId().getUserId(), m.getJoinedAt()))
                .toList();
    }

    @Transactional
    public void addMember(Long teamId, Long userId) {
        teamRepo.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found: " + teamId));
        TeamMemberId mid = new TeamMemberId(teamId, userId);
        if (!memberRepo.existsById(mid)) {
            memberRepo.save(new TeamMember(teamId, userId));
        }
    }

    @Transactional
    public void removeMember(Long teamId, Long userId) {
        memberRepo.removeByTeamIdAndUserId(teamId, userId);
    }
}
