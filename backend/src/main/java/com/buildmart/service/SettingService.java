package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.buildmart.common.CacheKeys;
import com.buildmart.entity.SysSetting;
import com.buildmart.mapper.SysSettingMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Key-value system settings. Reads are cached in Redis (10 min TTL);
 * writes upsert rows then evict the cached snapshot.
 */
@Service
public class SettingService {
    private static final Duration SETTINGS_TTL = Duration.ofMinutes(10);
    private static final int MAX_KEY_LENGTH = 100;
    private static final int MAX_VALUE_LENGTH = 2000;

    private final SysSettingMapper settingMapper;
    private final RedisCacheService cacheService;
    private final ObjectMapper objectMapper;

    public SettingService(SysSettingMapper settingMapper, RedisCacheService cacheService,
                          ObjectMapper objectMapper) {
        this.settingMapper = settingMapper;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    /** All settings as key -> value, merged with descriptions. */
    public Map<String, String> all() {
        JavaType mapType = objectMapper.getTypeFactory()
                .constructMapType(LinkedHashMap.class, String.class, String.class);
        Map<String, String> cached = cacheService.get(CacheKeys.SETTINGS_ALL, mapType);
        if (cached != null) return cached;

        Map<String, String> settings = new LinkedHashMap<>();
        settingMapper.selectList(new LambdaQueryWrapper<SysSetting>().orderByAsc(SysSetting::getId))
                .forEach(row -> settings.put(row.getSettingKey(), row.getSettingValue()));
        cacheService.put(CacheKeys.SETTINGS_ALL, settings, SETTINGS_TTL);
        return settings;
    }

    public String get(String key, String defaultValue) {
        return all().getOrDefault(key, defaultValue);
    }

    @Transactional
    public void save(Map<String, String> settings) {
        if (settings == null || settings.isEmpty()) {
            throw new IllegalArgumentException("设置内容不能为空");
        }
        settings.forEach((key, value) -> {
            String normalizedKey = key == null ? "" : key.trim();
            if (normalizedKey.isEmpty() || normalizedKey.length() > MAX_KEY_LENGTH) {
                throw new IllegalArgumentException("设置项 key 不合法：" + key);
            }
            if (value != null && value.length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("设置项 " + normalizedKey + " 的值过长");
            }
            SysSetting existing = settingMapper.selectOne(new LambdaQueryWrapper<SysSetting>()
                    .eq(SysSetting::getSettingKey, normalizedKey));
            if (existing == null) {
                SysSetting row = new SysSetting();
                row.setSettingKey(normalizedKey);
                row.setSettingValue(value);
                settingMapper.insert(row);
            } else {
                existing.setSettingValue(value);
                settingMapper.updateById(existing);
            }
        });
        cacheService.delete(CacheKeys.SETTINGS_ALL);
    }
}
