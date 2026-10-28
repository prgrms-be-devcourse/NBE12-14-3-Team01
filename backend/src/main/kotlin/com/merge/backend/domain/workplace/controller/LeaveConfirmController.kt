package com.merge.backend.domain.workplace.controller

import com.merge.backend.domain.workplace.dto.request.LeaveConfirmRequest
import com.merge.backend.domain.workplace.dto.response.LeaveConfirmResponse
import com.merge.backend.domain.workplace.service.LeaveConfirmService
import com.merge.backend.domain.workplace.service.LeaveConfirmationIntegration
import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.rq.Rq
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** #92 및 경쟁 Command Guard 통합 검증 후 Bridge Bean을 등록해야 노출된다. */
@RestController
@ConditionalOnBean(LeaveConfirmationIntegration::class)
@RequestMapping("/workplaces")
class LeaveConfirmController(private val service: LeaveConfirmService, private val rq: Rq) {
    @PostMapping("/{workplaceId}/members/{memberId}/leave")
    fun confirm(@PathVariable workplaceId: Long, @PathVariable memberId: Long,
        @Valid @RequestBody request: LeaveConfirmRequest): ResponseEntity<ApiResponse<LeaveConfirmResponse>> =
        ResponseEntity.ok(ApiResponse.success("200", "퇴사 처리를 완료했습니다.",
            service.confirm(rq.actorId, workplaceId, memberId, request)))
}
