package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.buildmart.common.AuthException;
import com.buildmart.entity.SysUser;
import com.buildmart.mapper.SysUserMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User lookup and provisioning for mini-program authentication. */
@Service
public class UserService {
    private static final String DEFAULT_NICKNAME = "微信用户";

    private final SysUserMapper userMapper;

    public UserService(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * Find or provision a user by the stable WeChat openid.
     *
     * <p>The unique index on {@code sys_user.openid} is the source of truth for
     * concurrent requests. If two first-login requests race, the loser reads
     * the row created by the winner instead of surfacing a duplicate-key error
     * to the mini-program.</p>
     */
    @Transactional
    public SysUser login(String openid, String nickname, String phone) {
        return login(openid, null, nickname, phone);
    }

    /** Same operation with an optional unionid returned by WeChat. */
    @Transactional
    public SysUser login(String openid, String unionid, String nickname, String phone) {
        String normalizedOpenid = requireOpenid(openid);
        String normalizedUnionid = normalize(unionid);
        String normalizedNickname = normalize(nickname);
        String normalizedPhone = normalize(phone);

        SysUser user;
        try {
            user = findByOpenid(normalizedOpenid);
        } catch (DataAccessException ex) {
            throw new AuthException(AuthException.Reason.USER_PERSISTENCE, ex);
        }

        if (user == null) {
            user = createOrReadConcurrent(normalizedOpenid, normalizedUnionid,
                    normalizedNickname, normalizedPhone);
        }

        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new AuthException(AuthException.Reason.USER_DISABLED);
        }

        updateProfileIfPresent(user, normalizedUnionid, normalizedNickname, normalizedPhone);
        return user;
    }

    private SysUser createOrReadConcurrent(String openid, String unionid,
                                           String nickname, String phone) {
        SysUser created = new SysUser();
        created.setOpenid(openid);
        created.setUnionid(unionid);
        created.setNickname(nickname == null ? DEFAULT_NICKNAME : nickname);
        created.setPhone(phone);
        created.setStatus(1);
        try {
            int affected = userMapper.insert(created);
            if (affected <= 0) {
                throw new AuthException(AuthException.Reason.USER_PERSISTENCE);
            }
            return created;
        } catch (DataIntegrityViolationException duplicate) {
            // Another request may have inserted the same openid between our
            // initial SELECT and INSERT. Re-read the canonical row.
            try {
                SysUser existing = findByOpenid(openid);
                if (existing != null) return existing;
            } catch (DataAccessException ignored) {
                // Preserve the stable authentication error below; details stay
                // in the exception cause/logs rather than the API response.
            }
            throw new AuthException(AuthException.Reason.USER_PERSISTENCE, duplicate);
        } catch (AuthException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw new AuthException(AuthException.Reason.USER_PERSISTENCE, ex);
        }
    }

    private void updateProfileIfPresent(SysUser user, String unionid,
                                        String nickname, String phone) {
        boolean changed = false;
        if (unionid != null && !unionid.equals(user.getUnionid())) {
            user.setUnionid(unionid);
            changed = true;
        }
        if (nickname != null && !nickname.equals(user.getNickname())
                && !(DEFAULT_NICKNAME.equals(nickname) && !DEFAULT_NICKNAME.equals(user.getNickname()))) {
            // A relogin must never downgrade a nickname the user chose back
            // to the placeholder default.
            user.setNickname(nickname);
            changed = true;
        }
        if (phone != null && !phone.equals(user.getPhone())) {
            user.setPhone(phone);
            changed = true;
        }
        if (!changed || user.getId() == null) return;
        try {
            userMapper.updateById(user);
        } catch (DataAccessException ex) {
            throw new AuthException(AuthException.Reason.USER_PERSISTENCE, ex);
        }
    }

    /**
     * SMS-code login / registration: find by phone, or provision a phone-only
     * account (openid stays null until the user later binds WeChat). MySQL
     * unique indexes allow multiple NULL openids, so phone-only rows do not
     * collide with WeChat-registered users.
     */
    @Transactional
    public SysUser loginByPhone(String phone, String nickname) {
        String normalizedPhone = normalize(phone);
        if (normalizedPhone == null) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, normalizedPhone)
                .last("LIMIT 1"));
        if (user == null) {
            user = new SysUser();
            user.setPhone(normalizedPhone);
            user.setNickname(normalize(nickname) != null ? normalize(nickname)
                    : "用户" + normalizedPhone.substring(normalizedPhone.length() - 4));
            user.setStatus(1);
            try {
                userMapper.insert(user);
            } catch (DataAccessException ex) {
                throw new AuthException(AuthException.Reason.USER_PERSISTENCE, ex);
            }
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new AuthException(AuthException.Reason.USER_DISABLED);
        }
        return user;
    }

    /** Load a user that must exist, e.g. the id carried by a valid JWT. */
    public SysUser requireById(Long userId) {
        if (userId == null) {
            throw new IllegalStateException("用户未登录");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalStateException("用户不存在或已被删除");
        }
        return user;
    }

    /**
     * Update the editable profile fields. Null arguments keep the current
     * value, so the avatar upload and the nickname form can update their own
     * field without knowing about each other.
     */
    @Transactional
    public SysUser updateProfile(Long userId, String nickname, String avatarUrl) {
        SysUser user = requireById(userId);
        String normalizedNickname = normalize(nickname);
        String normalizedAvatar = normalize(avatarUrl);
        boolean changed = false;
        if (normalizedNickname != null && !normalizedNickname.equals(user.getNickname())) {
            if (normalizedNickname.length() > 80) {
                throw new IllegalArgumentException("昵称长度不能超过 80 个字符");
            }
            user.setNickname(normalizedNickname);
            changed = true;
        }
        if (normalizedAvatar != null && !normalizedAvatar.equals(user.getAvatarUrl())) {
            if (normalizedAvatar.length() > 500) {
                throw new IllegalArgumentException("头像地址不合法");
            }
            user.setAvatarUrl(normalizedAvatar);
            changed = true;
        }
        if (changed) {
            try {
                userMapper.updateById(user);
            } catch (DataAccessException ex) {
                throw new AuthException(AuthException.Reason.USER_PERSISTENCE, ex);
            }
        }
        return user;
    }

    private SysUser findByOpenid(String openid) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getOpenid, openid));
    }

    private String requireOpenid(String openid) {
        String normalized = normalize(openid);
        if (normalized == null) {
            throw new AuthException(AuthException.Reason.OPENID_MISSING);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
