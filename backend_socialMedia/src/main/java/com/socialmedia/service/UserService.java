package com.socialmedia.service;

import com.socialmedia.dto.*;
import com.socialmedia.entity.Comment;
import com.socialmedia.entity.Post;
import com.socialmedia.entity.Users;
import com.socialmedia.response.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    Users getUserById(Long userId);
    Users getUserByEmail(String email);
    List<UserResponse> getFollowerUsersPaginate(Long userId, Integer page, Integer size);
    List<UserResponse> getFollowingUsersPaginate(Long userId, Integer page, Integer size);
    Users createNewUser(SignupDto signupDto);
    Users updateUserInfo(UpdateUserInfoDto updateUserInfoDto);
    Users updateEmail(UpdateEmailDto updateEmailDto);
    Users updatePassword(UpdatePasswordDto updatePasswordDto);
    Users updateProfilePhoto(MultipartFile photo);
    Users updateCoverPhoto(MultipartFile photo);
    Users verifyEmail(String token);
    void forgotPassword(String email);
    Users resetPassword(String token, ResetPasswordDto resetPasswordDto);
    void deleteUserAccount();
    void followUser(Long userId);
    void unfollowUser(Long userId);
    Users getAuthenticatedUser();
    List<UserResponse> getUserSearchResult(String key, Integer page, Integer size);
    List<Users> getLikesByPostPaginate(Post post, Integer page, Integer size);
    List<Users> getLikesByCommentPaginate(Comment comment, Integer page, Integer size);
}
