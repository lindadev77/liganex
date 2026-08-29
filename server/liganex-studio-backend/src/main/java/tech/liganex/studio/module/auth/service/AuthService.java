package tech.liganex.studio.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.auth.dto.LoginRequest;
import tech.liganex.studio.module.auth.dto.RegisterRequest;
import tech.liganex.studio.module.auth.dto.TokenResponse;
import tech.liganex.studio.module.auth.dto.UserResponse;
import tech.liganex.studio.module.auth.entity.User;
import tech.liganex.studio.module.auth.mapper.UserMapper;
import tech.liganex.studio.module.auth.security.JwtProperties;
import tech.liganex.studio.module.auth.security.JwtTokenProvider;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        Long duplicated = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.email()));
        if (duplicated != null && duplicated > 0) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? request.email().substring(0, request.email().indexOf('@'))
                : request.displayName());
        user.setStatus("ACTIVE");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userMapper.insert(user);

        log.info("user registered id={}", user.getId());
        return UserResponse.from(user);
    }

    public TokenResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.email()));

        // 用户不存在与密码错误返回同一错误，避免账号枚举
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.info("login failed for email={}", request.email());
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        return issueTokens(user);
    }

    public TokenResponse refresh(String refreshToken) {
        Jwt jwt;
        try {
            jwt = jwtTokenProvider.parse(refreshToken);
        } catch (JwtException ex) {
            throw new BizException(ErrorCode.INVALID_TOKEN);
        }
        // refresh token 不能当 access token 用
        if (jwtTokenProvider.isAccessToken(jwt)) {
            throw new BizException(ErrorCode.INVALID_TOKEN);
        }
        User user = userMapper.selectById(jwtTokenProvider.extractUserId(jwt));
        if (user == null || !user.isActive()) {
            throw new BizException(ErrorCode.INVALID_TOKEN);
        }
        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        return TokenResponse.of(
                jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail()),
                jwtTokenProvider.generateRefreshToken(user.getId()),
                jwtProperties.accessTokenTtl().toSeconds());
    }
}
