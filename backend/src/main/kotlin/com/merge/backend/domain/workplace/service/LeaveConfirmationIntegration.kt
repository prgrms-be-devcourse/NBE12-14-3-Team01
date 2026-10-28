package com.merge.backend.domain.workplace.service

import java.time.LocalDateTime

/**
 * #92 병합 후 V4가 구현할 연동 경계. 이 Bean 등록은 POST 활성화 조건이다.
 * 등록 전 DRAFT 편집/생성, Publish, 대타 생성/수락/승인의 소속 Guard를 통합 검증해야 한다.
 * cleanup은 호출 Transaction에 합류하고, 전달된 Request 외의 새 Row를 순서 밖으로 잠그지 않는다.
 * #92의 상태 전이를 복제하거나 미병합 타입을 참조하지 않는다.
 */
interface LeaveConfirmationIntegration {
    fun cleanupCandidacies(memberId: Long, now: LocalDateTime, lockedRequestIds: Set<Long>): CandidacyCleanup

    data class CandidacyCleanup(val closedRequestIds: Set<Long>, val ineligibleCandidacies: Int)
}
