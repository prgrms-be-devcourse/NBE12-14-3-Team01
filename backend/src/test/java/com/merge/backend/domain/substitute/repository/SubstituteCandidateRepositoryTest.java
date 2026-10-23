package com.merge.backend.domain.substitute.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.merge.backend.domain.shift.entity.Schedule;
import com.merge.backend.domain.shift.entity.ScheduleStatus;
import com.merge.backend.domain.shift.entity.Shift;
import com.merge.backend.domain.shift.entity.ShiftStatus;
import com.merge.backend.domain.substitute.entity.CandidateStatus;
import com.merge.backend.domain.substitute.entity.RequestStatus;
import com.merge.backend.domain.substitute.entity.SubstituteCandidate;
import com.merge.backend.domain.substitute.entity.SubstituteRequest;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.workplace.entity.Workplace;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.entity.WorkplaceRole;
import com.merge.backend.global.config.TimeConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import(TimeConfig.class)
class SubstituteCandidateRepositoryTest {

    private final SubstituteCandidateRepository substituteCandidateRepository;
    private final TestEntityManager entityManager;

    @Autowired
    SubstituteCandidateRepositoryTest(
        SubstituteCandidateRepository substituteCandidateRepository,
        TestEntityManager entityManager
    ) {
        this.substituteCandidateRepository = substituteCandidateRepository;
        this.entityManager = entityManager;
    }

