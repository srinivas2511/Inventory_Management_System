package com.springmfg.ims.admin;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.common.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** User administration. DELETE means deactivate: users are never removed (ARCHITECTURE.md section 12.1). */
@RestController
@RequestMapping("/api/users")
@Validated
@Tag(name = "Users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserAdminService service;

    UserController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    @Operation(summary = "List users (search q, filter active and role, page, sort)")
    public PageResponse<UserDtos.UserSummary> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) String role,
            Pageable pageable) {
        return service.list(q, active, role, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public UserDtos.UserResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Create a user with a temporary password; they must change it at first sign-in")
    public ResponseEntity<UserDtos.UserResponse> create(@Valid @RequestBody UserDtos.CreateUserRequest request) {
        UserDtos.UserResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public UserDtos.UserResponse update(@PathVariable long id, @Valid @RequestBody UserDtos.UpdateUserRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Replace the user's roles; the last active Admin cannot lose the ADMIN role")
    public UserDtos.UserResponse assignRoles(@PathVariable long id, @Valid @RequestBody UserDtos.AssignRolesRequest request) {
        return service.assignRoles(id, request);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Set a temporary password, unlock the account and end all sessions")
    public ResponseEntity<Void> resetPassword(@PathVariable long id, @Valid @RequestBody UserDtos.ResetPasswordRequest request) {
        service.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public UserDtos.UserResponse activate(@PathVariable long id) {
        return service.activate(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    @Operation(summary = "Deactivate the user (never deletes); ends their sessions")
    public ResponseEntity<Void> deactivate(@PathVariable long id,
            @RequestParam(required = false) @Size(max = 500) String reason) {
        service.deactivate(id, reason);
        return ResponseEntity.noContent().build();
    }
}
