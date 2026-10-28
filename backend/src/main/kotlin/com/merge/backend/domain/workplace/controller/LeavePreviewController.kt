package com.merge.backend.domain.workplace.controller

import com.merge.backend.domain.workplace.dto.response.LeavePreviewResponse
import com.merge.backend.domain.workplace.service.LeavePreviewService
import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.rq.Rq
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/workplaces")
class LeavePreviewController(
    private val leavePreviewService: LeavePreviewService,
    private val rq: Rq
) {
    @GetMapping("/{workplaceId}/members/{memberId}/leave-preview")
    fun getLeavePreview(
        @PathVariable workplaceId: Long,
        @PathVariable memberId: Long
    ): ResponseEntity<ApiResponse<LeavePreviewResponse>> = ResponseEntity.ok(
        ApiResponse.success(
            "200", "퇴사 미리보기를 조회했습니다.",
            leavePreviewService.getPreview(rq.actorId, workplaceId, memberId)
        )
    )
}
