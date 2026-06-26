package com.rental.shortrental.messaging.repository;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.entity.GuestMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuestMessageRepository extends JpaRepository<GuestMessage, Long> {
    boolean existsByExternalMessageId(String externalMessageId);
    List<GuestMessage> findByPropertyUserIdOrderByCreatedAtDesc(Long ownerId);

    @Query("SELECT m FROM GuestMessage m JOIN m.property p WHERE p.user.id = :ownerId AND m.platform = :platform "
            + "ORDER BY m.createdAt DESC")
    List<GuestMessage> findByLandlordAndPlatform(@Param("ownerId") Long ownerId, @Param("platform") ExternalPlatform platform);

    @Query("SELECT m FROM GuestMessage m JOIN m.property p WHERE p.user.id = :ownerId "
            + "AND p.id = :propertyId AND m.platform = :platform AND m.externalConversationId = :conversationId "
            + "ORDER BY m.createdAt ASC")
    List<GuestMessage> findThread(
            @Param("ownerId") Long ownerId,
            @Param("propertyId") Long propertyId,
            @Param("platform") ExternalPlatform platform,
            @Param("conversationId") String conversationId
    );
}
