package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.global.exception.BusinessException
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** 실행은 사람이 수행한다. V3/V4를 대신하지 않고 V2 쓰기의 Transaction 참여만 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class LeaveMembershipWriterIntegrationTest {
    @Autowired private lateinit var writer: LeaveMembershipWriter
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    private lateinit var transaction: TransactionTemplate
    private var workplaceId = 0L
    private var memberId = 0L
    private var userId = 0L
    private val joinedAt = LocalDateTime.of(2026, 10, 1, 9, 0)
    private val endedAt = joinedAt.plusDays(28)

    @BeforeEach
    fun setUp() {
        transaction = TransactionTemplate(transactionManager)
        transaction.executeWithoutResult {
            val user = User("leave-writer-${java.util.UUID.randomUUID()}@test.com", "pw", "직원")
            val workplace = Workplace("퇴사 쓰기 테스트", "A1B2C3D4")
            entityManager.persist(user)
            entityManager.persist(workplace)
            val member = WorkplaceMember(workplace, user, WorkplaceRole.EMPLOYEE, joinedAt)
            entityManager.persist(member)
            entityManager.flush()
            userId = requireNotNull(user.id)
            workplaceId = requireNotNull(workplace.id)
            memberId = requireNotNull(member.id)
        }
    }

    @AfterEach
    fun cleanUp() {
        transaction.executeWithoutResult {
            entityManager.find(WorkplaceMember::class.java, memberId)?.let(entityManager::remove)
            entityManager.flush()
            entityManager.find(Workplace::class.java, workplaceId)?.let(entityManager::remove)
            entityManager.find(User::class.java, userId)?.let(entityManager::remove)
        }
    }

    @Test
    fun endsMembershipAndRotatesInviteCodeInsideCallerTransaction() {
        transaction.executeWithoutResult {
            entityManager.find(Workplace::class.java, workplaceId, LockModeType.PESSIMISTIC_WRITE)
            val member = entityManager.find(WorkplaceMember::class.java, memberId, LockModeType.PESSIMISTIC_WRITE)
            val result = writer.complete(member, endedAt, true)
            assertThat(result.leftAt).isEqualTo(endedAt)
            assertThat(result.newInviteCode).matches("[0-9A-F]{8}").isNotEqualTo("A1B2C3D4")
        }
        transaction.executeWithoutResult {
            assertThat(entityManager.find(WorkplaceMember::class.java, memberId).leftAt).isEqualTo(endedAt)
            assertThat(entityManager.find(Workplace::class.java, workplaceId).inviteCode).isNotEqualTo("A1B2C3D4")
        }
    }

    @Test
    fun callerFailureRollsBackMembershipAndInviteCodeTogether() {
        val failure = assertThrows<IllegalStateException> {
            transaction.executeWithoutResult {
                entityManager.find(Workplace::class.java, workplaceId, LockModeType.PESSIMISTIC_WRITE)
                val member = entityManager.find(WorkplaceMember::class.java, memberId, LockModeType.PESSIMISTIC_WRITE)
                val result = writer.complete(member, endedAt, true)
                assertThat(result.newInviteCode).isNotNull()
                entityManager.flush()
                error("injected failure after V2 writes")
            }
        }
        assertThat(failure).hasMessage("injected failure after V2 writes")
        transaction.executeWithoutResult {
            assertThat(entityManager.find(WorkplaceMember::class.java, memberId).leftAt).isNull()
            assertThat(entityManager.find(Workplace::class.java, workplaceId).inviteCode).isEqualTo("A1B2C3D4")
        }
    }

    @Test
    fun requiresCallerTransactionAndExistingLocksBeforeAnyMutation() {
        val detached = WorkplaceMember(Workplace("매장", "A1B2C3D4"), User("none@test.com", "pw", "직원"),
            WorkplaceRole.EMPLOYEE, joinedAt)
        assertThrows<IllegalTransactionStateException> { writer.complete(detached, endedAt, false) }
        assertThrows<IllegalStateException> {
            transaction.executeWithoutResult {
                val member = entityManager.find(WorkplaceMember::class.java, memberId)
                writer.complete(member, endedAt, false)
            }
        }
        assertThrows<IllegalStateException> {
            transaction.executeWithoutResult {
                val member = entityManager.find(WorkplaceMember::class.java, memberId, LockModeType.PESSIMISTIC_WRITE)
                writer.complete(member, endedAt, true) // Workplace Lock이 빠진 경우
            }
        }
        transaction.executeWithoutResult {
            assertThat(entityManager.find(WorkplaceMember::class.java, memberId).leftAt).isNull()
            assertThat(entityManager.find(Workplace::class.java, workplaceId).inviteCode).isEqualTo("A1B2C3D4")
        }
    }

    @Test
    fun keepsInviteCodeWhenNotRequestedAndRejectsRepeatedCompletion() {
        transaction.executeWithoutResult {
            val member = entityManager.find(WorkplaceMember::class.java, memberId, LockModeType.PESSIMISTIC_WRITE)
            assertThat(writer.complete(member, endedAt, false).newInviteCode).isNull()
        }
        val failure = assertThrows<BusinessException> {
            transaction.executeWithoutResult {
                val member = entityManager.find(WorkplaceMember::class.java, memberId, LockModeType.PESSIMISTIC_WRITE)
                writer.complete(member, endedAt.plusDays(1), false)
            }
        }
        assertThat(failure.errorCode).isEqualTo(WorkplaceErrorCode.MEMBERSHIP_ALREADY_ENDED)
        transaction.executeWithoutResult {
            assertThat(entityManager.find(WorkplaceMember::class.java, memberId).leftAt).isEqualTo(endedAt)
            assertThat(entityManager.find(Workplace::class.java, workplaceId).inviteCode).isEqualTo("A1B2C3D4")
        }
    }
}
