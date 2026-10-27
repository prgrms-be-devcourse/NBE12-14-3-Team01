package com.merge.backend.domain.notification.repository

import com.merge.backend.domain.notification.entity.Notification
import jakarta.persistence.LockModeType
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NotificationRepository : JpaRepository<Notification, Long> {

    @Query(
        "select n\n" +
            "from Notification n\n" +
            "join fetch n.recipientMember rm\n" +
            "join fetch rm.workplace w\n" +
            "where rm.user.id = :userId and rm.leftAt is null\n" +
            "order by n.createDate desc, n.id desc\n"
    )
    fun findAllByUserId(@Param("userId") userId: Long?): List<Notification>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select n\n" +
            "from Notification n\n" +
            "where n.id = :notificationId\n"
    )
    fun findByIdForUpdate(
        @Param("notificationId") notificationId: Long?
    ): Optional<Notification>
}
