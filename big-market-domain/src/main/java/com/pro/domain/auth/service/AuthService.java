package com.pro.domain.auth.service;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 鉴权服务
 * @create 2024-10-07 17:55
 */
@Slf4j
@Service
public class AuthService extends AbstractAuthService {

    @javax.annotation.Resource
    private com.pro.domain.auth.repository.IAuthSessionRepository authSessionRepository;

    @Override
    public boolean checkToken(String token) {
        return token != null && isVerify(token)
                && openid(token).equals(authSessionRepository.queryTokenUserId(token));
    }

    @Override
    public String openid(String token) {
        Claims claims = decode(token);
        return claims.get("openId").toString();
    }

}
