package com.merge.backend.domain.substitute.dto

import jakarta.validation.constraints.NotNull

@JvmRecord
data class SubstituteCandidateRespondRequest(
    @field:NotNull
    val decision: Decision?, // 사용자가 선택한 행동: 수락 또는 거절
) {

    enum class Decision {
        ACCEPT, // 대타 요청 수락
        REJECT, // 대타 요청 거절
    }
}
