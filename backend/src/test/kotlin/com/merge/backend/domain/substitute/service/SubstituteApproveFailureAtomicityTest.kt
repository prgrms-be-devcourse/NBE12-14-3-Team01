package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.entity.UnavailableTime
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode
import com.merge.backend.domain.substitute.repository.SubstituteCandidateRepository
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
import com.merge.backend.global.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

// 대타 승인이 실패하면 Request·Candidate·Shift 어느 것도 바뀌지 않는다. 실제 Commit 결과를 MySQL에서 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class SubstituteApproveFailureAtomicityTest {

    @Autowired
    private lateinit var substituteRequestService: SubstituteRequestService

    @Autowired
    private lateinit var substituteCandidateService: SubstituteCandidateService

    @Autowired
    private lateinit var substituteRequestRepository: SubstituteRequestRepository

    @Autowired
    private lateinit var substituteCandidateRepository: SubstituteCandidateRepository

    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository

    @Autowired
    private lateinit var unavailableTimeRepository: UnavailableTimeRepository

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
    @DisplayName("승인이 수락자의 근무 불가 시간 검사에서 실패하면 요청·후보·근무 담당자가 모두 그대로다")
    fun approve_FailsAtUnavailableTimeCheck_KeepsRequestCandidatesAndShift() {
        // given: 후보 B가 수락하고 다른 후보 C는 대기 중, 그 뒤 B가 근무 시간과 겹치는 불가능 시간을 등록
        val f = createFixture()
        val acceptedAt = now()

        // when
        assertThatThrownBy { substituteRequestService.approve(f.requestId, f.managerUserId) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(SubstituteRequestErrorCode.CONFLICT_UNAVAILABLE_TIME)

        // then: 새 Transaction에서 다시 읽는다
        TransactionTemplate(transactionManager).executeWithoutResult {
            val request = substituteRequestRepository.findById(f.requestId).orElseThrow()
            assertThat(request.status).isEqualTo(RequestStatus.ACCEPTED)
            assertThat(request.approvedAt).isNull()
            assertThat(request.closedAt).isNull()
            assertThat(request.closeReason).isNull()

            val accepted = substituteCandidateRepository.findById(f.acceptedCandidateId).orElseThrow()
            assertThat(accepted.status).isEqualTo(CandidateStatus.ACCEPTED)
            assertThat(accepted.respondedAt).isEqualTo(acceptedAt)

            val pending = substituteCandidateRepository.findById(f.pendingCandidateId).orElseThrow()
            assertThat(pending.status).isEqualTo(CandidateStatus.PENDING)
            assertThat(pending.respondedAt).isNull()

            assertThat(shiftRepository.findById(f.shiftId).orElseThrow().member.id).isEqualTo(f.requesterMemberId)
        }
    }

    // ===================== Fixture =====================

    private data class Fixture(
        val managerUserId: Long,
        val requesterMemberId: Long,
        val shiftId: Long,
        val requestId: Long,
        val acceptedCandidateId: Long,
        val pendingCandidateId: Long,
    )

    private fun createFixture(): Fixture {
        val (fixture, acceptedUserId) = requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val suffix = uniqueSuffix()
                val workplace = workplaceRepository.save(Workplace("대타 승인 실패 테스트 매장 $suffix", "APF-$suffix"))
                val schedule = savePublishedSchedule(workplace)

                val manager = saveMember(workplace, "manager-$suffix", WorkplaceRole.MANAGER)
                val requester = saveMember(workplace, "requester-$suffix", WorkplaceRole.EMPLOYEE)
                val acceptedMember = saveMember(workplace, "accepted-$suffix", WorkplaceRole.EMPLOYEE)
                val pendingMember = saveMember(workplace, "pending-$suffix", WorkplaceRole.EMPLOYEE)

                val shift = shiftRepository.save(
                    Shift(schedule, requester, shiftStartAt(), shiftStartAt().plusHours(4), ShiftStatus.SCHEDULED)
                )
                val request = substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN))
                val acceptedCandidate = substituteCandidateRepository.save(SubstituteCandidate(request, acceptedMember))
                val pendingCandidate = substituteCandidateRepository.save(SubstituteCandidate(request, pendingMember))

                Pair(
                    Fixture(
                        managerUserId = requireNotNull(manager.user.id),
                        requesterMemberId = requireNotNull(requester.id),
                        shiftId = requireNotNull(shift.id),
                        requestId = requireNotNull(request.id),
                        acceptedCandidateId = requireNotNull(acceptedCandidate.id),
                        pendingCandidateId = requireNotNull(pendingCandidate.id),
                    ),
                    requireNotNull(acceptedMember.user.id),
                )
            }
        )

        // 실제 수락 흐름으로 ACCEPTED를 만든다
        substituteCandidateService.acceptCandidate(fixture.acceptedCandidateId, acceptedUserId)

        // 수락 뒤 등록한 불가능 시간 (근무 시작 1시간 뒤부터 겹침)
        TransactionTemplate(transactionManager).executeWithoutResult {
            unavailableTimeRepository.save(
                UnavailableTime(
                    userRepository.findById(acceptedUserId).orElseThrow(),
                    shiftStartAt().plusHours(1),
                    shiftStartAt().plusHours(5),
                )
            )
        }

        return fixture
    }

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

    private fun saveMember(workplace: Workplace, name: String, role: WorkplaceRole): WorkplaceMember {
        val user = userRepository.save(User("$name@test.com", "password-hash", name))

        return workplaceMemberRepository.save(WorkplaceMember(workplace, user, role, now().minusDays(30)))
    }

    companion object {
        // 2026-09-15 10:00 (KST)
        private val FIXED_NOW: Instant = Instant.parse("2026-09-15T01:00:00Z")

        private fun uniqueSuffix(): String = UUID.randomUUID().toString().substring(0, 8)
    }
}