    @Test
    @DisplayName("SUB-02 - 지금 응답 가능한 대타 요청만 조회한다")
    void findReceivedRequests_Success() {

        // given
        LocalDateTime now = LocalDateTime.of(2026, 9, 21, 12, 0);

        Workplace workplace = entityManager.persist(
            new Workplace("SWITCH 카페", "TEST1234")
        );

        User requesterUser = entityManager.persist(
            new User("requester@test.com", "password", "요청자")
        );

        User candidateUser = entityManager.persist(
            new User("candidate@test.com", "password", "후보자")
        );

        WorkplaceMember requesterMember = entityManager.persist(
            new WorkplaceMember(
                workplace,
                requesterUser,
                WorkplaceRole.EMPLOYEE,
                now.minusMonths(1)
            )
        );

        WorkplaceMember candidateMember = entityManager.persist(
            new WorkplaceMember(
                workplace,
                candidateUser,
                WorkplaceRole.EMPLOYEE,
                now.minusMonths(1)
            )
        );

        // 공개된 공식 근무표
        Schedule publishedSchedule =
            new Schedule(workplace, LocalDate.of(2026, 9, 21));

        publishedSchedule.publish(now.minusDays(1));
        entityManager.persist(publishedSchedule);

        // 아직 공개되지 않은 근무표
        Schedule draftSchedule =
            new Schedule(workplace, LocalDate.of(2026, 9, 28));

        entityManager.persist(draftSchedule);

        // 1. 모든 조건을 만족하는 정상 Candidate
        SubstituteCandidate validCandidate = createCandidate(
            publishedSchedule,
            requesterMember,
            candidateMember,
            now.plusHours(2),      // 미래 근무
            ShiftStatus.SCHEDULED,
            RequestStatus.OPEN,
            CandidateStatus.PENDING
        );

        // 2. 이미 거절한 Candidate
        createCandidate(
            publishedSchedule,
            requesterMember,
            candidateMember,
            now.plusHours(3),
            ShiftStatus.SCHEDULED,
            RequestStatus.OPEN,
            CandidateStatus.REJECTED
        );

        // 3. 이미 닫힌 Request
        createCandidate(
            publishedSchedule,
            requesterMember,
            candidateMember,
            now.plusHours(4),
            ShiftStatus.SCHEDULED,
            RequestStatus.CLOSED,
            CandidateStatus.PENDING
        );

        // 4. 이미 시작 시간이 지난 Shift
        createCandidate(
            publishedSchedule,
            requesterMember,
            candidateMember,
            now.minusHours(5),
            ShiftStatus.SCHEDULED,
            RequestStatus.OPEN,
            CandidateStatus.PENDING
        );

        // 5. 아직 DRAFT 상태인 근무표
        createCandidate(
            draftSchedule,
            requesterMember,
            candidateMember,
            now.plusDays(8),
            ShiftStatus.SCHEDULED,
            RequestStatus.OPEN,
            CandidateStatus.PENDING
        );

        // 6. 취소된 Shift
        createCandidate(
            publishedSchedule,
            requesterMember,
            candidateMember,
            now.plusHours(6),
            ShiftStatus.CANCELLED,
            RequestStatus.OPEN,
            CandidateStatus.PENDING
        );

        // 7. 다른 사용자에게 온 요청 → userId 조건 확인
        WorkplaceMember otherMember = persistMember(
            workplace, "other@test.com", WorkplaceRole.EMPLOYEE, now
        );

        createCandidate(
            publishedSchedule,
            requesterMember,
            otherMember,
            now.plusHours(7),
            ShiftStatus.SCHEDULED,
            RequestStatus.OPEN,
            CandidateStatus.PENDING
        );

        // 8. 같은 사용자지만 이미 퇴사한 근무지의 요청 → leftAt 조건 확인
        Workplace leftWorkplace = entityManager.persist(
            new Workplace("퇴사한 매장", "LEFT1234")
        );

        WorkplaceMember leftMember = entityManager.persist(
            new WorkplaceMember(
                leftWorkplace,
                candidateUser,
                WorkplaceRole.EMPLOYEE,
                now.minusMonths(2),
                now.minusDays(1)
            )
        );

        Schedule leftWorkplaceSchedule =
            new Schedule(leftWorkplace, LocalDate.of(2026, 9, 21));

        leftWorkplaceSchedule.publish(now.minusDays(1));
        entityManager.persist(leftWorkplaceSchedule);

        createCandidate(
            leftWorkplaceSchedule,
            requesterMember,
            leftMember,
            now.plusHours(8),
            ShiftStatus.SCHEDULED,
            RequestStatus.OPEN,
            CandidateStatus.PENDING
        );

        // 9. 시작 시각이 더 이른 정상 Candidate → 정렬(startAt ASC) 확인
        SubstituteCandidate earlierCandidate = createCandidate(
            publishedSchedule,
            requesterMember,
            candidateMember,
            now.plusHours(1),
            ShiftStatus.SCHEDULED,
            RequestStatus.OPEN,
            CandidateStatus.PENDING
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<SubstituteCandidate> result =
            substituteCandidateRepository.findReceivedRequests(
                candidateUser.getId(), // 이 사용자에게 온 요청 조회
                now                    // 현재 시간 기준
            );

        // then
        assertThat(result)
            .extracting(SubstituteCandidate::getId)
            .containsExactly(earlierCandidate.getId(), validCandidate.getId());

        // 다른 사용자 입장에서는 본인 요청만 보여야 한다
        assertThat(
            substituteCandidateRepository.findReceivedRequests(
                otherMember.getUser().getId(),
                now
            )
        )
            .extracting(candidate -> candidate.getMember().getId())
            .containsOnly(otherMember.getId());
    }

    @Test
    @DisplayName("SUB-07 - 내가 수락한 승인 대기 요청만 조회한다")
    void findAcceptedPendingRequests_Success() {

        // given
        LocalDateTime now = LocalDateTime.of(2026, 9, 23, 14, 0);

        Workplace workplace = entityManager.persist(
            new Workplace("SWITCH 카페", "TEST5678")
        );

        User requesterUser = entityManager.persist(
            new User("requester2@test.com", "password", "요청자")
        );

        User candidateUser = entityManager.persist(
            new User("candidate2@test.com", "password", "후보자")
        );

        WorkplaceMember requesterMember = entityManager.persist(
            new WorkplaceMember(
                workplace,
                requesterUser,
                WorkplaceRole.EMPLOYEE,
                now.minusMonths(1)
            )
        );

        WorkplaceMember candidateMember = entityManager.persist(
            new WorkplaceMember(
                workplace,
                candidateUser,
                WorkplaceRole.EMPLOYEE,
                now.minusMonths(1)
            )
        );

        // 공개된 근무표
        Schedule publishedSchedule =
            new Schedule(workplace, LocalDate.of(2026, 9, 21));

        publishedSchedule.publish(now.minusDays(1));
        entityManager.persist(publishedSchedule);

        // 아직 공개되지 않은 근무표
        Schedule draftSchedule =
            new Schedule(workplace, LocalDate.of(2026, 9, 28));

        entityManager.persist(draftSchedule);

        // 1. 모든 조건을 만족 → 조회되어야 함
        SubstituteCandidate validCandidate = createCandidate(
            publishedSchedule,
            requesterMember,
            candidateMember,
            now.plusHours(2),
            ShiftStatus.SCHEDULED,
            RequestStatus.ACCEPTED,
            CandidateStatus.ACCEPTED
        );

        // 2. 아직 수락하지 않은 Candidate → 제외
        createCandidate(
            publishedSchedule,
            requesterMember,
            candidateMember,
            now.plusHours(3),
            ShiftStatus.SCHEDULED,
            RequestStatus.ACCEPTED,
            CandidateStatus.PENDING
        );

        // 3. 이미 최종 승인된 Request → 제외
        createCandidate(
            publishedSchedule,
            requesterMember,
            candidateMember,
            now.plusHours(4),
            ShiftStatus.SCHEDULED,
            RequestStatus.APPROVED,
            CandidateStatus.ACCEPTED
        );

        // 4. 종료된 Request → 제외
        createCandidate(
            publishedSchedule,
            requesterMember,
            candidateMember,
            now.plusHours(5),
            ShiftStatus.SCHEDULED,
            RequestStatus.CLOSED,
            CandidateStatus.ACCEPTED
        );

        // 5. 이미 시작된 Shift → 제외
        createCandidate(
            publishedSchedule,
            requesterMember,
            candidateMember,
            now.minusHours(1),
            ShiftStatus.SCHEDULED,
            RequestStatus.ACCEPTED,
            CandidateStatus.ACCEPTED
        );

        // 6. DRAFT 근무표 → 제외
        createCandidate(
            draftSchedule,
            requesterMember,
            candidateMember,
            now.plusDays(8),
            ShiftStatus.SCHEDULED,
            RequestStatus.ACCEPTED,
            CandidateStatus.ACCEPTED
        );

        // 7. 취소된 Shift → 제외
        createCandidate(
            publishedSchedule,
            requesterMember,
            candidateMember,
            now.plusHours(7),
            ShiftStatus.CANCELLED,
            RequestStatus.ACCEPTED,
            CandidateStatus.ACCEPTED
        );

        // 8. 다른 사용자가 수락한 요청 → userId 조건 확인
        WorkplaceMember otherMember = persistMember(
            workplace, "other2@test.com", WorkplaceRole.EMPLOYEE, now
        );

        createCandidate(
            publishedSchedule,
            requesterMember,
            otherMember,
            now.plusHours(8),
            ShiftStatus.SCHEDULED,
            RequestStatus.ACCEPTED,
            CandidateStatus.ACCEPTED
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<SubstituteCandidate> result =
            substituteCandidateRepository.findAcceptedPendingRequests(
                candidateUser.getId(), // 현재 로그인 User
                now                    // 현재 시간
            );

        // then
        assertThat(result)
            .extracting(SubstituteCandidate::getId)
            .containsExactly(validCandidate.getId());
    }

    @Test
    @DisplayName("SUB-01/03 - 이미 수락한 활성 대타 약속과 시간이 겹치는지 확인한다")
    void existsOverlappingAcceptedSubstitute() {

        // given
        LocalDateTime now = LocalDateTime.of(2026, 9, 21, 12, 0);
        LocalDateTime startAt = now.plusDays(1);     // 내일 12:00
        LocalDateTime endAt = startAt.plusHours(4);  // 내일 16:00

        ConflictFixture fixture = createConflictFixture(now, startAt, "overlap");

        Long candidateUserId = fixture.candidateMember().getUser().getId();
        Long otherUserId = fixture.otherMember().getUser().getId();

        // then - 같은 사용자 + 겹치는 시간 → 충돌
        assertThat(substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
            candidateUserId, startAt.plusHours(2), endAt.plusHours(2), now
        )).isTrue();

        // then - 다른 사용자 → 충돌 아님 (userId 조건)
        assertThat(substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
            otherUserId, startAt, endAt, now
        )).isFalse();

        // then - 끝나는 시각에 바로 시작(경계값) → 충돌 아님
        assertThat(substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
            candidateUserId, endAt, endAt.plusHours(4), now
        )).isFalse();

