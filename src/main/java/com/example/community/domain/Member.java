package com.example.community.domain;

import com.example.community.domain.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "members", indexes = {
        @Index(name = "uk_member_username", columnList = "username", unique = true),
        @Index(name = "uk_member_email", columnList = "email", unique = true)
})
public class Member extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 200)
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_roles", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "role")
    private Set<String> roles;
}
