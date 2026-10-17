package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.buildmart.common.PageResult;
import com.buildmart.entity.PurchaseOrder;
import com.buildmart.entity.SysUser;
import com.buildmart.mapper.PurchaseOrderMapper;
import com.buildmart.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Admin-side customer (mini-program user) management. */
@Service
public class CustomerService {
    private final SysUserMapper userMapper;
    private final PurchaseOrderMapper orderMapper;

    public CustomerService(SysUserMapper userMapper, PurchaseOrderMapper orderMapper) {
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
    }

    public PageResult<SysUser> page(long page, long size, String keyword, Integer status) {
        var query = new LambdaQueryWrapper<SysUser>()
                .and(keyword != null && !keyword.isBlank(), q -> q
                        .like(SysUser::getNickname, keyword).or()
                        .like(SysUser::getPhone, keyword).or()
                        .like(SysUser::getCompanyName, keyword))
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** Customer profile plus order aggregation for the admin detail view. */
    public CustomerDetail detail(Long id) {
        SysUser user = requireUser(id);
        long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getUserId, id));
        Object value = orderMapper.selectObjs(new QueryWrapper<PurchaseOrder>()
                        .select("COALESCE(SUM(payable_amount), 0) AS payable_amount")
                        .eq("user_id", id)
                        .ne("status", "CANCELLED"))
                .stream().findFirst().orElse(null);
        BigDecimal totalSpent = BigDecimal.ZERO;
        if (value != null) {
            try {
                totalSpent = value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
            } catch (NumberFormatException ignored) {
                // keep ZERO
            }
        }
        PurchaseOrder latest = orderMapper.selectOne(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getUserId, id)
                .orderByDesc(PurchaseOrder::getCreatedAt)
                .last("LIMIT 1"));
        LocalDateTime lastOrderAt = latest == null ? null : latest.getCreatedAt();
        return new CustomerDetail(user, orderCount, totalSpent, lastOrderAt);
    }

    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("status 仅支持 0（禁用）或 1（正常）");
        }
        SysUser user = requireUser(id);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    public void changeCreditLimit(Long id, BigDecimal creditLimit) {
        if (creditLimit == null || creditLimit.signum() < 0) {
            throw new IllegalArgumentException("信用额度不能为负数");
        }
        SysUser user = requireUser(id);
        user.setCreditLimit(creditLimit);
        userMapper.updateById(user);
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new IllegalArgumentException("客户不存在");
        return user;
    }

    public record CustomerDetail(SysUser user, long orderCount, BigDecimal totalSpent, LocalDateTime lastOrderAt) {
    }
}
