package com.socialmedia.response;

import com.socialmedia.entity.Users;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Users user;
    private Boolean followedByAuthUser;
}
