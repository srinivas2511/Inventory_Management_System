package com.mfg.ims.doccore.tag;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService service;

    public TagController(TagService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<TagDto> list(@RequestParam(required = false) String entityType,
                             @RequestParam(required = false) Long entityId) {
        if (entityType != null && entityId != null) return service.getForEntity(entityType, entityId);
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    public TagDto create(@RequestBody Map<String, String> body) {
        return service.create(body.get("name"), body.get("color"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    public TagDto update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.update(id, body.get("name"), body.get("color"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/entities/{entityType}/{entityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COMMENT_ADD')")
    public void addToEntity(@PathVariable Long id,
                            @PathVariable String entityType,
                            @PathVariable Long entityId) {
        service.addToEntity(id, entityType, entityId, currentUserId());
    }

    @DeleteMapping("/{id}/entities/{entityType}/{entityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COMMENT_ADD')")
    public void removeFromEntity(@PathVariable Long id,
                                 @PathVariable String entityType,
                                 @PathVariable Long entityId) {
        service.removeFromEntity(id, entityType, entityId);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return null;
    }
}
