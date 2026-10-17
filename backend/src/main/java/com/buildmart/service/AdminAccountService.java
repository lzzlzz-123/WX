package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.buildmart.common.AuthContext;
import com.buildmart.entity.SysAdmin;
import com.buildmart.entity.SysRolePermission;
import com.buildmart.mapper.SysAdminMapper;
import com.buildmart.mapper.SysRolePermissionMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端账号与角色权限管理。仅 SUPER_ADMIN 可操作；
 * 不允许停用自己，不允许停用/降权最后一个 SUPER_ADMIN。
 */
@Service
public class AdminAccountService {
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_OPERATOR = "OPERATOR";
    public static final String ROLE_FINANCE = "FINANCE";

    private final SysAdminMapper adminMapper;
    private final SysRolePermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService auditService;

    public AdminAccountService(SysAdminMapper adminMapper, SysRolePermissionMapper permissionMapper,
                               PasswordEncoder passwordEncoder, AdminAuditService auditService) {
        this.adminMapper = adminMapper;
        this.permissionMapper = permissionMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public List<SysAdmin> list() {
        requireSuperAdmin();
        List<SysAdmin> admins = adminMapper.selectList(new LambdaQueryWrapper<SysAdmin>().orderByAsc(SysAdmin::getId));
        admins.forEach(a -> a.setPasswordHash(null));
        return admins;
    }

    @Transactional
    public SysAdmin create(String username, String password, String realName, String roleCode) {
        requireSuperAdmin();
        if (username == null || username.isBlank()) throw new IllegalArgumentException("用户名不能为空");
        if (password == null || password.length() < 8) throw new IllegalArgumentException("密码长度至少 8 位");
        Long exists = adminMapper.selectCount(new LambdaQueryWrapper<SysAdmin>().eq(SysAdmin::getUsername, username.trim()));
        if (exists != null && exists > 0) throw new IllegalArgumentException("用户名已存在");
        SysAdmin admin = new SysAdmin();
        admin.setUsername(username.trim());
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRealName(realName);
        admin.setRoleCode(normalizeRole(roleCode));
        admin.setStatus(1);
        adminMapper.insert(admin);
        auditService.record(AuthContext.requireAdminId(), null, "ACCOUNT_CREATE", username, "创建管理员，角色 " + admin.getRoleCode());
        admin.setPasswordHash(null);
        return admin;
    }

    @Transactional
    public SysAdmin update(Long id, String realName, String roleCode) {
        requireSuperAdmin();
        SysAdmin admin = requireAdmin(id);
        String newRole = normalizeRole(roleCode);
        if (ROLE_SUPER_ADMIN.equals(admin.getRoleCode()) && !ROLE_SUPER_ADMIN.equals(newRole)) {
            ensureNotLastSuperAdmin(id, "不允许降权最后一个超级管理员");
        }
        admin.setRealName(realName);
        admin.setRoleCode(newRole);
        adminMapper.updateById(admin);
        admin.setPasswordHash(null);
        return admin;
    }

    @Transactional
    public void changeStatus(Long id, Integer status) {
        requireSuperAdmin();
        Long selfId = AuthContext.requireAdminId();
        if (selfId.equals(id) && (status == null || status != 1)) {
            throw new IllegalStateException("不能停用自己的账号");
        }
        SysAdmin admin = requireAdmin(id);
        if (ROLE_SUPER_ADMIN.equals(admin.getRoleCode()) && (status == null || status != 1)) {
            ensureNotLastSuperAdmin(id, "不允许停用最后一个超级管理员");
        }
        admin.setStatus(status == null ? 1 : status);
        adminMapper.updateById(admin);
        auditService.record(AuthContext.requireAdminId(), null, "ACCOUNT_STATUS", admin.getUsername(),
                "状态变更为 " + admin.getStatus());
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        requireSuperAdmin();
        if (newPassword == null || newPassword.length() < 8) throw new IllegalArgumentException("密码长度至少 8 位");
        SysAdmin admin = requireAdmin(id);
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        adminMapper.updateById(admin);
        auditService.record(AuthContext.requireAdminId(), null, "ACCOUNT_RESET_PASSWORD", admin.getUsername(), "重置密码");
    }

    /** 角色 -> 权限码列表。 */
    public Map<String, List<String>> rolePermissions() {
        requireSuperAdmin();
        List<SysRolePermission> rows = permissionMapper.selectList(null);
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (SysRolePermission row : rows) {
            result.computeIfAbsent(row.getRoleCode(), k -> new java.util.ArrayList<>()).add(row.getPermissionCode());
        }
        result.putIfAbsent(ROLE_SUPER_ADMIN, List.of("*"));
        result.putIfAbsent(ROLE_OPERATOR, List.of());
        return result;
    }

    /** 覆盖式更新某角色的权限码（SUPER_ADMIN 固定为全部权限，不可修改）。 */
    @Transactional
    public void saveRolePermissions(String roleCode, List<String> permissions) {
        requireSuperAdmin();
        String role = normalizeRole(roleCode);
        if (ROLE_SUPER_ADMIN.equals(role)) throw new IllegalStateException("超级管理员权限不可修改");
        permissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleCode, role));
        if (permissions != null) {
            for (String permission : permissions.stream().distinct().collect(Collectors.toList())) {
                if (permission == null || permission.isBlank()) continue;
                SysRolePermission row = new SysRolePermission();
                row.setRoleCode(role);
                row.setPermissionCode(permission.trim());
                permissionMapper.insert(row);
            }
        }
    }

    private SysAdmin requireAdmin(Long id) {
        SysAdmin admin = adminMapper.selectById(id);
        if (admin == null) throw new IllegalArgumentException("管理员不存在");
        return admin;
    }

    private void ensureNotLastSuperAdmin(Long excludeId, String message) {
        Long count = adminMapper.selectCount(new LambdaQueryWrapper<SysAdmin>()
                .eq(SysAdmin::getRoleCode, ROLE_SUPER_ADMIN)
                .eq(SysAdmin::getStatus, 1)
                .ne(SysAdmin::getId, excludeId));
        if (count == null || count == 0) throw new IllegalStateException(message);
    }

    private String normalizeRole(String roleCode) {
        String role = roleCode == null || roleCode.isBlank() ? ROLE_OPERATOR : roleCode.trim().toUpperCase();
        if (!List.of(ROLE_SUPER_ADMIN, ROLE_OPERATOR, ROLE_FINANCE).contains(role)) {
            throw new IllegalArgumentException("不支持的角色：" + roleCode);
        }
        return role;
    }

    private void requireSuperAdmin() {
        SysAdmin current = adminMapper.selectById(AuthContext.requireAdminId());
        if (current == null || !ROLE_SUPER_ADMIN.equals(current.getRoleCode())) {
            throw new IllegalStateException("仅超级管理员可管理账号与权限");
        }
    }
}
