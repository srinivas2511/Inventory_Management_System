package com.springmfg.ims.iam.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.iam.dto.PermissionDto;
import com.springmfg.ims.iam.dto.RoleDto;
import com.springmfg.ims.iam.repository.PermissionRepository;
import com.springmfg.ims.iam.repository.RoleRepository;

@Service
public class RoleAdminService {

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;

    public RoleAdminService(RoleRepository roleRepo, PermissionRepository permissionRepo) {
        this.roleRepo = roleRepo;
        this.permissionRepo = permissionRepo;
    }

    @Transactional(readOnly = true)
    public List<RoleDto> listRoles() {
        return roleRepo.findAll().stream().map(RoleDto::from).toList();
    }

    @Transactional(readOnly = true)
    public RoleDto findById(Long id) {
        return RoleDto.from(roleRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Role not found: " + id)));
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> listAllPermissions() {
        return permissionRepo.findAllByOrderByModuleAscCodeAsc().stream()
            .map(PermissionDto::from).toList();
    }
}
