package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.buildmart.common.PageResult;
import com.buildmart.entity.AdminOperationLog;
import com.buildmart.mapper.AdminOperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 管理员操作审计。写日志失败不影响业务主流程。
 */
@Service
public class AdminAuditService {
    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);

    private final AdminOperationLogMapper logMapper;

    public AdminAuditService(AdminOperationLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    public void record(Long adminId, String adminUsername, String action, String target, String detail, String ip) {
        try {
            AdminOperationLog entry = new AdminOperationLog();
            entry.setAdminId(adminId);
            entry.setAdminUsername(adminUsername);
            entry.setAction(action);
            entry.setTarget(target);
            entry.setDetail(detail == null ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail));
            entry.setIp(ip);
            logMapper.insert(entry);
        } catch (Exception ex) {
            log.warn("审计日志写入失败 action={} target={}：{}", action, target, ex.getMessage());
        }
    }

    public void record(Long adminId, String adminUsername, String action, String target, String detail) {
        record(adminId, adminUsername, action, target, detail, null);
    }

    public PageResult<AdminOperationLog> page(long page, long size, String action) {
        Page<AdminOperationLog> result = logMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AdminOperationLog>()
                        .eq(action != null && !action.isBlank(), AdminOperationLog::getAction, action)
                        .orderByDesc(AdminOperationLog::getId));
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}
