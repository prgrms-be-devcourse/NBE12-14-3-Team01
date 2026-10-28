package com.merge.backend.domain.substitute.service

// 퇴사로 정리한 대타 후보 결과. 호출한 쪽(V2 퇴사 확정)이 응답 개수와 알림 받는 사람을 정할 때 씀
data class MemberLeaveCandidacyResult(
    // CANDIDATE_INELIGIBLE로 닫은 요청 (요청 ID 오름차순)
    val closedRequests: List<CandidateIneligibleClosedRequest>,
    // INELIGIBLE로 바꾼 대기 후보 수 (API-LV-02 ineligibleCandidacies)
    val ineligibleCandidacies: Int,
)

data class CandidateIneligibleClosedRequest(
    val requestId: Long,
    val requesterMemberId: Long,
)
