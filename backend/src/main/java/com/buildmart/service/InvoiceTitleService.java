package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.buildmart.entity.InvoiceTitle;
import com.buildmart.mapper.InvoiceTitleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvoiceTitleService {
    private final InvoiceTitleMapper invoiceTitleMapper;

    public InvoiceTitleService(InvoiceTitleMapper invoiceTitleMapper) {
        this.invoiceTitleMapper = invoiceTitleMapper;
    }

    public List<InvoiceTitle> list(Long userId) {
        return invoiceTitleMapper.selectList(new LambdaQueryWrapper<InvoiceTitle>()
                .eq(InvoiceTitle::getUserId, userId)
                .orderByDesc(InvoiceTitle::getIsDefault)
                .orderByDesc(InvoiceTitle::getCreatedAt));
    }

    @Transactional
    public InvoiceTitle save(Long userId, InvoiceTitle invoice) {
        validate(invoice);
        invoice.setUserId(userId);
        if (invoice.getTitleType() == null || invoice.getTitleType().isBlank()) {
            invoice.setTitleType("COMPANY");
        }
        if (invoice.getIsDefault() != null && invoice.getIsDefault() == 1) {
            clearDefault(userId);
        }
        if (invoice.getId() == null) {
            // 首个抬头自动设为默认，省去用户再点一次。
            long count = invoiceTitleMapper.selectCount(new LambdaQueryWrapper<InvoiceTitle>()
                    .eq(InvoiceTitle::getUserId, userId));
            if (count == 0) {
                clearDefault(userId);
                invoice.setIsDefault(1);
            } else if (invoice.getIsDefault() == null) {
                invoice.setIsDefault(0);
            }
            invoice.setCreatedAt(LocalDateTime.now());
            invoiceTitleMapper.insert(invoice);
        } else {
            InvoiceTitle owned = invoiceTitleMapper.selectOne(new LambdaQueryWrapper<InvoiceTitle>()
                    .eq(InvoiceTitle::getId, invoice.getId()).eq(InvoiceTitle::getUserId, userId));
            if (owned == null) throw new IllegalArgumentException("发票抬头不存在");
            if (invoice.getIsDefault() == null) invoice.setIsDefault(owned.getIsDefault());
            invoiceTitleMapper.updateById(invoice);
        }
        return invoice;
    }

    public void delete(Long userId, Long id) {
        invoiceTitleMapper.delete(new LambdaQueryWrapper<InvoiceTitle>()
                .eq(InvoiceTitle::getId, id).eq(InvoiceTitle::getUserId, userId));
    }

    @Transactional
    public void setDefault(Long userId, Long id) {
        InvoiceTitle owned = invoiceTitleMapper.selectOne(new LambdaQueryWrapper<InvoiceTitle>()
                .eq(InvoiceTitle::getId, id).eq(InvoiceTitle::getUserId, userId));
        if (owned == null) throw new IllegalArgumentException("发票抬头不存在");
        clearDefault(userId);
        InvoiceTitle update = new InvoiceTitle();
        update.setId(id);
        update.setIsDefault(1);
        invoiceTitleMapper.updateById(update);
    }

    private void validate(InvoiceTitle invoice) {
        if (invoice.getTitle() == null || invoice.getTitle().isBlank()) {
            throw new IllegalArgumentException("发票抬头不能为空");
        }
        if ("COMPANY".equals(invoice.getTitleType())
                && (invoice.getTaxNo() == null || invoice.getTaxNo().isBlank())) {
            throw new IllegalArgumentException("企业抬头必须填写税号");
        }
    }

    private void clearDefault(Long userId) {
        InvoiceTitle update = new InvoiceTitle();
        update.setIsDefault(0);
        invoiceTitleMapper.update(update, new LambdaQueryWrapper<InvoiceTitle>()
                .eq(InvoiceTitle::getUserId, userId));
    }
}
