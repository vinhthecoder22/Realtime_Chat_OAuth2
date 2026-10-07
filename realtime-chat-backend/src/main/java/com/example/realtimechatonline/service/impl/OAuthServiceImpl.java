package com.example.realtimechatonline.service.impl;

import com.example.realtimechatonline.constant.ErrorMessage;
import com.example.realtimechatonline.domain.entity.AuthProvider;
import com.example.realtimechatonline.domain.entity.Role;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.exception.extended.OAuth2EmailConflictException;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.security.jwt.JwtTokenProvider;
import com.example.realtimechatonline.service.OAuthService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthServiceImpl implements OAuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public String login(Map<String, Object> attributes, Authentication authentication) {
        String email    = (String) attributes.get("email");
        String name     = (String) attributes.get("name");
        String picture  = (String) attributes.get("picture");

        Optional<User> existingOpt = userRepository.findByEmail(email);

        if (existingOpt.isPresent()) {
            User existing = existingOpt.get();

            if (existing.getAuthProvider() == AuthProvider.LOCAL) {
                throw new OAuth2EmailConflictException(ErrorMessage.User.ERR_OAUTH2_EMAIL_CONFLICT);
            }

            log.debug("OAuth2 login for existing user: {}", email);
            return jwtTokenProvider.generateToken(existing);
        }

        // New OAuth2 user — auto-register
        User newUser = User.builder()
                .email(email)
                .username(email)          // use email as username for OAuth2 users
                .password(null)           // no password for OAuth2 users
                .fullname(name)
                .picture(picture)
                .role(Role.USER)
                .authProvider(AuthProvider.GOOGLE)
                .build();

        User saved = userRepository.save(newUser);
        log.debug("New OAuth2 user registered: {}", email);
        return jwtTokenProvider.generateToken(saved);
    }
}
