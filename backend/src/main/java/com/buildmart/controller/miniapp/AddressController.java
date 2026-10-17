package com.buildmart.controller.miniapp;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.AuthContext;
import com.buildmart.entity.UserAddress;
import com.buildmart.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "小程序地址", description = "收货地址管理")
@RestController
@RequestMapping("/miniapp/addresses")
public class AddressController {
    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    @Operation(summary = "查询收货地址")
    public ApiResponse<List<UserAddress>> list() {
        return ApiResponse.ok(addressService.list(AuthContext.requireUserId()));
    }

    @PostMapping
    @Operation(summary = "保存收货地址", description = "新增或更新当前用户的收货地址。")
    public ApiResponse<UserAddress> save(@RequestBody UserAddress address) {
        return ApiResponse.ok(addressService.save(AuthContext.requireUserId(), address));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除收货地址")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        addressService.delete(AuthContext.requireUserId(), id);
        return ApiResponse.ok();
    }
}
