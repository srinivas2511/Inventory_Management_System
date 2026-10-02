package com.springmfg.ims.iam.web;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

import com.springmfg.ims.iam.dto.AssignRolesRequest;
import com.springmfg.ims.iam.dto.CreateUserRequest;
import com.springmfg.ims.iam.dto.UpdateUserRequest;
import com.springmfg.ims.iam.dto.UserDto;
import com.springmfg.ims.iam.service.UserAdminService;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IAM_USER_MANAGE')")
    public Page<UserDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(q, active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IAM_USER_MANAGE')")
    public UserDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('IAM_USER_MANAGE')")
    public UserDto create(@Valid @RequestBody CreateUserRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('IAM_USER_MANAGE')")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
        return service.update(id, req);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('IAM_USER_MANAGE')")
    public UserDto assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest req) {
        return service.assignRoles(id, req.roleCodes());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('IAM_USER_MANAGE')")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('IAM_USER_MANAGE')")
    public UserDto reactivate(@PathVariable Long id) {
        service.reactivate(id);
        return service.findById(id);
    }
}
