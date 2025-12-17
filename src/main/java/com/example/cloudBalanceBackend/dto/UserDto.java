package com.example.cloudBalanceBackend.dto;

import com.example.cloudBalanceBackend.model.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {

    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private Instant createdAt;

    public static UserDto fromEntity(User u) {
        return UserDto.builder()
                .id(u.getId())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .role(u.getRole().name())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
