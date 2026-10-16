package com.aiscam.antiscam.bootstrap;

import com.aiscam.antiscam.auth.domain.*;
import com.aiscam.antiscam.auth.username.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountInitializer implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public AdminAccountInitializer(UserRepository users, PasswordEncoder encoder,
            @Value("${app.admin.username:}") String username, @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isEmpty() && password.isEmpty()) { return; }
        String normalized = Username.normalize(username);
        Username.validate(normalized);
        PasswordPolicy.validate(password);
        var existing = users.findByUsernameKey(Username.key(normalized));
        if (existing.isPresent()) {
            if (existing.get().getRole() != UserRole.ADMIN) {
                throw new IllegalStateException("管理员用户名已被普通账号占用，请更换配置");
            }
            return;
        }
        users.saveAndFlush(new User(normalized, encoder.encode(password), UserRole.ADMIN));
    }
}
