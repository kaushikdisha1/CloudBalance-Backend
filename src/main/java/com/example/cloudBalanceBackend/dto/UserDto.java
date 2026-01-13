package com.example.cloudBalanceBackend.dto;

import com.example.cloudBalanceBackend.model.User;
import com.example.cloudBalanceBackend.model.UserAccount;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {

    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String name;  // Add full name for frontend compatibility
    private String role;
    private List<AccountResponse> accounts;  // Add accounts list
    private Instant createdAt;

    public static UserDto fromEntity(User u) {
//        this.accountName = acc.getName();
//        this.accountNumber = acc.getProviderAccountId();
//        this.provider = acc.getProvider();
//        this.status = acc.getStatus();
//        this.createdAt = acc.getCreatedAt();
//        this.updatedAt = acc.getUpdatedAt();
        List<AccountResponse> accounts=u.getAccount()==null?List.of(): u.getAccount()
                .stream()
                .map(a->new AccountResponse(
                        a.getAccount().getId(),
                        a.getAccount().getProviderAccountId(),
                        a.getAccount().getName(),
                        a.getAccount().getMeta()
                ))
                .toList();

        return UserDto.builder()
                .id(u.getId())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .name(u.getName())  // Use transient getName() method
                .role(u.getRole().name())
                .accounts(accounts)
                .createdAt(u.getCreatedAt())
                .build();
    }

    // Constructor with accounts
//    public static UserDto fromEntity(User u, List<String> accountIds) {
////
//        return UserDto.builder()
//                .id(u.getId())
//                .email(u.getEmail())
//                .firstName(u.getFirstName())
//                .lastName(u.getLastName())
//                .name(u.getName())
//                .role(u.getRole().name())
//                .accounts(accountIds)
//                .createdAt(u.getCreatedAt())
//                .build();
//    }
}