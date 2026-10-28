package com.merge.backend.domain.substitute.repository

import com.merge.backend.domain.substitute.entity.CandidateStatus
import com.merge.backend.domain.substitute.entity.SubstituteCandidate
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SubstituteCandidateRepository : JpaRepository<SubstituteCandidate, Long> {

    // Candidate의 Request ID만 조회하는 메서드
    @Query(
        """
        SELECT c.request.id
        FROM SubstituteCandidate c
        WHERE c.id = :candidateId
        """,
    )
    fun findRequestIdById(@Param("candidateId") candidateId: Long): Long?

    // SUB-01 / SUB-03
    // 이미 수락한 다른 활성 대타 근무와 시간이 겹치는지 확인
    @Query(
        """
        SELECT COUNT(c) > 0
        FROM SubstituteCandidate c
        WHERE c.member.user.id = :userId
            AND c.status = 'ACCEPTED'
            AND c.request.status = 'ACCEPTED'
            AND c.request.shift.startAt < :endAt
            AND c.request.shift.endAt > :startAt
            AND c.request.shift.startAt > :now
        """,
    )
    fun existsOverlappingAcceptedSubstitute(
        @Param("userId") userId: Long,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime,
        @Param("now") now: LocalDateTime,
    ): Boolean

    // SUB-02
    // 현재 로그인 User에게 온 응답 가능한 대타 요청 조회
    @EntityGraph(
        attributePaths = [
            "request",
            "request.shift",
            "request.shift.schedule",
            "request.shift.schedule.workplace",
            "request.requesterMember",
            "request.requesterMember.user",
        ],
    )
    @Query(
        """
        SELECT c
        FROM SubstituteCandidate c
        WHERE c.member.user.id = :userId
          AND c.member.leftAt IS NULL
          AND c.status = 'PENDING'
          AND c.request.status = 'OPEN'
          AND c.request.shift.startAt > :now
          AND c.request.shift.schedule.status = 'PUBLISHED'
          AND c.request.shift.status = 'SCHEDULED'
        ORDER BY c.request.shift.startAt ASC,
                 c.request.id ASC
        """,
    )
    fun findReceivedRequests(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime,
    ): List<SubstituteCandidate>

    // SUB-07
    // 현재 로그인 User가 수락했고 MANAGER 승인을 기다리는 대타 요청 조회
    @EntityGraph(
        attributePaths = [
            "request",
            "request.shift",
            "request.shift.schedule",
            "request.shift.schedule.workplace",
            "request.requesterMember",
            "request.requesterMember.user",
        ],
    )
    @Query(
        """
        SELECT c
        FROM SubstituteCandidate c
        WHERE c.member.user.id = :userId
          AND c.member.leftAt IS NULL
          AND c.status = 'ACCEPTED'
          AND c.request.status = 'ACCEPTED'
          AND c.request.shift.startAt > :now
          AND c.request.shift.schedule.status = 'PUBLISHED'
          AND c.request.shift.status = 'SCHEDULED'
        ORDER BY c.request.shift.startAt ASC,
                 c.request.id ASC
        """,
    )
    fun findAcceptedPendingRequests(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime,
    ): List<SubstituteCandidate>

    // API-SB-01
    // 현재 소속으로 내가 수락했고 끝난 대타 요청 (승인·종료·계산된 만료), 효력 종료 시각이 :from 이후
    @EntityGraph(
        attributePaths = [
            "request",
            "request.shift",
            "request.shift.schedule",
            "request.shift.schedule.workplace",
            "request.requesterMember",
            "request.requesterMember.user",
        ],
    )
    @Query(
        """
        SELECT c
        FROM SubstituteCandidate c
        WHERE c.member.user.id = :userId
          AND c.member.leftAt IS NULL
          AND c.status = 'ACCEPTED'
          AND (
                (c.request.status IN ('APPROVED', 'CLOSED') AND c.request.closedAt >= :from)
             OR (c.request.status IN ('OPEN', 'ACCEPTED')
                 AND c.request.shift.startAt <= :now
                 AND c.request.shift.startAt >= :from)
          )
        """,
    )
    fun findAcceptedClosedRequests(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime,
        @Param("from") from: LocalDateTime,
    ): List<SubstituteCandidate>

    // 특정 Request에 대해 상태가 ACCEPTED인 Candidate 단건 조회
    fun findByRequestIdAndStatus(
        requestId: Long,
        status: CandidateStatus,
    ): SubstituteCandidate?

    // 후보자가 다른 대체 요청에서 이미 수락한 활성 대타 약속 중,
    // 동시간대 겹침이 있는지 확인
    @Query(
        """
        select count(c) > 0
        from SubstituteCandidate c
        where c.member.user.id = :userId
            and c.request.id <> :requestId
            and c.status = CandidateStatus.ACCEPTED
            and c.request.status = RequestStatus.ACCEPTED
            and c.request.shift.startAt < :endAt
            and c.request.shift.endAt > :startAt
            and c.request.shift.startAt > :now
        """,
    )
    fun existsConflictingActiveSubstitute(
        @Param("userId") userId: Long,
        @Param("requestId") requestId: Long,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime,
        @Param("now") now: LocalDateTime,
    ): Boolean

    // N+1 해결용 - IN 절로 한 번에 조회
    @Query(
        """
        SELECT c
        FROM SubstituteCandidate c
        JOIN FETCH c.member m
        JOIN FETCH m.user
        WHERE c.request.id IN :requestIds
          AND c.status = :status
        """,
    )
    fun findByRequestIdInAndStatus(
        @Param("requestIds") requestIds: List<Long>,
        @Param("status") status: CandidateStatus,
    ): List<SubstituteCandidate>

    // 퇴사 처리: 그 요청에서 이 소속의 후보 상태를 DB 값으로 확인 (Lock 전에 읽힌 Entity는 옛 값일 수 있음)
    @Query(
        """
        SELECT c.status
        FROM SubstituteCandidate c
        WHERE c.request.id = :requestId
          AND c.member.id = :memberId
        """,
    )
    fun findStatusByRequestIdAndMemberId(
        @Param("requestId") requestId: Long,
        @Param("memberId") memberId: Long,
    ): CandidateStatus?

    fun findByRequestIdAndMemberId(requestId: Long, memberId: Long): SubstituteCandidate?

    // 퇴사·거절 공통: 이 소속을 빼고 응답 가능한 후보가 남아 있는지 (ML-3-P1)
    // 응답 가능 = 수락·거절할 수 있는 후보 (같은 근무지의 현재 EMPLOYEE 소속, PENDING). 퇴사 미리보기와 같은 조건
    @Query(
        """
        SELECT COUNT(c) > 0
        FROM SubstituteCandidate c
        WHERE c.request.id = :requestId
          AND c.member.id <> :memberId
          AND c.member.leftAt IS NULL
          AND c.member.role = com.merge.backend.domain.workplace.entity.WorkplaceRole.EMPLOYEE
          AND c.member.workplace.id = c.request.shift.schedule.workplace.id
          AND c.status = com.merge.backend.domain.substitute.entity.CandidateStatus.PENDING
        """,
    )
    fun existsRespondableCandidateExcept(
        @Param("requestId") requestId: Long,
        @Param("memberId") memberId: Long,
    ): Boolean

    // 거절 처리: 이 Candidate를 빼고 REJECTED가 아닌 후보가 있는지 (있으면 자격 상실이 종료 원인)
    fun existsByRequest_IdAndIdNotAndStatusNot(requestId: Long, candidateId: Long, status: CandidateStatus): Boolean
}
