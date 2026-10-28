package com.merge.backend.domain.notification.service

import com.merge.backend.domain.notification.dto.NotificationReadResponse
import com.merge.backend.domain.notification.entity.Notification
import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.notification.repository.NotificationRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class NotificationReadConcurrencyTest {
    @Autowired private lateinit var notificationService: NotificationService
    @Autowired private lateinit var notificationRepository: NotificationRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var workplaceRepository: WorkplaceRepository
    @Autowired private lateinit var workplaceMemberRepository: WorkplaceMemberRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var testClock: TestClock

    @Test
    @DisplayName("같은 알림을 동시에 읽음 처리해도 최초 readAt을 유지한다")
    fun concurrentReadPreservesFirstReadAt() {
        val fixture = createFixture("same-notification")
        testClock.setInstant(Instant.parse("2026-09-15T01:00:00Z"))
        val executor = Executors.newFixedThreadPool(2)
        val firstMarked = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)

        try {
            // Keep the outer Transaction open after markAsRead returns, holding PESSIMISTIC_WRITE.
            val firstFuture = executor.submit(Callable<NotificationReadResponse> {
                checkNotNull(TransactionTemplate(transactionManager).execute {
                    val result = notificationService.markAsRead(fixture.userId, fixture.notificationId)
                    firstMarked.countDown()
                    try {
                        releaseFirst.await()
                    } catch (exception: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw RuntimeException(exception)
                    }
                    result
                })
            })
            assertThat(firstMarked.await(3, TimeUnit.SECONDS)).isTrue()
            testClock.setInstant(Instant.parse("2026-09-15T01:05:00Z"))
            val secondFuture = executor.submit(Callable<NotificationReadResponse> {
                secondStarted.countDown()
                notificationService.markAsRead(fixture.userId, fixture.notificationId)
            })
            assertThat(secondStarted.await(3, TimeUnit.SECONDS)).isTrue()
            assertThatThrownBy { secondFuture.get(500, TimeUnit.MILLISECONDS) }
                .isInstanceOf(TimeoutException::class.java)

            releaseFirst.countDown()
            val firstResult = firstFuture.get(5, TimeUnit.SECONDS)
            val secondResult = secondFuture.get(5, TimeUnit.SECONDS)
            val expectedReadAt = LocalDateTime.of(2026, 9, 15, 10, 0)
            assertThat(firstResult.readAt).isEqualTo(expectedReadAt)
            assertThat(secondResult.readAt).isEqualTo(expectedReadAt)
            assertThat(firstResult.notificationId).isEqualTo(fixture.notificationId)
            assertThat(secondResult.notificationId).isEqualTo(fixture.notificationId)
            val finalReadAt = TransactionTemplate(transactionManager).execute {
                notificationRepository.findById(fixture.notificationId).orElseThrow().readAt
            }
            assertThat(finalReadAt).isEqualTo(expectedReadAt)
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    private fun createFixture(suffix: String): Fixture =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            val user = userRepository.save(User(
                "notification-read-${suffix}@test.com", "password-hash", "notification-user",
            ))
            val workplace = workplaceRepository.save(Workplace(
                "알림 읽음 동시성 테스트 매장 " + suffix, "NOT-READ-" + suffix,
            ))
            val joinedAt = LocalDateTime.of(2026, 9, 1, 9, 0)
            val member = workplaceMemberRepository.save(WorkplaceMember(
                workplace, user, WorkplaceRole.EMPLOYEE, joinedAt,
            ))
            val notification = notificationRepository.save(Notification(
                member, NotificationType.SCHEDULE_PUBLISHED, "알림 읽음 동시성 테스트",
            ))
            Fixture(checkNotNull(user.id), checkNotNull(notification.id))
        })

    private data class Fixture(val userId: Long, val notificationId: Long)
}
