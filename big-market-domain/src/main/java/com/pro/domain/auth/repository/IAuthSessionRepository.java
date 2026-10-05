package com.pro.domain.auth.repository;

public interface IAuthSessionRepository {
    String queryTokenUserId(String token);
}
