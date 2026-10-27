package com.merge.backend.domain.substitute.repository

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.global.config.TimeConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
@Import(TimeConfig::class)
class SubstituteCandidateRepositoryTest @Autowired constructor(
    private val substituteCandidateRepository: SubstituteCandidateRepository,
    private val entityManager: TestEntityManager,
) {

    @Test
    @DisplayName("SUB-02 - 지금 응답 가능한 대타 요청만 조회한다")
    fun findReceivedRequests_Success() {

        // given
        val now = LocalDateTime.of(2026, 9, 21, 12, 0)

        val workplace = entityManager.persist(Workplace("SWITCH 카페", "TEST1234"))

        val requesterUser = entityManager.persist(User("requester@test.com", "password", "요청자"))
        val candidateUser = entityManager.persist(User("candidate@test.com", "password", "후보자"))

        val requesterMember = entityManager.persist(
            WorkplaceMember(workplace, requesterUser, WorkplaceRole.EMPLOYEE, now.minusMonths(1))
        )
        val candidateMember = entityManager.persist(
            WorkplaceMember(workplace, candidateUser, WorkplaceRole.EMPLOYEE, now.minusMonths(1))
        )

        // 공개된 공식 근무표
        val publishedSchedule = Schedule(workplace, LocalDate.of(2026, 9, 21)).apply {
            publish(now.minusDays(1))
        }
        entityManager.persist(publishedSchedule)

        // 아직 공개되지 않은 근무표
        val draftSchedule = Schedule(workplace, LocalDate.of(2026, 9, 28))
        entityManager.persist(draftSchedule)

        // 1. 모든 조건을 만족하는 정상 Candidate
        val validCandidate = createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.plusHours(2),      // 미래 근무
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.OPEN,
            candidateStatus = CandidateStatus.PENDING,
        )

        // 2. 이미 거절한 Candidate
        createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.plusHours(3),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.OPEN,
            candidateStatus = CandidateStatus.REJECTED,
        )

        // 3. 이미 닫힌 Request
        createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.plusHours(4),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.CLOSED,
            candidateStatus = CandidateStatus.PENDING,
        )

        // 4. 이미 시작 시간이 지난 Shift
        createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.minusHours(5),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.OPEN,
            candidateStatus = CandidateStatus.PENDING,
        )

        // 5. 아직 DRAFT 상태인 근무표
        createCandidate(
            schedule = draftSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.plusDays(8),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.OPEN,
            candidateStatus = CandidateStatus.PENDING,
        )

        // 6. 취소된 Shift
        createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.plusHours(6),
            shiftStatus = ShiftStatus.CANCELLED,
            requestStatus = RequestStatus.OPEN,
            candidateStatus = CandidateStatus.PENDING,
        )

        // 7. 다른 사용자에게 온 요청 → userId 조건 확인
        val otherMember = persistMember(workplace, "other@test.com", WorkplaceRole.EMPLOYEE, now)

        createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = otherMember,
            startAt = now.plusHours(7),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.OPEN,
            candidateStatus = CandidateStatus.PENDING,
        )

        // 8. 같은 사용자지만 이미 퇴사한 근무지의 요청 → leftAt 조건 확인
        val leftWorkplace = entityManager.persist(Workplace("퇴사한 매장", "LEFT1234"))

        val leftMember = entityManager.persist(
            WorkplaceMember(
                leftWorkplace,
                candidateUser,
                WorkplaceRole.EMPLOYEE,
                now.minusMonths(2),
                now.minusDays(1),
            )
        )

        val leftWorkplaceSchedule = Schedule(leftWorkplace, LocalDate.of(2026, 9, 21)).apply {
            publish(now.minusDays(1))
        }
        entityManager.persist(leftWorkplaceSchedule)

        createCandidate(
            schedule = leftWorkplaceSchedule,
            requesterMember = requesterMember,
            candidateMember = leftMember,
            startAt = now.plusHours(8),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.OPEN,
            candidateStatus = CandidateStatus.PENDING,
        )

        // 9. 시작 시각이 더 이른 정상 Candidate → 정렬(startAt ASC) 확인
        val earlierCandidate = createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.plusHours(1),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.OPEN,
            candidateStatus = CandidateStatus.PENDING,
        )

        entityManager.flush()
        entityManager.clear()

        // when
        val result = substituteCandidateRepository.findReceivedRequests(
            candidateUser.id, // 이 사용자에게 온 요청 조회
            now,              // 현재 시간 기준
        )

        // then
        assertThat(result)
            .extracting<Long> { it.id }
            .containsExactly(earlierCandidate.id, validCandidate.id)

        // 다른 사용자 입장에서는 본인 요청만 보여야 한다
        assertThat(substituteCandidateRepository.findReceivedRequests(otherMember.user.id, now))
            .extracting<Long> { it.member.id }
            .containsOnly(otherMember.id)
    }

    @Test
    @DisplayName("SUB-07 - 내가 수락한 승인 대기 요청만 조회한다")
    fun findAcceptedPendingRequests_Success() {

        // given
        val now = LocalDateTime.of(2026, 9, 23, 14, 0)

        val workplace = entityManager.persist(Workplace("SWITCH 카페", "TEST5678"))

        val requesterUser = entityManager.persist(User("requester2@test.com", "password", "요청자"))
        val candidateUser = entityManager.persist(User("candidate2@test.com", "password", "후보자"))

        val requesterMember = entityManager.persist(
            WorkplaceMember(workplace, requesterUser, WorkplaceRole.EMPLOYEE, now.minusMonths(1))
        )
        val candidateMember = entityManager.persist(
            WorkplaceMember(workplace, candidateUser, WorkplaceRole.EMPLOYEE, now.minusMonths(1))
        )

        // 공개된 근무표
        val publishedSchedule = Schedule(workplace, LocalDate.of(2026, 9, 21)).apply {
            publish(now.minusDays(1))
        }
        entityManager.persist(publishedSchedule)

        // 아직 공개되지 않은 근무표
        val draftSchedule = Schedule(workplace, LocalDate.of(2026, 9, 28))
        entityManager.persist(draftSchedule)

        // 1. 모든 조건을 만족 → 조회되어야 함
        val validCandidate = createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.plusHours(2),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.ACCEPTED,
            candidateStatus = CandidateStatus.ACCEPTED,
        )

        // 2. 아직 수락하지 않은 Candidate → 제외
        createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.plusHours(3),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.ACCEPTED,
            candidateStatus = CandidateStatus.PENDING,
        )

        // 3. 이미 최종 승인된 Request → 제외
        createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.plusHours(4),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.APPROVED,
            candidateStatus = CandidateStatus.ACCEPTED,
        )

        // 4. 종료된 Request → 제외
        createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.plusHours(5),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.CLOSED,
            candidateStatus = CandidateStatus.ACCEPTED,
        )

        // 5. 이미 시작된 Shift → 제외
        createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.minusHours(1),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.ACCEPTED,
            candidateStatus = CandidateStatus.ACCEPTED,
        )

        // 6. DRAFT 근무표 → 제외
        createCandidate(
            schedule = draftSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.plusDays(8),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.ACCEPTED,
            candidateStatus = CandidateStatus.ACCEPTED,
        )

        // 7. 취소된 Shift → 제외
        createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = now.plusHours(7),
            shiftStatus = ShiftStatus.CANCELLED,
            requestStatus = RequestStatus.ACCEPTED,
            candidateStatus = CandidateStatus.ACCEPTED,
        )

        // 8. 다른 사용자가 수락한 요청 → userId 조건 확인
        val otherMember = persistMember(workplace, "other2@test.com", WorkplaceRole.EMPLOYEE, now)

        createCandidate(
            schedule = publishedSchedule,
            requesterMember = requesterMember,
            candidateMember = otherMember,
            startAt = now.plusHours(8),
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.ACCEPTED,
            candidateStatus = CandidateStatus.ACCEPTED,
        )

        entityManager.flush()
        entityManager.clear()

        // when
        val result = substituteCandidateRepository.findAcceptedPendingRequests(
            candidateUser.id, // 현재 로그인 User
            now,              // 현재 시간
        )

        // then
        assertThat(result)
            .extracting<Long> { it.id }
            .containsExactly(validCandidate.id)
    }

    @Test
    @DisplayName("SUB-01/03 - 이미 수락한 활성 대타 약속과 시간이 겹치는지 확인한다")
    fun existsOverlappingAcceptedSubstitute() {

        // given
        val now = LocalDateTime.of(2026, 9, 21, 12, 0)
        val startAt = now.plusDays(1)     // 내일 12:00
        val endAt = startAt.plusHours(4)  // 내일 16:00

        val fixture = createConflictFixture(now, startAt, "overlap")

        val candidateUserId = fixture.candidateMember.user.id
        val otherUserId = fixture.otherMember.user.id

        // then - 같은 사용자 + 겹치는 시간 → 충돌
        assertThat(
            substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
                candidateUserId, startAt.plusHours(2), endAt.plusHours(2), now
            )
        ).isTrue()

        // then - 다른 사용자 → 충돌 아님 (userId 조건)
        assertThat(
            substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
                otherUserId, startAt, endAt, now
            )
        ).isFalse()

        // then - 끝나는 시각에 바로 시작(경계값) → 충돌 아님
        assertThat(
            substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
                candidateUserId, endAt, endAt.plusHours(4), now
            )
        ).isFalse()

        // then - 이미 시작된 대타 약속은 활성 약속으로 보지 않는다
        assertThat(
            substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
                candidateUserId, startAt, endAt, startAt
            )
        ).isFalse()

        // then - Request가 CLOSED 된 수락 Candidate는 활성 약속이 아니다
        assertThat(
            substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
                candidateUserId,
                fixture.closedStartAt,
                fixture.closedStartAt.plusHours(4),
                now,
            )
        ).isFalse()
    }

    @Test
    @DisplayName("SUB-04 - 승인 시 현재 Request를 제외한 다른 활성 대타 약속과의 충돌만 확인한다")
    fun existsConflictingActiveSubstitute() {

        // given
        val now = LocalDateTime.of(2026, 9, 21, 12, 0)
        val startAt = now.plusDays(1)
        val endAt = startAt.plusHours(4)

        val fixture = createConflictFixture(now, startAt, "conflict")

        val candidateUserId = fixture.candidateMember.user.id
        val otherUserId = fixture.otherMember.user.id
        val acceptedRequestId = fixture.acceptedCandidate.request.id

        // then - 승인하려는 Request 자기 자신은 충돌로 보지 않는다
        assertThat(
            substituteCandidateRepository.existsConflictingActiveSubstitute(
                candidateUserId, acceptedRequestId, startAt, endAt, now
            )
        ).isFalse()

        // then - 다른 Request 기준으로는 충돌
        assertThat(
            substituteCandidateRepository.existsConflictingActiveSubstitute(
                candidateUserId, -1L, startAt, endAt, now
            )
        ).isTrue()

        // then - 다른 사용자 → 충돌 아님 (userId 조건)
        assertThat(
            substituteCandidateRepository.existsConflictingActiveSubstitute(
                otherUserId, -1L, startAt, endAt, now
            )
        ).isFalse()
    }

    // 후보자가 수락한 활성 약속 1건 + CLOSED 된 수락 1건을 만든다
    private fun createConflictFixture(
        now: LocalDateTime,
        acceptedStartAt: LocalDateTime,
        suffix: String,
    ): ConflictFixture {
        val workplace = entityManager.persist(Workplace("충돌 테스트 매장 $suffix", "CF-$suffix"))

        val requesterMember = persistMember(workplace, "requester-$suffix@test.com", WorkplaceRole.EMPLOYEE, now)
        val candidateMember = persistMember(workplace, "candidate-$suffix@test.com", WorkplaceRole.EMPLOYEE, now)
        val otherMember = persistMember(workplace, "other-$suffix@test.com", WorkplaceRole.EMPLOYEE, now)

        val schedule = Schedule(workplace, LocalDate.of(2026, 9, 21)).apply {
            publish(now.minusDays(1))
        }
        entityManager.persist(schedule)

        val acceptedCandidate = createCandidate(
            schedule = schedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = acceptedStartAt,
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.ACCEPTED,
            candidateStatus = CandidateStatus.ACCEPTED,
        )

        val closedStartAt = acceptedStartAt.plusDays(1)

        createCandidate(
            schedule = schedule,
            requesterMember = requesterMember,
            candidateMember = candidateMember,
            startAt = closedStartAt,
            shiftStatus = ShiftStatus.SCHEDULED,
            requestStatus = RequestStatus.CLOSED,
            candidateStatus = CandidateStatus.ACCEPTED,
        )

        entityManager.flush()

        return ConflictFixture(
            candidateMember = candidateMember,
            otherMember = otherMember,
            acceptedCandidate = acceptedCandidate,
            closedStartAt = closedStartAt,
        )
    }

    private fun persistMember(
        workplace: Workplace,
        email: String,
        role: WorkplaceRole,
        now: LocalDateTime,
    ): WorkplaceMember {
        val user = entityManager.persist(User(email, "password", email))

        return entityManager.persist(WorkplaceMember(workplace, user, role, now.minusMonths(1)))
    }

    private data class ConflictFixture(
        val candidateMember: WorkplaceMember,
        val otherMember: WorkplaceMember,
        val acceptedCandidate: SubstituteCandidate,
        val closedStartAt: LocalDateTime,
    )

    private fun createCandidate(
        schedule: Schedule,                 // 어느 근무표의 Shift인지
        requesterMember: WorkplaceMember,   // 대타 요청자
        candidateMember: WorkplaceMember,   // 대타 후보자
        startAt: LocalDateTime,             // 근무 시작 시간
        shiftStatus: ShiftStatus,           // SCHEDULED / CANCELLED
        requestStatus: RequestStatus,       // OPEN / CLOSED 등
        candidateStatus: CandidateStatus,   // PENDING / REJECTED 등
    ): SubstituteCandidate {
        val shift = entityManager.persist(
            Shift(schedule, requesterMember, startAt, startAt.plusHours(4), shiftStatus)
        )

        val request = entityManager.persist(SubstituteRequest(shift, requesterMember, requestStatus))

        // 생성자는 기본적으로 PENDING이므로,
        // REJECTED 같은 다른 상태 테스트가 필요할 때만 바꿔준다.
        val candidate = SubstituteCandidate(request, candidateMember).apply {
            ReflectionTestUtils.setField(this, "status", candidateStatus)
        }

        return entityManager.persist(candidate)
    }
}
