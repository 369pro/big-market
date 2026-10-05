package com.pro.infrastructure.adapter.repository;

import com.pro.domain.auth.repository.IAuthSessionRepository;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Repository;
import javax.annotation.Resource;

@Repository
public class AuthSessionRepository implements IAuthSessionRepository {
    @Resource
    private RedissonClient redissonClient;

    @Override
    public String queryTokenUserId(String token) {
        return redissonClient.<String>getBucket("auth_session_" + token, StringCodec.INSTANCE).get();
    }
}
