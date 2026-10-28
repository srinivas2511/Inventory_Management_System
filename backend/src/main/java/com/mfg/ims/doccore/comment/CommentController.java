package com.mfg.ims.doccore.comment;

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
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('COMMENT_ADD')")
    public List<CommentDto> list(@RequestParam String entityType, @RequestParam Long entityId) {
        return service.list(entityType, entityId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMMENT_ADD')")
    public CommentDto add(@RequestBody Map<String, Object> body) {
        return service.add(
                (String) body.get("entityType"),
                Long.valueOf(body.get("entityId").toString()),
                body.get("parentId") != null ? Long.valueOf(body.get("parentId").toString()) : null,
                (String) body.get("body"),
                currentUserId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COMMENT_ADD')")
    public CommentDto edit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.edit(id, body.get("body"), currentUserId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COMMENT_ADD')")
    public void delete(@PathVariable Long id) {
        service.softDelete(id, currentUserId());
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return null;
    }
}
