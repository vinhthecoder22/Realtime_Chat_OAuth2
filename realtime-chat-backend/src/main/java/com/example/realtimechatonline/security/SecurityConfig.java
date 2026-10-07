package com.example.realtimechatonline.security;

import lombok.RequiredArgsConstructor;
import com.example.realtimechatonline.constant.SecurityPath;
import com.example.realtimechatonline.security.CustomUserDetailsService;
import com.example.realtimechatonline.security.jwt.JwtAuthenticationFilter;
import com.example.realtimechatonline.security.jwt.RestAuthenticationEntryPoint;
import com.example.realtimechatonline.security.oauth2.OAuth2LoginFailureHandler;
import com.example.realtimechatonline.security.oauth2.OAuth2LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter     jwtAuthenticationFilter;
    private final CustomUserDetailsService    userDetailsService;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final OAuth2LoginSuccessHandler   oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler   oAuth2LoginFailureHandler;
    private final CorsConfigurationSource     corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Security headers
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentSecurityPolicy(csp ->
                                csp.policyDirectives("default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; " +
                                        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                                        "font-src 'self' https://fonts.gstatic.com; " +
                                        "connect-src 'self' ws: wss: http://localhost:8080 https://cdnjs.cloudflare.com; " +
                                        "img-src 'self' data:;")
                        )
                )

                // CORS — delegated to CorsConfig bean
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // CSRF — disabled for stateless JWT API
                .csrf(AbstractHttpConfigurer::disable)

                // Session — stateless: no HTTP session for JWT-based auth
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Auth exception handling
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(restAuthenticationEntryPoint)
                )

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Cho phép preflight CORS
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Cho phép WebSocket
                        .requestMatchers("/ws-chat/**").permitAll()

                        // Cho phép Auth API
                         .requestMatchers("/api/v1/auth/**").permitAll()

                        //.requestMatchers(SecurityPath.ANONYMOUS).permitAll()

                        // Public, Static, Swagger
                        .requestMatchers(SecurityPath.PUBLIC).permitAll()
                        .requestMatchers(
                                "/favicon.ico",
                                "/error",
                                "/oauth2-redirect.html",
                                "/chat.html",
                                "/index.html",
                                "/css/**",
                                "/js/**",
                                "/images/**").permitAll()

                        // Admin
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )

                // OAuth2 login
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler)
                )

                // JWT filter runs before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}