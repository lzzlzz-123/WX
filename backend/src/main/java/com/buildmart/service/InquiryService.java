package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.buildmart.common.PageResult;
import com.buildmart.entity.Inquiry;
import com.buildmart.entity.InquiryQuote;
import com.buildmart.mapper.InquiryMapper;
import com.buildmart.mapper.InquiryQuoteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InquiryService {
    private final InquiryMapper inquiryMapper;
    private final InquiryQuoteMapper quoteMapper;

    public InquiryService(InquiryMapper inquiryMapper, InquiryQuoteMapper quoteMapper) {
        this.inquiryMapper = inquiryMapper;
        this.quoteMapper = quoteMapper;
    }

    public PageResult<Inquiry> page(long page, long size, String status) {
        var query = new LambdaQueryWrapper<Inquiry>()
                .eq(status != null && !status.isBlank(), Inquiry::getStatus, status)
                .orderByDesc(Inquiry::getCreatedAt);
        Page<Inquiry> result = inquiryMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 小程序端：分页查询当前用户的询价单。 */
    public PageResult<Inquiry> pageForUser(Long userId, long page, long size, String status) {
        var query = new LambdaQueryWrapper<Inquiry>()
                .eq(Inquiry::getUserId, userId)
                .eq(status != null && !status.isBlank(), Inquiry::getStatus, status)
                .orderByDesc(Inquiry::getCreatedAt);
        Page<Inquiry> result = inquiryMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 管理端报价：写入报价单（直接置为 SENT 对用户可见），并把询价单状态推进为 QUOTED。
     */
    @Transactional
    public InquiryQuote quote(Long inquiryId, Long adminId, BigDecimal quotePrice, LocalDate validUntil,
                              BigDecimal deliveryFee, String remark) {
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) throw new IllegalArgumentException("询价单不存在");
        if ("CLOSED".equals(inquiry.getStatus())) throw new IllegalStateException("询价单已关闭，无法报价");
        if (quotePrice == null || quotePrice.signum() <= 0) throw new IllegalArgumentException("报价必须大于 0");

        InquiryQuote quote = new InquiryQuote();
        quote.setInquiryId(inquiryId);
        quote.setAdminId(adminId == null ? 1L : adminId);
        quote.setQuotePrice(quotePrice);
        quote.setValidUntil(validUntil);
        quote.setDeliveryFee(deliveryFee == null ? BigDecimal.ZERO : deliveryFee);
        quote.setRemark(remark);
        quote.setStatus("SENT");
        quote.setCreatedAt(LocalDateTime.now());
        quoteMapper.insert(quote);

        inquiry.setStatus("QUOTED");
        inquiryMapper.updateById(inquiry);
        return quote;
    }

    /** 批量取每个询价单最新一条 SENT 报价（小程序列表展示用）。 */
    public Map<Long, InquiryQuote> latestQuotes(List<Long> inquiryIds) {
        if (inquiryIds == null || inquiryIds.isEmpty()) return Collections.emptyMap();
        List<InquiryQuote> quotes = quoteMapper.selectList(new LambdaQueryWrapper<InquiryQuote>()
                .in(InquiryQuote::getInquiryId, inquiryIds)
                .eq(InquiryQuote::getStatus, "SENT")
                .orderByDesc(InquiryQuote::getCreatedAt));
        // 按询价单分组，每组保留最新一条。
        return quotes.stream().collect(Collectors.toMap(InquiryQuote::getInquiryId, q -> q, (a, b) -> a));
    }
}
