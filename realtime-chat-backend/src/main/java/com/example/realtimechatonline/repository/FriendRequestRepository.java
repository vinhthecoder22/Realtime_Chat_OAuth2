package com.example.realtimechatonline.repository;

import com.example.realtimechatonline.domain.entity.FriendRequest;
import com.example.realtimechatonline.domain.entity.FriendRequestStatus;
import com.example.realtimechatonline.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    Optional<FriendRequest> findByUser1AndUser2(User user1, User user2);

    @Query("SELECT f FROM FriendRequest f WHERE (f.user1 = :user OR f.user2 = :user) AND f.sender <> :user AND f.status = :status")
    Page<FriendRequest> findReceivedRequests(@Param("user") User user, @Param("status") FriendRequestStatus status, Pageable pageable);

    @Query("SELECT f FROM FriendRequest f WHERE f.sender = :sender AND f.status = :status")
    Page<FriendRequest> findBySenderAndStatus(@Param("sender") User sender, @Param("status") FriendRequestStatus status, Pageable pageable);

    @Query("SELECT f FROM FriendRequest f WHERE (f.user1 = :user OR f.user2 = :user) AND f.status = 'ACCEPTED'")
    Page<FriendRequest> findFriends(@Param("user") User user, Pageable pageable);
}
