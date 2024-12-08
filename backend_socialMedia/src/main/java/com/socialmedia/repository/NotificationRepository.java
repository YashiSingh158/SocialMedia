package com.socialmedia.repository;

import com.socialmedia.entity.Comment;
import com.socialmedia.entity.Post;
import com.socialmedia.entity.Users;
import com.socialmedia.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByReceiverAndOwningPostAndType(Users receiver, Post owningPost, String type);
    List<Notification> findNotificationsByReceiver(Users receiver, Pageable pageable);
    List<Notification> findNotificationsByReceiverAndIsSeenIsFalse(Users receiver);
    List<Notification> findNotificationsByReceiverAndIsReadIsFalse(Users receiver);
    void deleteNotificationByOwningPost(Post owningPost);
    void deleteNotificationByOwningComment(Comment owningComment);
}
