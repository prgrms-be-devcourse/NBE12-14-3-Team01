package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.repository.SubstituteRequestRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ThrowingConsumer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID

// 만료는 조회 응답에서만 계산하고 DB는 바꾸지 않는다 (G03-D-19 D19-1·D19-2). 실제 Commit 결과를 MySQL에서 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class SentSubstituteRequestExpiryIntegrationTest {

    @Autowired
    private lateinit var substituteRequestService: SubstituteRequestService

    @Autowired
    private lateinit var substituteRequestRepository: SubstituteRequestRepository

    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var workplaceRepository: WorkplaceRepository

    @Autowired
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var testClock: TestClock

    @BeforeEach
    fun fixClock() {
        // 같은 Context를 쓰는 다른 테스트가 시간을 바꿨을 수 있으므로 매번 고정
        testClock.setInstant(FIXED_NOW)
    }

    @Test
    @DisplayName("만료된 요청을 목록 조회하면 CLOSED/EXPIRED·closedAt = 근무 시작 시각이지만 DB Row는 그대로다")
    fun listingExpiredRequestsKeepsDbRows() {
        // given: 근무 시작 전에 만든 OPEN·ACCEPTED 요청
        val f = createFixture()

        // when: 근무 시작 시각이 지난 뒤 보낸 요청 목록 조회
        testClock.setInstant(FIXED_NOW.plusSeconds(8 * 86_400))
        val responses = substituteRequestService.getSentRequests(f.requesterUserId)

        // then: 응답은 만료, 종료 시각은 근무 시작 시각
        assertThat(responses.map { it.requestId }).containsExactlyInAnyOrder(f.openRequestId, f.acceptedRequestId)
        assertThat(responses).allSatisfy(ThrowingConsumer { response ->
            assertThat(response.status).isEqualTo(RequestStatus.CLOSED)
            assertThat(response.closeReason).isEqualTo(RequestCloseReason.EXPIRED)
            assertThat(response.closedAt).isEqualTo(response.startAt)
        })

        // then: DB는 조회 전 상태 그대로
        TransactionTemplate(transactionManager).executeWithoutResult {
            val open = substituteRequestRepository.findById(f.openRequestId).orElseThrow()
            val accepted = substituteRequestRepository.findById(f.acceptedRequestId).orElseThrow()

            assertThat(open.status).isEqualTo(RequestStatus.OPEN)
            assertThat(accepted.status).isEqualTo(RequestStatus.ACCEPTED)
            assertThat(listOf(open, accepted)).allSatisfy(ThrowingConsumer { request ->
                assertThat(request.closeReason).isNull()
                assertThat(request.closedAt).isNull()
            })
        }
    }

    // ===================== Fixture =====================

    private data class Fixture(
        val requesterUserId: Long,
        val openRequestId: Long,
        val acceptedRequestId: Long,
    )

    // 요청자 1명의 공개 근무 2개, 각각 OPEN·ACCEPTED Request
    private fun createFixture(): Fixture = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val suffix = uniqueSuffix()
            val workplace = workplaceRepository.save(Workplace("만료 조회 테스트 매장 $suffix", "EXP-$suffix"))
            val schedule = savePublishedSchedule(workplace)
            val requester = saveMember(workplace, "requester-$suffix")

            val openShift = saveShift(schedule, requester, shiftStartAt())
            val acceptedShift = saveShift(schedule, requester, shiftStartAt().plusHours(5))

            val openRequest = substituteRequestRepository.save(SubstituteRequest(openShift, requester, RequestStatus.OPEN))
            val acceptedRequest = substituteRequestRepository.save(
                SubstituteRequest(acceptedShift, requester, RequestStatus.ACCEPTED)
            )

            Fixture(
                requesterUserId = requireNotNull(requester.user.id),
                openRequestId = requireNotNull(openRequest.id),
                acceptedRequestId = requireNotNull(acceptedRequest.id),
            )
        }
    )

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    // 일주일 뒤 10:00
    private fun shiftStartAt(): LocalDateTime =
        now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0)

    private fun savePublishedSchedule(workplace: Workplace): Schedule {
        val schedule = Schedule(
            workplace,
            shiftStartAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
        )
        schedule.publish(now())
        return scheduleRepository.save(schedule)
    }

    private fun saveMember(workplace: Workplace, name: String): WorkplaceMember {
        val user = userRepository.save(User("$name@test.com", "password-hash", name))

        return workplaceMemberRepository.save(WorkplaceMember(workplace, user, WorkplaceRole.EMPLOYEE, now().minusDays(30)))
    }

    private fun saveShift(schedule: Schedule, member: WorkplaceMember, startAt: LocalDateTime): Shift =
        shiftRepository.save(Shift(schedule, member, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))

    companion object {
        // 2026-09-15 10:00 (KST)
        private val FIXED_NOW: Instant = Instant.parse("2026-09-15T01:00:00Z")

        private fun uniqueSuffix(): String = UUID.randomUUID().toString().substring(0, 8)
    }
}
