package com.merge.backend.domain.workplace.dto.request

import com.merge.backend.domain.workplace.validation.ValidLeaveConfirmRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.time.LocalDateTime

/** API-LV-02. 쓰기 엔드포인트는 전체 도메인 연동 이후에 연결한다. */
@ValidLeaveConfirmRequest
data class LeaveConfirmRequest(
    @field:NotBlank val previewContext: String?,
    @field:NotNull @field:Valid val decisions: List<Decision?>?,
    @field:Valid val acknowledgedUnavailableConflicts: List<AcknowledgedUnavailableConflict?> = emptyList(),
    val regenerateInviteCode: Boolean = false
) {
    data class Decision(
        @field:NotNull val shiftId: Long?,
        @field:NotNull val shiftStartAt: LocalDateTime?,
        @field:NotNull val shiftEndAt: LocalDateTime?,
        @field:NotNull @field:Pattern(regexp = "REASSIGN|CANCEL") val action: String?,
        val targetMemberId: Long? = null
    )

    // #91과 동일한 요청 형식이며, V3의 충돌 판정 로직은 재사용한다.
    data class AcknowledgedUnavailableConflict(
        @field:NotNull val shiftId: Long?,
        @field:NotNull val targetMemberId: Long?,
        @field:NotNull @field:Valid val overlaps: List<Overlap?>?
    )

    data class Overlap(
        @field:NotNull val startAt: LocalDateTime?,
        @field:NotNull val endAt: LocalDateTime?
    )
}
