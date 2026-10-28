package com.merge.backend.domain.shift.controller

import com.merge.backend.domain.shift.dto.RemovedShiftResponse
import com.merge.backend.domain.shift.service.RemovedShiftQueryService
import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.rq.Rq
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class RemovedShiftController(
    private val removedShiftQueryService: RemovedShiftQueryService,
    private val rq: Rq,
) {

    // API-SC-01 - 빠진 근무 (원래 시작 시각 기준 미래 + 최근 7일)
    @GetMapping("/schedules/me/removed")
    fun getRemovedShifts(): ResponseEntity<ApiResponse<List<RemovedShiftResponse>>> {
        val userId = rq.actorId

        val response = removedShiftQueryService.findRemovedShifts(userId)

        return ResponseEntity.ok(
            ApiResponse.success("200", "빠진 근무를 조회했습니다.", response),
        )
    }
}
