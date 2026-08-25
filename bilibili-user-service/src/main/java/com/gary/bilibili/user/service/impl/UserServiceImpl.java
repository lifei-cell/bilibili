package com.gary.bilibili.user.service.impl;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.user.constant.UserRedisConstant;
import com.gary.bilibili.user.config.AuthSessionProperties;
import com.gary.bilibili.user.dto.LoginDTO;
import com.gary.bilibili.user.dto.PasswordUpdateDTO;
import com.gary.bilibili.user.dto.ProfileUpdateDTO;
import com.gary.bilibili.user.dto.RegisterDTO;
import com.gary.bilibili.user.dto.SendCodeDTO;
import com.gary.bilibili.user.entity.SysUser;
import com.gary.bilibili.user.entity.SysUserAuth;
import com.gary.bilibili.user.mapper.SysUserAuthMapper;
import com.gary.bilibili.user.mapper.SysUserMapper;
import com.gary.bilibili.user.service.UserService;
import com.gary.bilibili.user.service.RefreshSessionService;
import com.gary.bilibili.user.vo.CurrentUserVO;
import com.gary.bilibili.user.vo.LoginVO;
import com.gary.bilibili.user.vo.UserInfoVO;
import com.gary.bilibili.user.vo.UserProfileVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String IDENTITY_PHONE = "phone";
    private static final String IDENTITY_PASSWORD = "password";

    private final SysUserMapper userMapper;
    private final SysUserAuthMapper userAuthMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final AuthSessionProperties authSessionProperties;
    private final RefreshSessionService refreshSessionService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(SysUserMapper userMapper,
                           SysUserAuthMapper userAuthMapper,
                           StringRedisTemplate stringRedisTemplate,
                           AuthSessionProperties authSessionProperties,
                           RefreshSessionService refreshSessionService) {
        this.userMapper = userMapper;
        this.userAuthMapper = userAuthMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.authSessionProperties = authSessionProperties;
        this.refreshSessionService = refreshSessionService;
    }

    @Override
    public void sendCode(SendCodeDTO request) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set(codeKey(request.getPhone()), code,
                Duration.ofMinutes(UserRedisConstant.LOGIN_CODE_TTL_MINUTES));
        log.info("Verification code created, phoneSuffix={}",
                request.getPhone().substring(request.getPhone().length() - 4));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO request) {
        verifyCode(request.getPhone(), request.getCode());
        assertPhoneAvailable(request.getPhone());
        assertUsernameAvailable(request.getUsername());

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setNickname(request.getUsername());
        user.setPhone(request.getPhone());
        user.setGender(0);
        user.setRole("user");
        user.setStatus(0);
        user.setDeleted(0);
        userMapper.insert(user);

        userAuthMapper.insert(buildAuth(user.getId(), IDENTITY_PHONE, request.getPhone(), null, 1));
        userAuthMapper.insert(buildAuth(user.getId(), IDENTITY_PASSWORD, request.getUsername(),
                passwordEncoder.encode(request.getPassword()), 1));

        stringRedisTemplate.delete(codeKey(request.getPhone()));
        return createLoginResult(user, request.getTerminal());
    }

    @Override
    public LoginVO login(LoginDTO request) {
        if (StringUtils.hasText(request.getPhone()) && StringUtils.hasText(request.getCode())) {
            verifyCode(request.getPhone(), request.getCode());
            SysUser user = findUserByPhone(request.getPhone());
            if (user == null) {
                throw new BusinessException("手机号未注册");
            }
            stringRedisTemplate.delete(codeKey(request.getPhone()));
            return createLoginResult(user, request.getTerminal());
        }

        if (StringUtils.hasText(request.getUsername()) && StringUtils.hasText(request.getPassword())) {
            SysUser user = findUserByUsername(request.getUsername());
            if (user == null) {
                throw new BusinessException("用户名或密码错误");
            }
            SysUserAuth auth = findPasswordAuth(user);
            if (auth == null || !passwordEncoder.matches(request.getPassword(), auth.getCredential())) {
                throw new BusinessException("用户名或密码错误");
            }
            return createLoginResult(user, request.getTerminal());
        }

        throw new BusinessException("请输入手机号验证码或用户名密码");
    }

    @Override
    public LoginVO refresh(Long userId, String terminal) {
        return createLoginResult(loadEnabledUser(userId), terminal);
    }

    @Override
    public void logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
    }

    @Override
    public CurrentUserVO getCurrentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        return toCurrentUser(loadEnabledUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(ProfileUpdateDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = loadEnabledUser(userId);

        SysUser update = new SysUser();
        update.setId(user.getId());
        if (request.getNickname() != null) {
            update.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            update.setAvatar(request.getAvatar());
        }
        if (request.getGender() != null) {
            update.setGender(request.getGender());
        }
        if (request.getBirthday() != null) {
            update.setBirthday(request.getBirthday());
        }
        if (request.getSignature() != null) {
            update.setSignature(request.getSignature());
        }
        userMapper.updateById(update);
        stringRedisTemplate.delete(userInfoKey(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(PasswordUpdateDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = loadEnabledUser(userId);
        SysUserAuth auth = findPasswordAuth(user);
        if (auth == null || !passwordEncoder.matches(request.getOldPassword(), auth.getCredential())) {
            throw new BusinessException("旧密码错误");
        }
        if (!isValidPassword(request.getNewPassword())) {
            throw new BusinessException("密码不符合规则");
        }

        userAuthMapper.update(null, new LambdaUpdateWrapper<SysUserAuth>()
                .eq(SysUserAuth::getId, auth.getId())
                .set(SysUserAuth::getCredential, passwordEncoder.encode(request.getNewPassword())));
        stringRedisTemplate.delete(userInfoKey(userId));
        StpUtil.logout(userId);
        refreshSessionService.revokeAll(userId);
    }

    @Override
    public UserProfileVO getProfile(Long userId) {
        SysUser user = loadEnabledUser(userId);
        UserProfileVO profile = new UserProfileVO();
        profile.setId(user.getId());
        profile.setUsername(user.getUsername());
        profile.setNickname(user.getNickname());
        profile.setAvatar(user.getAvatar());
        profile.setSignature(user.getSignature());
        profile.setVideoCount(nullToZero(userMapper.countPublishedVideos(userId)));
        profile.setFollowerCount(nullToZero(userMapper.countFollowers(userId)));
        profile.setFollowingCount(nullToZero(userMapper.countFollowing(userId)));
        profile.setIsFollowing(isCurrentUserFollowing(userId));
        return profile;
    }

    private void verifyCode(String phone, String code) {
        String cachedCode = stringRedisTemplate.opsForValue().get(codeKey(phone));
        if (!StringUtils.hasText(cachedCode) || !cachedCode.equals(code)) {
            throw new BusinessException("验证码错误");
        }
    }

    private void assertPhoneAvailable(String phone) {
        if (findUserByPhone(phone) != null) {
            throw new BusinessException("手机号已注册");
        }
    }

    private void assertUsernameAvailable(String username) {
        if (findUserByUsername(username) != null) {
            throw new BusinessException("用户名已存在");
        }
    }

    private SysUser findUserByPhone(String phone) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .eq(SysUser::getDeleted, 0));
    }

    private SysUser findUserByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDeleted, 0));
    }

    private SysUser loadEnabledUser(Long userId) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getDeleted, 0));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException("账号已被禁用");
        }
        return user;
    }

    private SysUserAuth findPasswordAuth(SysUser user) {
        return userAuthMapper.selectOne(new LambdaQueryWrapper<SysUserAuth>()
                .eq(SysUserAuth::getUserId, user.getId())
                .eq(SysUserAuth::getIdentityType, IDENTITY_PASSWORD)
                .eq(SysUserAuth::getIdentifier, user.getUsername()));
    }

    private SysUserAuth buildAuth(Long userId, String identityType, String identifier,
                                  String credential, Integer verified) {
        SysUserAuth auth = new SysUserAuth();
        auth.setUserId(userId);
        auth.setIdentityType(identityType);
        auth.setIdentifier(identifier);
        auth.setCredential(credential);
        auth.setVerified(verified);
        return auth;
    }

    private LoginVO createLoginResult(SysUser user, String terminal) {
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException("账号已被禁用");
        }

        SaLoginParameter loginModel = new SaLoginModel()
                .setDevice(normalizeTerminal(terminal))
                .setTimeout(authSessionProperties.getAccessTokenTtlSeconds())
                .setIsShare(false)
                .setIsLastingCookie(false)
                .setExtra("username", user.getUsername())
                .setExtra("role", user.getRole());
        StpUtil.login(user.getId(), loginModel);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(StpUtil.getTokenValue());
        loginVO.setExpiresIn(authSessionProperties.getAccessTokenTtlSeconds());
        loginVO.setUser(toUserInfo(user));
        return loginVO;
    }

    private UserInfoVO toUserInfo(SysUser user) {
        UserInfoVO userInfoVO = new UserInfoVO();
        userInfoVO.setId(user.getId());
        userInfoVO.setUsername(user.getUsername());
        userInfoVO.setNickname(user.getNickname());
        userInfoVO.setAvatar(user.getAvatar());
        userInfoVO.setRole(user.getRole());
        return userInfoVO;
    }

    private CurrentUserVO toCurrentUser(SysUser user) {
        CurrentUserVO currentUserVO = new CurrentUserVO();
        currentUserVO.setId(user.getId());
        currentUserVO.setUsername(user.getUsername());
        currentUserVO.setNickname(user.getNickname());
        currentUserVO.setPhone(user.getPhone());
        currentUserVO.setAvatar(user.getAvatar());
        currentUserVO.setGender(user.getGender());
        currentUserVO.setBirthday(user.getBirthday());
        currentUserVO.setSignature(user.getSignature());
        currentUserVO.setRole(user.getRole());
        return currentUserVO;
    }

    private Boolean isCurrentUserFollowing(Long targetUserId) {
        if (!StpUtil.isLogin()) {
            return false;
        }
        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (currentUserId.equals(targetUserId)) {
            return false;
        }
        return nullToZero(userMapper.countFollowRelation(currentUserId, targetUserId)) > 0;
    }

    private boolean isValidPassword(String password) {
        if (!StringUtils.hasText(password) || password.length() < 8 || password.length() > 64) {
            return false;
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }

    private Long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private String normalizeTerminal(String terminal) {
        return StringUtils.hasText(terminal) ? terminal : "web";
    }

    private String codeKey(String phone) {
        return UserRedisConstant.LOGIN_CODE_PREFIX + phone;
    }

    private String userInfoKey(Long userId) {
        return UserRedisConstant.USER_INFO_PREFIX + userId;
    }
}
