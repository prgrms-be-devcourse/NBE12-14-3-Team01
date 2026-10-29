package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.substitute.service.CandidateIneligibleClosedRequest
import java.time.LocalDateTime

/**
 * #92의 실제 V4 정리와 연결하는 경계. 이 Bean만으로 POST를 활성화하지 않는다.
 * cleanup은 호출 Transaction에 합류하고, 전달된 Request 외의 새 Row를 순서 밖으로 잠그지 않는다.
 * #92의 상태 전이를 복제하지 않는다.
 */
interface LeaveConfirmationIntegration {
    fun cleanupCandidacies(memberId: Long, now: LocalDateTime, lockedRequestIds: Set<Long>): CandidacyCleanup

    data class CandidacyCleanup(
        val closedRequestIds: Set<Long>,
        val ineligibleCandidacies: Int,
        // 알림 계산용: CANDIDATE_INELIGIBLE로 닫은 요청의 요청자 (V5-N-20)
        val closedRequests: List<CandidateIneligibleClosedRequest> = emptyList(),
    )
}

/**
 * DRAFT/Publish/대타/배정 경로의 소속 Guard와 미확정 HTTP 계약 검증이 끝났음을 나타내는
 * 별도 활성화 조건. V4 Adapter와 분리해 둔다.
 */
interface LeaveConfirmationReadiness

/**
 * 퇴사 확정 POST 활성화 (2026-10-29). 경쟁 Command의 소속 Guard가 모두 들어감:
 * 대타 생성·수락·승인 (PR #105), DRAFT 추가·수정·Pattern 복사 (PR #106). 공개 후 추가·재배정은 #91·#93에서 이미 소속 FOR SHARE.
 * 공식 근무 충돌은 기존 SFT-009/010, 원본 검증 실패는 WPL-010 (DL-016 리뷰어 추천, 팀 기록 필요)
 */
@org.springframework.stereotype.Component
class MembershipGuardLeaveConfirmationReadiness : LeaveConfirmationReadiness
