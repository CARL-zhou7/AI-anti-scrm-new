package com.aiscam.antiscam.auth.service;

import com.aiscam.antiscam.auth.domain.*;
import com.aiscam.antiscam.auth.username.*;
import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
        this.dummyHash = encoder.encode("nonexistent-account-timing-check");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Account register(String rawUsername, String password, String confirmation) {
        String username = Username.normalize(rawUsername);
        Username.validate(username);
        PasswordPolicy.validate(password, confirmation);
        if (users.existsByUsernameKey(Username.key(username))) { throw usernameTaken(); }
        try {
            return Account.from(users.saveAndFlush(new User(username, encoder.encode(password), UserRole.USER)));
        } catch (DataIntegrityViolationException ex) {
            throw usernameTaken();
        }
    }

    @Transactional(readOnly = true)
    public Account authenticate(String rawUsername, String password) {
        String username = Username.normalize(rawUsername);
        Username.validate(username);
        PasswordPolicy.validate(password);
        var user = users.findByUsernameKey(Username.key(username));
        boolean matches = encoder.matches(password, user.map(User::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "账号或密码错误");
        }
        return Account.from(user.get());
    }

    private static ApiException usernameTaken() {
        return new ApiException(409, "USERNAME_TAKEN", "用户名已被使用");
    }
}
