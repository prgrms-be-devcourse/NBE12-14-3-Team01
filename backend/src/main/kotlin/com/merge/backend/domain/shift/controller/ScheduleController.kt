package com.merge.backend.domain.shift.controller

import com.merge.backend.domain.shift.dto.ManagerScheduleResponse
import com.merge.backend.domain.shift.dto.ScheduleCreateRequest
import com.merge.backend.domain.shift.dto.ScheduleCreateResponse
import com.merge.backend.domain.shift.dto.SchedulePublishRequest
import com.merge.backend.domain.shift.dto.SchedulePublishResponse
import com.merge.backend.domain.shift.service.ScheduleService
import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.rq.Rq
import jakarta.validation.Valid
import java.time.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/workplaces/{workplaceId}/schedules")
class ScheduleController(
    private val scheduleService: ScheduleService,
    private val rq: Rq
) {

    @PostMapping
    fun createDraftSchedule(
        @PathVariable workplaceId: Long?,
        @Valid @RequestBody request: ScheduleCreateRequest
    ): ResponseEntity<ApiResponse<ScheduleCreateResponse>> {
        val actorUserId = rq.actorId
        val weekStartDate = checkNotNull(request.weekStartDate)
        val response = scheduleService.createDraftSchedule(
            actorUserId,
            workplaceId,
            weekStartDate
        )

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                ApiResponse.success(
                    "201",
                    "주간 근무표 초안이 생성되었습니다.",
                    response
                )
            )
    }

    @GetMapping
    fun getWeeklySchedule(
        @PathVariable workplaceId: Long?,
        @RequestParam weekStartDate: LocalDate
    ): ResponseEntity<ApiResponse<ManagerScheduleResponse>> {
        val actorUserId = rq.actorId
        val response = scheduleService.getWeeklySchedule(
            actorUserId,
            workplaceId,
            weekStartDate
        )

        if (response == null) {
            return ResponseEntity.ok(
                ApiResponse.success<ManagerScheduleResponse>(
                    "200",
                    "해당 주차의 근무표가 아직 생성되지 않았습니다.",
                    null
                )
            )
        }

        return ResponseEntity.ok(
            ApiResponse.success(
                "200",
                "주간 근무표를 조회했습니다.",
                response
            )
        )
    }

    @PatchMapping("/{scheduleId}/publish")
    fun publishSchedule(
        @PathVariable workplaceId: Long?,
        @PathVariable scheduleId: Long?,
        @Valid @RequestBody request: SchedulePublishRequest
    ): ResponseEntity<ApiResponse<SchedulePublishResponse>> {
        val actorUserId = rq.actorId
        val confirmUnavailableConflict =
            checkNotNull(request.confirmUnavailableConflict)
        val response = scheduleService.publishSchedule(
            actorUserId,
            workplaceId,
            scheduleId,
            confirmUnavailableConflict
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                "200",
                "근무표가 공개되었습니다.",
                response
            )
        )
    }
}
