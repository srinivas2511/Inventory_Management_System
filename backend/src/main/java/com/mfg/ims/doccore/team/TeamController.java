package com.mfg.ims.doccore.team;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/teams")
public class TeamController {

    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TEAM_MANAGE','SETTINGS_MANAGE')")
    public List<TeamDto> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public TeamDto create(@RequestBody Map<String, String> body) {
        return service.create(body.get("teamCode"), body.get("name"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public TeamDto update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.update(id, body.get("name"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAnyAuthority('TEAM_MANAGE','SETTINGS_MANAGE')")
    public List<TeamMemberDto> listMembers(@PathVariable Long id) {
        return service.listMembers(id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public void addMember(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        service.addMember(id, body.get("userId"));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public void removeMember(@PathVariable Long id, @PathVariable Long userId) {
        service.removeMember(id, userId);
    }
}