        // then - 이미 시작된 대타 약속은 활성 약속으로 보지 않는다
        assertThat(substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
            candidateUserId, startAt, endAt, startAt
        )).isFalse();

        // then - Request가 CLOSED 된 수락 Candidate는 활성 약속이 아니다
        assertThat(substituteCandidateRepository.existsOverlappingAcceptedSubstitute(
            candidateUserId,
            fixture.closedStartAt(),
            fixture.closedStartAt().plusHours(4),
            now
        )).isFalse();
    }

    @Test
    @DisplayName("SUB-04 - 승인 시 현재 Request를 제외한 다른 활성 대타 약속과의 충돌만 확인한다")
    void existsConflictingActiveSubstitute() {

        // given
        LocalDateTime now = LocalDateTime.of(2026, 9, 21, 12, 0);
        LocalDateTime startAt = now.plusDays(1);
        LocalDateTime endAt = startAt.plusHours(4);

        ConflictFixture fixture = createConflictFixture(now, startAt, "conflict");

        Long candidateUserId = fixture.candidateMember().getUser().getId();
        Long otherUserId = fixture.otherMember().getUser().getId();
        Long acceptedRequestId = fixture.acceptedCandidate().getRequest().getId();

        // then - 승인하려는 Request 자기 자신은 충돌로 보지 않는다
        assertThat(substituteCandidateRepository.existsConflictingActiveSubstitute(
            candidateUserId, acceptedRequestId, startAt, endAt, now
        )).isFalse();

        // then - 다른 Request 기준으로는 충돌
        assertThat(substituteCandidateRepository.existsConflictingActiveSubstitute(
            candidateUserId, -1L, startAt, endAt, now
        )).isTrue();

        // then - 다른 사용자 → 충돌 아님 (userId 조건)
        assertThat(substituteCandidateRepository.existsConflictingActiveSubstitute(
            otherUserId, -1L, startAt, endAt, now
        )).isFalse();
    }

    // 후보자가 수락한 활성 약속 1건 + CLOSED 된 수락 1건을 만든다
    private ConflictFixture createConflictFixture(
        LocalDateTime now,
        LocalDateTime acceptedStartAt,
        String suffix
    ) {
        Workplace workplace = entityManager.persist(
            new Workplace("충돌 테스트 매장 " + suffix, "CF-" + suffix)
        );

        WorkplaceMember requesterMember = persistMember(
            workplace, "requester-" + suffix + "@test.com", WorkplaceRole.EMPLOYEE, now
        );
        WorkplaceMember candidateMember = persistMember(
            workplace, "candidate-" + suffix + "@test.com", WorkplaceRole.EMPLOYEE, now
        );
        WorkplaceMember otherMember = persistMember(
            workplace, "other-" + suffix + "@test.com", WorkplaceRole.EMPLOYEE, now
        );

        Schedule schedule = new Schedule(workplace, LocalDate.of(2026, 9, 21));
        schedule.publish(now.minusDays(1));
        entityManager.persist(schedule);

        SubstituteCandidate acceptedCandidate = createCandidate(
            schedule,
            requesterMember,
            candidateMember,
            acceptedStartAt,
            ShiftStatus.SCHEDULED,
            RequestStatus.ACCEPTED,
            CandidateStatus.ACCEPTED
        );

        LocalDateTime closedStartAt = acceptedStartAt.plusDays(1);

        createCandidate(
            schedule,
            requesterMember,
            candidateMember,
            closedStartAt,
            ShiftStatus.SCHEDULED,
            RequestStatus.CLOSED,
            CandidateStatus.ACCEPTED
        );

        entityManager.flush();

        return new ConflictFixture(
            candidateMember,
            otherMember,
            acceptedCandidate,
            closedStartAt
        );
    }

    private WorkplaceMember persistMember(
        Workplace workplace,
        String email,
        WorkplaceRole role,
        LocalDateTime now
    ) {
        User user = entityManager.persist(new User(email, "password", email));

        return entityManager.persist(
            new WorkplaceMember(workplace, user, role, now.minusMonths(1))
        );
    }

    private record ConflictFixture(
        WorkplaceMember candidateMember,
        WorkplaceMember otherMember,
        SubstituteCandidate acceptedCandidate,
        LocalDateTime closedStartAt
    ) {
    }

    private SubstituteCandidate createCandidate(
        Schedule schedule,                  // 어느 근무표의 Shift인지
        WorkplaceMember requesterMember,    // 대타 요청자
        WorkplaceMember candidateMember,    // 대타 후보자
        LocalDateTime startAt,              // 근무 시작 시간
        ShiftStatus shiftStatus,            // SCHEDULED / CANCELLED
        RequestStatus requestStatus,        // OPEN / CLOSED 등
        CandidateStatus candidateStatus     // PENDING / REJECTED 등
    ) {

        Shift shift = entityManager.persist(
            new Shift(
                schedule,
                requesterMember,
                startAt,
                startAt.plusHours(4),
                shiftStatus
            )
        );

        SubstituteRequest request = entityManager.persist(
            new SubstituteRequest(
                shift,
                requesterMember,
                requestStatus
            )
        );

        SubstituteCandidate candidate =
            new SubstituteCandidate(request, candidateMember);

        // 생성자는 기본적으로 PENDING이므로,
        // REJECTED 같은 다른 상태 테스트가 필요할 때만 바꿔준다.
        ReflectionTestUtils.setField(
            candidate,
            "status",
            candidateStatus
        );

        return entityManager.persist(candidate);
    }
}