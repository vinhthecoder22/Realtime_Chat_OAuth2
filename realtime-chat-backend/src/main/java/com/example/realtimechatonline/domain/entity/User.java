package com.example.realtimechatonline.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    String username;

    @Column(name = "password")
    String password;

    @Column(name = "fullname")
    String fullname;

    @Column(name = "email", unique = true, length = 250)
    String email;

    @Column(name = "picture")
    String picture;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    Role role = Role.USER;

    /**
     * Tracks which identity provider created this account.
     * LOCAL  — registered via username/password form.
     * GOOGLE — registered via Google OAuth2.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    @Builder.Default
    AuthProvider authProvider = AuthProvider.LOCAL;
}
