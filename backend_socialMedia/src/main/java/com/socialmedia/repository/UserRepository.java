package com.socialmedia.repository;

import com.socialmedia.entity.Comment;
import com.socialmedia.entity.Post;
import com.socialmedia.entity.Users;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<Users, Long> {
    Optional<Users> findByEmail(String email);
    void deleteByEmail(String email);
    List<Users> findUsersByFollowerUsers(Users user, Pageable pageable);
    List<Users> findUsersByFollowingUsers(Users user, Pageable pageable);
    List<Users> findUsersByLikedPosts(Post post, Pageable pageable);
    List<Users> findUsersByLikedComments(Comment comment, Pageable pageable);

    @Query(value = "select * from users u " +
            "where concat(u.first_name, ' ', u.last_name) like %:name% " +
            "order by u.first_name asc, u.last_name asc",
           nativeQuery = true)
    List<Users> findUsersByName(String name, Pageable pageable);
}
