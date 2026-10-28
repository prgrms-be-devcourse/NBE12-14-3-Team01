package com.merge.backend.domain.substitute.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID

// 보낸 요청(SUB-05)·수락한 요청(SUB-07)은 현재 소속 것만 (API-CATALOG CHG-02·03, G03-D-22 §6-4). 실제 Commit 결과를 MySQL에서 확인
@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class SubstituteCurrentMembershipListTest {

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
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var workplaceRepository: WorkplaceRepository

    @Autowired
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var testClock: TestClock

    @BeforeEach
    fun fixClock() {
        // 같은 Context를 쓰는 다른 테스트가 시간을 바꿨을 수 있으므로 매번 고정
        testClock.setInstant(FIXED_NOW)
    }

    @Test
    @DisplayName("W1·W2에서 수락한 뒤 W1만 퇴사하면 수락한 요청 목록에 W2 요청만 남고 W1 Row는 그대로다")
    fun acceptedListShowsOnlyCurrentMembershipCandidates() {
        // given: 한 직원이 W1·W2 소속, 두 근무지 요청을 실제 수락 흐름으로 ACCEPTED
        val accepter = userRepository.save(User("accepter-${uniqueSuffix()}@test.com", "password-hash", "수락자"))
        val w1 = createAcceptedRequest(accepter, daysUntilShift = 7)
        val w2 = createAcceptedRequest(accepter, daysUntilShift = 8)
        assertThat(acceptedCandidateIdsOf(accepter)).containsExactly(w1.candidateId, w2.candidateId)

        // W1 소속 종료 (퇴사 Command가 없어 leftAt 직접 기록, ML-3 요청 종료 없이 방어 조건만 확인)
        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now(), w1.accepterMemberId)

        // when & then: 현재 소속인 W2 요청만
        assertThat(acceptedCandidateIdsOf(accepter)).containsExactly(w2.candidateId)

        // then: W1 Request·Candidate Row는 지우거나 바꾸지 않는다
        TransactionTemplate(transactionManager).executeWithoutResult {
            val candidate = substituteCandidateRepository.findById(w1.candidateId).orElseThrow()
            assertThat(candidate.status).isEqualTo(CandidateStatus.ACCEPTED)
            assertThat(candidate.member.id).isEqualTo(w1.accepterMemberId)
            assertThat(substituteRequestRepository.findById(w1.requestId).orElseThrow().status)
                .isEqualTo(RequestStatus.ACCEPTED)
        }
    }

    @Test
    @DisplayName("요청을 보낸 뒤 퇴사하고 같은 근무지에 재가입하면 보낸 요청 목록에 이전 소속 기간 요청은 없고 새 소속 요청만 보인다")
    fun sentListShowsOnlyCurrentMembershipRequests() {
        // given: 이전 소속으로 요청을 보냄
        val suffix = uniqueSuffix()
        val old = requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val workplace = workplaceRepository.save(Workplace("현재 소속 조회 테스트 매장 $suffix", "CMS-$suffix"))
                val requester = saveMember(workplace, "requester-$suffix")
                val request = saveRequest(requester, shiftStartAt(daysUntilShift = 7))

                SentFixture(
                    requesterUserId = requireNotNull(requester.user.id),
                    requesterMemberId = requireNotNull(requester.id),
                    requestId = requireNotNull(request.id),
                )
            }
        )
        assertThat(sentRequestIdsOf(old.requesterUserId)).containsExactly(old.requestId)

        // 소속 종료 (퇴사 Command가 없어 leftAt 직접 기록)
        jdbcTemplate.update("UPDATE workplace_member SET left_at = ? WHERE id = ?", now(), old.requesterMemberId)
        assertThat(sentRequestIdsOf(old.requesterUserId)).isEmpty()

        // 같은 근무지 재가입 → 새 소속으로 요청을 보냄
        val currentRequestId = requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val oldMember = workplaceMemberRepository.findById(old.requesterMemberId).orElseThrow()
                val rejoined = workplaceMemberRepository.save(
                    WorkplaceMember(oldMember.workplace, oldMember.user, WorkplaceRole.EMPLOYEE, now())
                )
                saveRequest(rejoined, shiftStartAt(daysUntilShift = 8)).id
            }
        )

        // when & then: 새 소속 요청만
        assertThat(sentRequestIdsOf(old.requesterUserId)).containsExactly(currentRequestId)

        // then: 이전 소속 요청 Row는 그대로 이전 소속을 가리킨다
        TransactionTemplate(transactionManager).executeWithoutResult {
            assertThat(substituteRequestRepository.findById(old.requestId).orElseThrow().requesterMember.id)
                .isEqualTo(old.requesterMemberId)
        }
    }

    // ===================== 실행 헬퍼 =====================

    private fun acceptedCandidateIdsOf(user: User): List<Long?> =
        substituteCandidateService.findAcceptedPendingRequests(requireNotNull(user.id)).map { it.id }

    private fun sentRequestIdsOf(userId: Long): List<Long> =
        substituteRequestService.getSentRequests(userId).map { it.requestId }

    private fun now(): LocalDateTime = LocalDateTime.now(testClock)

    private fun shiftStartAt(daysUntilShift: Long): LocalDateTime =
        now().plusDays(daysUntilShift).withHour(10).withMinute(0).withSecond(0).withNano(0)

    // ===================== Fixture =====================

    private data class AcceptedFixture(
        val accepterMemberId: Long,
        val candidateId: Long,
        val requestId: Long,
    )

    private data class SentFixture(
        val requesterUserId: Long,
        val requesterMemberId: Long,
        val requestId: Long,
    )

    // 새 근무지에 요청자 1명과 accepter의 소속을 만들고, 요청자의 근무 1개에 요청 1건. accepter가 실제 수락 흐름으로 ACCEPTED
    private fun createAcceptedRequest(accepter: User, daysUntilShift: Long): AcceptedFixture {
        val f = requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val suffix = uniqueSuffix()
                val workplace = workplaceRepository.save(Workplace("현재 소속 조회 테스트 매장 $suffix", "CMA-$suffix"))
                val requester = saveMember(workplace, "requester-$suffix")
                val accepterMember = workplaceMemberRepository.save(
                    WorkplaceMember(workplace, accepter, WorkplaceRole.EMPLOYEE, now().minusDays(30))
                )
                val request = saveRequest(requester, shiftStartAt(daysUntilShift))
                val candidate = substituteCandidateRepository.save(SubstituteCandidate(request, accepterMember))

                AcceptedFixture(
                    accepterMemberId = requireNotNull(accepterMember.id),
                    candidateId = requireNotNull(candidate.id),
                    requestId = requireNotNull(request.id),
                )
            }
        )

        substituteCandidateService.acceptCandidate(f.candidateId, requireNotNull(accepter.id))

        return f
    }

    // requester의 공개 근무 1개와 OPEN 요청 1건
    private fun saveRequest(requester: WorkplaceMember, startAt: LocalDateTime): SubstituteRequest {
        val schedule = savePublishedSchedule(requester.workplace, startAt)
        val shift = shiftRepository.save(Shift(schedule, requester, startAt, startAt.plusHours(4), ShiftStatus.SCHEDULED))

        return substituteRequestRepository.save(SubstituteRequest(shift, requester, RequestStatus.OPEN))
    }

    private fun savePublishedSchedule(workplace: Workplace, startAt: LocalDateTime): Schedule {
        val weekStart = startAt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        // 같은 근무지·같은 주 근무표는 하나뿐 (Unique)
        return scheduleRepository.findByWorkplace_IdAndWeekStartDate(workplace.id, weekStart).orElse(null)
            ?: scheduleRepository.save(Schedule(workplace, weekStart).also { it.publish(now()) })
    }

    private fun saveMember(workplace: Workplace, name: String): WorkplaceMember {
        val user = userRepository.save(User("$name@test.com", "password-hash", name))

        return workplaceMemberRepository.save(WorkplaceMember(workplace, user, WorkplaceRole.EMPLOYEE, now().minusDays(30)))
    }

    companion object {
        // 2026-09-15 10:00 (KST)
        private val FIXED_NOW: Instant = Instant.parse("2026-09-15T01:00:00Z")

        private fun uniqueSuffix(): String = UUID.randomUUID().toString().substring(0, 8)
    }
}
