package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.buildmart.entity.UserAddress;
import com.buildmart.mapper.UserAddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressService {
    private final UserAddressMapper addressMapper;

    public AddressService(UserAddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    public List<UserAddress> list(Long userId) {
        return addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId).orderByDesc(UserAddress::getIsDefault).orderByDesc(UserAddress::getCreatedAt));
    }

    @Transactional
    public UserAddress save(Long userId, UserAddress address) {
        address.setUserId(userId);
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefault(userId);
        }
        if (address.getId() == null) addressMapper.insert(address);
        else {
            UserAddress owned = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                    .eq(UserAddress::getId, address.getId()).eq(UserAddress::getUserId, userId));
            if (owned == null) throw new IllegalArgumentException("收货地址不存在");
            addressMapper.updateById(address);
        }
        return address;
    }

    public void delete(Long userId, Long id) {
        addressMapper.delete(new LambdaQueryWrapper<UserAddress>().eq(UserAddress::getId, id).eq(UserAddress::getUserId, userId));
    }

    private void clearDefault(Long userId) {
        UserAddress update = new UserAddress();
        update.setIsDefault(0);
        addressMapper.update(update, new LambdaQueryWrapper<UserAddress>().eq(UserAddress::getUserId, userId));
    }
}
