package com.socialmedia.dto;

import com.socialmedia.annotation.ValidEmail;
import com.socialmedia.annotation.ValidPassword;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginDto {
    @ValidEmail
    private String email;

    @ValidPassword
    private String password;
}
