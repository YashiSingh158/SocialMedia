package com.socialmedia.service;

import com.socialmedia.common.AppConstants;
import com.socialmedia.common.UserPrincipal;
import com.socialmedia.entity.Comment;
import com.socialmedia.entity.Country;
import com.socialmedia.entity.Post;
import com.socialmedia.entity.Users;
import com.socialmedia.enumeration.Role;
import com.socialmedia.exception.EmailExistsException;
import com.socialmedia.exception.InvalidOperationException;
import com.socialmedia.exception.SameEmailUpdateException;
import com.socialmedia.exception.UserNotFoundException;
import com.socialmedia.mapper.MapStructMapper;
import com.socialmedia.mapper.MapstructMapperUpdate;
import com.socialmedia.repository.UserRepository;
import com.socialmedia.response.UserResponse;
import com.socialmedia.dto.*;
import com.socialmedia.util.FileNamingUtil;
import com.socialmedia.util.FileUploadUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final CountryService countryService;
    private final EmailService emailService;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final MapStructMapper mapStructMapper;
    private final MapstructMapperUpdate mapstructMapperUpdate;
    private final Environment environment;
    private final FileNamingUtil fileNamingUtil;
    private final FileUploadUtil fileUploadUtil;

    @Override
    public Users getUserById(Long userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    @Override
    public Users getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
    }

    @Override
    public List<UserResponse> getFollowerUsersPaginate(Long userId, Integer page, Integer size) {
        Users targetUser = getUserById(userId);
        return userRepository.findUsersByFollowingUsers(targetUser,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "firstName", "lastName")))
                .stream().map(this::userToUserResponse).collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getFollowingUsersPaginate(Long userId, Integer page, Integer size) {
        Users targetUser = getUserById(userId);
        return userRepository.findUsersByFollowerUsers(targetUser,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "firstName", "lastName")))
                .stream().map(this::userToUserResponse).collect(Collectors.toList());
    }

    @Override
    public Users createNewUser(SignupDto signupDto) {
        try {
            Users user = getUserByEmail(signupDto.getEmail());
            if (user != null) {
                throw new EmailExistsException();
            }
        } catch (UserNotFoundException e) {
            Users newUser = new Users();
            newUser.setEmail(signupDto.getEmail());
            newUser.setPassword(passwordEncoder.encode(signupDto.getPassword()));
            newUser.setFirstName(signupDto.getFirstName());
            newUser.setLastName(signupDto.getLastName());
            newUser.setFollowerCount(0);
            newUser.setFollowingCount(0);
            newUser.setEnabled(true);
            newUser.setAccountVerified(false);
            newUser.setEmailVerified(false);
            newUser.setJoinDate(new Date());
            newUser.setDateLastModified(new Date());
            newUser.setRole(Role.ROLE_USER.name());
            Users savedUser = userRepository.save(newUser);
            UserPrincipal userPrincipal = new UserPrincipal(savedUser);
            String emailVerifyMail =
                    emailService.buildEmailVerifyMail(jwtTokenService.generateToken(userPrincipal));
            emailService.send(savedUser.getEmail(), AppConstants.VERIFY_EMAIL, emailVerifyMail);
            return savedUser;
        }
        return null;
    }

    @Override
    public Users updateUserInfo(UpdateUserInfoDto updateUserInfoDto) {
        Users authUser = getAuthenticatedUser();
        if (updateUserInfoDto.getCountryName() != null) {
            Country selectedUserCountry = countryService.getCountryByName(updateUserInfoDto.getCountryName());
            authUser.setCountry(selectedUserCountry);
        }
        mapstructMapperUpdate.updateUserFromUserUpdateDto(updateUserInfoDto, authUser);
        return userRepository.save(authUser);
    }

    @Override
    public Users updateEmail(UpdateEmailDto updateEmailDto) {
        Users authUser = getAuthenticatedUser();
        String newEmail = updateEmailDto.getEmail();
        String password = updateEmailDto.getPassword();

        if (!newEmail.equalsIgnoreCase(authUser.getEmail())) {
            try {
                Users duplicateUser = getUserByEmail(newEmail);
                if (duplicateUser != null) {
                    throw new EmailExistsException();
                }
            } catch (UserNotFoundException e) {
                if (passwordEncoder.matches(password, authUser.getPassword())) {
                    authUser.setEmail(newEmail);
                    authUser.setEmailVerified(false);
                    authUser.setDateLastModified(new Date());
                    Users updatedUser = userRepository.save(authUser);
                    UserPrincipal userPrincipal = new UserPrincipal(updatedUser);
                    String emailVerifyMail =
                            emailService.buildEmailVerifyMail(jwtTokenService.generateToken(userPrincipal));
                    emailService.send(updatedUser.getEmail(), AppConstants.VERIFY_EMAIL, emailVerifyMail);
                    return updatedUser;
                } else {
                    throw new InvalidOperationException();
                }
            }
        } else {
            throw new SameEmailUpdateException();
        }
        return null;
    }

    @Override
    public Users updatePassword(UpdatePasswordDto updatePasswordDto) {
        Users authUser = getAuthenticatedUser();
        if (passwordEncoder.matches(updatePasswordDto.getOldPassword(), authUser.getPassword())) {
            authUser.setPassword(passwordEncoder.encode(updatePasswordDto.getPassword()));
            authUser.setDateLastModified(new Date());
            return userRepository.save(authUser);
        } else {
            throw new InvalidOperationException();
        }
    }

    @Override
    public Users verifyEmail(String token) {
        String targetEmail = jwtTokenService.getSubjectFromToken(token);
        Users targetUser = getUserByEmail(targetEmail);
        targetUser.setEmailVerified(true);
        targetUser.setAccountVerified(true);
        targetUser.setDateLastModified(new Date());
        return userRepository.save(targetUser);
    }

    @Override
    public Users updateProfilePhoto(MultipartFile profilePhoto) {
        Users targetUser = getAuthenticatedUser();
        if (!profilePhoto.isEmpty() && profilePhoto.getSize() > 0) {
            String uploadDir = environment.getProperty("upload.user.images");
            String oldPhotoName = targetUser.getProfilePhoto();
            String newPhotoName = fileNamingUtil.nameFile(profilePhoto);
            String newPhotoUrl = environment.getProperty("app.root.backend") + File.separator
                    + environment.getProperty("upload.user.images") + File.separator + newPhotoName;
            targetUser.setProfilePhoto(newPhotoUrl);
            try {
                if (oldPhotoName == null) {
                    fileUploadUtil.saveNewFile(uploadDir, newPhotoName, profilePhoto);
                } else {
                    fileUploadUtil.updateFile(uploadDir, oldPhotoName, newPhotoName, profilePhoto);
                }
            } catch (IOException e) {
                throw new RuntimeException();
            }
        }
        return userRepository.save(targetUser);
    }

    @Override
    public Users updateCoverPhoto(MultipartFile coverPhoto) {
        Users targetUser = getAuthenticatedUser();
        if (!coverPhoto.isEmpty() && coverPhoto.getSize() > 0) {
            String uploadDir = environment.getProperty("upload.user.images");
            String oldPhotoName = targetUser.getCoverPhoto();
            String newPhotoName = fileNamingUtil.nameFile(coverPhoto);
            String newPhotoUrl = environment.getProperty("app.root.backend") + File.separator
                    + environment.getProperty("upload.user.images") + File.separator + newPhotoName;
            targetUser.setCoverPhoto(newPhotoUrl);
            try {
                if (oldPhotoName == null) {
                    fileUploadUtil.saveNewFile(uploadDir, newPhotoName, coverPhoto);
                } else {
                    fileUploadUtil.updateFile(uploadDir, oldPhotoName, newPhotoName, coverPhoto);
                }
            } catch (IOException e) {
                throw new RuntimeException();
            }
        }
        return userRepository.save(targetUser);
    }

    @Override
    public void forgotPassword(String email) {
        try {
            Users targetUser = getUserByEmail(email);
            UserPrincipal userPrincipal = new UserPrincipal(targetUser);
            String emailVerifyMail =
                    emailService.buildResetPasswordMail(jwtTokenService.generateToken(userPrincipal));
            emailService.send(targetUser.getEmail(), AppConstants.RESET_PASSWORD, emailVerifyMail);
        } catch (UserNotFoundException ignored) {}
    }

    @Override
    public Users resetPassword(String token, ResetPasswordDto resetPasswordDto) {
        String targetUserEmail = jwtTokenService.getSubjectFromToken(token);
        Users targetUser = getUserByEmail(targetUserEmail);
        targetUser.setPassword(passwordEncoder.encode(resetPasswordDto.getPassword()));
        return userRepository.save(targetUser);
    }

    @Override
    public void deleteUserAccount() {
        Users authUser = getAuthenticatedUser();
        String profilePhoto = getPhotoNameFromPhotoUrl(authUser.getProfilePhoto());
        // delete user profile picture from filesystem if exists
        if (profilePhoto != null && profilePhoto.length() > 0) {
            String uploadDir = environment.getProperty("upload.user.images");
            try {
                fileUploadUtil.deleteFile(uploadDir, profilePhoto);
            } catch (IOException e) {
                throw new RuntimeException();
            }
        }
        userRepository.deleteByEmail(authUser.getEmail());
    }

    @Override
    public void followUser(Long userId) {
        Users authUser = getAuthenticatedUser();
        if (!authUser.getId().equals(userId)) {
            Users userToFollow = getUserById(userId);
            authUser.getFollowingUsers().add(userToFollow);
            authUser.setFollowingCount(authUser.getFollowingCount() + 1);
            userToFollow.getFollowerUsers().add(authUser);
            userToFollow.setFollowerCount(userToFollow.getFollowerCount() + 1);
            userRepository.save(userToFollow);
            userRepository.save(authUser);
        } else {
            throw new InvalidOperationException();
        }
    }

    @Override
    public void unfollowUser(Long userId) {
        Users authUser = getAuthenticatedUser();
        if (!authUser.getId().equals(userId)) {
            Users userToUnfollow = getUserById(userId);
            authUser.getFollowingUsers().remove(userToUnfollow);
            authUser.setFollowingCount(authUser.getFollowingCount() - 1);
            userToUnfollow.getFollowerUsers().remove(authUser);
            userToUnfollow.setFollowerCount(userToUnfollow.getFollowerCount() - 1);
            userRepository.save(userToUnfollow);
            userRepository.save(authUser);
        } else {
            throw new InvalidOperationException();
        }
    }

    @Override
    public List<UserResponse> getUserSearchResult(String key, Integer page, Integer size) {
        if (key.length() < 3) throw new InvalidOperationException();

        return userRepository.findUsersByName(
                key,
                PageRequest.of(page, size)
        ).stream().map(this::userToUserResponse).collect(Collectors.toList());
    }

    @Override
    public List<Users> getLikesByPostPaginate(Post post, Integer page, Integer size) {
        return userRepository.findUsersByLikedPosts(
                post,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "firstName", "lastName"))
        );
    }

    @Override
    public List<Users> getLikesByCommentPaginate(Comment comment, Integer page, Integer size) {
        return userRepository.findUsersByLikedComments(
                comment,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "firstName", "lastName"))
        );
    }

    public final Users getAuthenticatedUser() {
        String authUserEmail = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        return getUserByEmail(authUserEmail);
    }

    private String getPhotoNameFromPhotoUrl(String photoUrl) {
        if (photoUrl != null) {
            String stringToOmit = environment.getProperty("app.root.backend") + File.separator
                    + environment.getProperty("upload.user.images") + File.separator;
            return photoUrl.substring(stringToOmit.length());
        } else {
            return null;
        }
    }

    private UserResponse userToUserResponse(Users user) {
        Users authUser = getAuthenticatedUser();
        return UserResponse.builder()
                .user(user)
                .followedByAuthUser(user.getFollowerUsers().contains(authUser))
                .build();
    }
}
