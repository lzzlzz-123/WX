package com.buildmart.controller.miniapp;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.AuthContext;
import com.buildmart.common.PageResult;
import com.buildmart.entity.Inquiry;
import com.buildmart.entity.InquiryQuote;
import com.buildmart.mapper.InquiryMapper;
import com.buildmart.service.InquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Tag(name = "小程序询价", description = "用户询价单与报价查看")
@RestController
@RequestMapping("/miniapp/inquiries")
public class InquiryController {
    private final InquiryMapper inquiryMapper;
    private final InquiryService inquiryService;

    public InquiryController(InquiryMapper inquiryMapper, InquiryService inquiryService) {
        this.inquiryMapper = inquiryMapper;
        this.inquiryService = inquiryService;
    }

    @PostMapping
    @Operation(summary = "提交询价单")
    public ApiResponse<Inquiry> create(@RequestBody Inquiry inquiry) {
        if (inquiry.getProductName() == null || inquiry.getProductName().isBlank()) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (inquiry.getQuantity() == null || inquiry.getQuantity().signum() <= 0) {
            throw new IllegalArgumentException("采购数量必须大于 0");
        }
        inquiry.setId(null);
        inquiry.setUserId(AuthContext.requireUserId());
        inquiry.setInquiryNo("IN" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(100, 1000));
        inquiry.setStatus("PENDING_QUOTE");
        inquiry.setCreatedAt(LocalDateTime.now());
        inquiryMapper.insert(inquiry);
        return ApiResponse.ok(inquiry);
    }

    @GetMapping
    @Operation(summary = "分页查询我的询价单", description = "每条询价附最新一条已发送报价（quote 字段，无报价为 null）。")
    public ApiResponse<PageResult<InquiryView>> page(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long size,
                                                      @RequestParam(required = false) String status) {
        Long uid = AuthContext.requireUserId();
        PageResult<Inquiry> result = inquiryService.pageForUser(uid, page, size, status);
        List<Long> ids = result.records().stream().map(Inquiry::getId).toList();
        Map<Long, InquiryQuote> quotes = inquiryService.latestQuotes(ids);
        List<InquiryView> views = result.records().stream()
                .map(inquiry -> new InquiryView(inquiry, quotes.get(inquiry.getId())))
                .toList();
        return ApiResponse.ok(new PageResult<>(views, result.total(), result.page(), result.size()));
    }

    /** 询价单 + 最新报价的组合视图。 */
    public record InquiryView(Inquiry inquiry, InquiryQuote quote) {
    }
}
