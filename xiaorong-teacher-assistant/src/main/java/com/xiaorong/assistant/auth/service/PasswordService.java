package com.xiaorong.assistant.auth.service;

import com.xiaorong.assistant.config.XiaorongProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    private final BCryptPasswordEncoder encoder;

    public PasswordService(XiaorongProperties properties) {
        int strength = Math.max(10, Math.min(14, properties.getAuth().getBcryptStrength()));
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
