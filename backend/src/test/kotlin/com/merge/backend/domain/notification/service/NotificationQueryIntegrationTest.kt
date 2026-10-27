package com.merge.backend.domain.notification.service

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
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.hibernate.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** 실제 MySQL 목록 Query가 모든 현재 소속의 내 알림을 반환하는지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class NotificationQueryIntegrationTest {
    @Autowired private lateinit var notificationService: NotificationService
    @Autowired private lateinit var notificationRepository: NotificationRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var workplaceRepository: WorkplaceRepository
    @Autowired private lateinit var memberRepository: WorkplaceMemberRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var testClock: TestClock
    @PersistenceContext private lateinit var entityManager: EntityManager

    @BeforeEach
    fun resetClock() {
        testClock.setInstant(BASE_INSTANT)
    }

    @Test
    @DisplayName("현재 소속된 모든 근무지의 내 알림만 반환하고 각 근무지 정보가 일치한다")
    fun getNotificationsReturnsOwnNotificationsAcrossAllActiveWorkplaces() {
        val fixture = fixture()
        TransactionTemplate(transactionManager).executeWithoutResult {
            assertThat(entityManager.createQuery(
                "select n.id from Notification n where n.recipientMember.id in :ids",
                Long::class.javaObjectType,
            ).setParameter("ids", fixture.memberIds).resultList)
                .containsExactlyInAnyOrderElementsOf(fixture.allNotificationIds)
            entityManager.unwrap(Session::class.java).doWork { connection ->
                val metadata = connection.metaData
                assertThat(metadata.databaseProductName).isEqualTo("MySQL")
                assertThat(metadata.databaseProductVersion).startsWith("9.7.2")
                println("J4 DB=${metadata.databaseProductName}, version=${metadata.databaseProductVersion}, isolation=${connection.transactionIsolation}")
            }
        }

        val result = notificationService.getNotifications(fixture.userId)

        assertThat(result.map { it.notificationId })
            .containsExactlyInAnyOrder(fixture.axId, fixture.ayId)
        assertThat(result.map { tuple(it.notificationId, it.workplaceId, it.workplaceName) })
            .containsExactlyInAnyOrder(
                tuple(fixture.axId, fixture.xId, fixture.xName),
                tuple(fixture.ayId, fixture.yId, fixture.yName),
            )
        println("J4 committed fixture=5, result=AX+AY, excluded=AZ+BX+BW")
    }

    private fun fixture(): Fixture =
        checkNotNull(TransactionTemplate(transactionManager).execute {
            val suffix = UUID.randomUUID().toString().replace("-", "")
            val a = userRepository.save(User("j4-a-${suffix}@test.com", "pw", "A"))
            val b = userRepository.save(User("j4-b-${suffix}@test.com", "pw", "B"))
            val x = workplace("X", suffix)
            val y = workplace("Y", suffix)
            val z = workplace("Z", suffix)
            val w = workplace("W", suffix)
            val ax = member(a, x, null)
            val ay = member(a, y, null)
            val az = member(a, z, LocalDateTime.of(2026, 9, 10, 18, 0))
            val bx = member(b, x, null)
            val bw = member(b, w, null)
            val nAx = notification(ax)
            val nAy = notification(ay)
            val nAz = notification(az)
            val nBx = notification(bx)
            val nBw = notification(bw)
            Fixture(
                checkNotNull(a.id),
                listOf(checkNotNull(ax.id), checkNotNull(ay.id), checkNotNull(az.id),
                    checkNotNull(bx.id), checkNotNull(bw.id)),
                listOf(checkNotNull(nAx.id), checkNotNull(nAy.id), checkNotNull(nAz.id),
                    checkNotNull(nBx.id), checkNotNull(nBw.id)),
                checkNotNull(nAx.id), checkNotNull(nAy.id),
                checkNotNull(x.id), x.name,
                checkNotNull(y.id), y.name,
            )
        })

    private fun workplace(name: String, suffix: String): Workplace =
        workplaceRepository.save(Workplace("J4 " + name + suffix, name + suffix))

    private fun member(user: User, workplace: Workplace, leftAt: LocalDateTime?): WorkplaceMember =
        memberRepository.save(WorkplaceMember(
            workplace, user, WorkplaceRole.EMPLOYEE,
            LocalDateTime.of(2026, 9, 1, 9, 0), leftAt,
        ))

    private fun notification(member: WorkplaceMember): Notification =
        notificationRepository.save(Notification(
            member, NotificationType.SCHEDULE_PUBLISHED, "J4 목록 조회 Fixture",
        ))

    private data class Fixture(
        val userId: Long,
        val memberIds: List<Long>,
        val allNotificationIds: List<Long>,
        val axId: Long,
        val ayId: Long,
        val xId: Long,
        val xName: String,
        val yId: Long,
        val yName: String,
    )

    private companion object {
        private val BASE_INSTANT = Instant.parse("2026-09-15T01:00:00Z")
    }
}
