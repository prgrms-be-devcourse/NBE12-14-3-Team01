package com.merge.backend.domain.workplace.service

import java.time.LocalDateTime

/**
 * #92의 실제 V4 정리와 연결하는 경계. 이 Bean만으로 POST를 활성화하지 않는다.
 * cleanup은 호출 Transaction에 합류하고, 전달된 Request 외의 새 Row를 순서 밖으로 잠그지 않는다.
 * #92의 상태 전이를 복제하지 않는다.
 */
interface LeaveConfirmationIntegration {
    fun cleanupCandidacies(memberId: Long, now: LocalDateTime, lockedRequestIds: Set<Long>): CandidacyCleanup

    data class CandidacyCleanup(val closedRequestIds: Set<Long>, val ineligibleCandidacies: Int)
}

/**
 * DRAFT/Publish/대타/배정 경로의 소속 Guard와 미확정 HTTP 계약 검증이 끝났음을 나타내는
 * 별도 활성화 조건. 아직 구현 Bean을 제공하지 않으며 V4 Adapter와 분리해 둔다.
 */
interface LeaveConfirmationReadiness
