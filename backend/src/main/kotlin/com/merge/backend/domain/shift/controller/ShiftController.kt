package com.merge.backend.domain.shift.controller

import com.merge.backend.domain.shift.dto.ShiftCreateResponse
import com.merge.backend.domain.shift.dto.ShiftCancelResponse
import com.merge.backend.domain.shift.dto.ShiftDetailResponse
import com.merge.backend.domain.shift.dto.ShiftListResponse
import com.merge.backend.domain.shift.dto.ShiftModifyResponse
import com.merge.backend.domain.shift.dto.ShiftRequest
import com.merge.backend.domain.shift.dto.ShiftReassignRequest
import com.merge.backend.domain.shift.dto.ShiftReassignResponse
import com.merge.backend.domain.shift.service.ShiftService
import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.rq.Rq
import jakarta.validation.Valid
import java.time.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ShiftController(
    private val shiftService: ShiftService,
    private val rq: Rq
) {

    @GetMapping("/schedules/me")
    fun list(
        @RequestParam weekStartDate: LocalDate
    ): ResponseEntity<ApiResponse<ShiftListResponse>> {
        val currentUserId = rq.actorId

        val shifts = shiftService.list(weekStartDate, currentUserId)

        return ResponseEntity.status(HttpStatus.OK).body(
            ApiResponse.success(
                "200",
                "내 주간 근무를 조회했습니다.",
                ShiftListResponse.of(weekStartDate, shifts)
            )
        )
    }

    @GetMapping("/shifts/{shiftId}")
    fun detail(
        @PathVariable shiftId: Long?
    ): ResponseEntity<ApiResponse<ShiftDetailResponse>> {
        val currentUserId = rq.actorId

        val shift = shiftService.detail(shiftId, currentUserId)

        return ResponseEntity.status(HttpStatus.OK).body(
            ApiResponse.success(
                "200",
                ShiftDetailResponse.from(shift)
            )
        )
    }

    @PostMapping("/workplaces/{workplaceId}/schedules/{scheduleId}/shifts")
    fun create(
        @PathVariable workplaceId: Long?,
        @PathVariable scheduleId: Long?,
        @Valid @RequestBody reqBody: ShiftRequest
    ): ResponseEntity<ApiResponse<ShiftCreateResponse>> {
        val shift = shiftService.create(
            reqBody,
            workplaceId,
            scheduleId,
            rq.actorId
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(
                "201",
                "근무가 추가되었습니다.",
                ShiftCreateResponse.from(shift)
            )
        )
    }

    @PatchMapping("/workplaces/{workplaceId}/schedules/{scheduleId}/shifts/{shiftId}")
    fun modify(
        @PathVariable workplaceId: Long?,
        @PathVariable scheduleId: Long?,
        @PathVariable shiftId: Long?,
        @Valid @RequestBody reqBody: ShiftRequest
    ): ResponseEntity<ApiResponse<ShiftModifyResponse>> {
        val modifiedShift = shiftService.modify(
            workplaceId,
            scheduleId,
            shiftId,
            reqBody,
            rq.actorId
        )
        return ResponseEntity.status(HttpStatus.OK).body(
            ApiResponse.success(
                "200",
                "근무가 수정되었습니다.",
                ShiftModifyResponse.from(modifiedShift)
            )
        )
    }

    @DeleteMapping("/workplaces/{workplaceId}/schedules/{scheduleId}/shifts/{shiftId}")
    fun delete(
        @PathVariable workplaceId: Long?,
        @PathVariable scheduleId: Long?,
        @PathVariable shiftId: Long?
    ): ResponseEntity<ApiResponse<Void?>> {
        shiftService.delete(workplaceId, scheduleId, shiftId, rq.actorId)

        return ResponseEntity.status(HttpStatus.OK).body(
            ApiResponse.success<Void?>(
                "200",
                "근무가 삭제되었습니다.",
                null
            )
        )
    }

    @PostMapping("/workplaces/{workplaceId}/schedules/{scheduleId}/shifts/{shiftId}/cancel")
    fun cancel(
        @PathVariable workplaceId: Long?,
        @PathVariable scheduleId: Long?,
        @PathVariable shiftId: Long?,
    ): ResponseEntity<ApiResponse<ShiftCancelResponse>> {
        val cancellationResult = shiftService.cancelPublishedShift(
            workplaceId,
            scheduleId,
            shiftId,
            rq.actorId,
        )

        return ResponseEntity.status(HttpStatus.OK).body(
            ApiResponse.success(
                "200",
                "공개 근무를 취소했습니다.",
                ShiftCancelResponse.from(
                    cancellationResult.shift,
                    cancellationResult.closedSubstituteRequests,
                ),
            )
        )
    }

    @PostMapping("/workplaces/{workplaceId}/schedules/{scheduleId}/shifts/{shiftId}/reassign")
    fun reassign(
        @PathVariable workplaceId: Long?,
        @PathVariable scheduleId: Long?,
        @PathVariable shiftId: Long?,
        @Valid @RequestBody request: ShiftReassignRequest,
    ): ResponseEntity<ApiResponse<ShiftReassignResponse>> {
        val reassignmentResult = shiftService.reassignPublishedShift(
            workplaceId,
            scheduleId,
            shiftId,
            request,
            rq.actorId,
        )

        return ResponseEntity.status(HttpStatus.OK).body(
            ApiResponse.success(
                "200",
                "공개 근무 담당자를 변경했습니다.",
                ShiftReassignResponse.from(
                    reassignmentResult.shift,
                    reassignmentResult.closedSubstituteRequests,
                ),
            ),
        )
    }
}
