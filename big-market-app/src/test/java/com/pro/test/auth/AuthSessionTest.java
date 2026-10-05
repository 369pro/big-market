package com.pro.test.auth;

import com.pro.domain.auth.repository.IAuthSessionRepository;
import com.pro.domain.auth.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AuthSessionTest {
    @Test
    public void validSignatureStillRequiresActiveSession() {
        String secret = "test-only-signing-key-at-least-32-bytes";
        AuthService service = new AuthService();
        service.configureJwtSecret(secret);
        IAuthSessionRepository repository = mock(IAuthSessionRepository.class);
        ReflectionTestUtils.setField(service, "authSessionRepository", repository);
        String token = Jwts.builder().claim("openId", "user-1").setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(SignatureAlgorithm.HS256, Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8))).compact();
        when(repository.queryTokenUserId(token)).thenReturn("user-1");
        assertTrue(service.checkToken(token));
        when(repository.queryTokenUserId(token)).thenReturn(null);
        assertFalse(service.checkToken(token));
        assertFalse(service.checkToken(null));
    }
}
