package com.merge.backend.domain.shift.controller

import com.merge.backend.domain.shift.dto.UnavailableTimeListResponse
import com.merge.backend.domain.shift.dto.UnavailableTimeRegisterReqBody
import com.merge.backend.domain.shift.dto.UnavailableTimeRegisterResponse
import com.merge.backend.domain.shift.dto.UnavailableTimeUpdateReqBody
import com.merge.backend.domain.shift.dto.UnavailableTimeUpdateResponse
import com.merge.backend.domain.shift.service.UnavailableTimeService
import com.merge.backend.global.dto.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/unavailable-times")
class UnavailableTimeController(
    private val unavailableTimeService: UnavailableTimeService
) {

    @GetMapping
    fun list(): ResponseEntity<ApiResponse<List<UnavailableTimeListResponse>>> {
        val results = unavailableTimeService.findAll()

        val responses = results.stream()
            .map { result ->
                UnavailableTimeListResponse(
                    result.unavailableTimeId,
                    result.startAt,
                    result.endAt,
                    result.officialShiftConflict
                )
            }
            .toList()

        return ResponseEntity.ok(
            ApiResponse.success(
                "200",
                "근무 불가능 일정을 조회했습니다.",
                responses
            )
        )
    }

    @PostMapping
    fun register(
        @Valid @RequestBody reqBody: UnavailableTimeRegisterReqBody
    ): ResponseEntity<ApiResponse<UnavailableTimeRegisterResponse>> {
        val startAt = checkNotNull(reqBody.startAt)
        val endAt = checkNotNull(reqBody.endAt)
        val confirmOfficialShiftConflict = checkNotNull(reqBody.confirmOfficialShiftConflict)

        val savedUnavailableTime =
            unavailableTimeService.register(
                startAt,
                endAt,
                confirmOfficialShiftConflict
            )

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(
                ApiResponse.success(
                    "201",
                    "근무 불가능 일정이 등록되었습니다",
                    UnavailableTimeRegisterResponse(
                        savedUnavailableTime.id,
                        savedUnavailableTime.startAt,
                        savedUnavailableTime.endAt
                    )
                )
            )
    }

    @PatchMapping("/{unavailableTimeId}")
    fun update(
        @PathVariable unavailableTimeId: Long?,
        @Valid @RequestBody reqBody: UnavailableTimeUpdateReqBody
    ): ResponseEntity<ApiResponse<UnavailableTimeUpdateResponse>> {
        val startAt = checkNotNull(reqBody.startAt)
        val endAt = checkNotNull(reqBody.endAt)
        val confirmOfficialShiftConflict = checkNotNull(reqBody.confirmOfficialShiftConflict)

        val unavailableTime =
            unavailableTimeService.update(
                unavailableTimeId,
                startAt,
                endAt,
                confirmOfficialShiftConflict
            )

        return ResponseEntity.status(HttpStatus.OK)
            .body(
                ApiResponse.success(
                    "200",
                    "근무 불가능 일정이 수정되었습니다",
                    UnavailableTimeUpdateResponse(
                        unavailableTime.id,
                        unavailableTime.startAt,
                        unavailableTime.endAt
                    )
                )
            )
    }

    @DeleteMapping("/{unavailableTimeId}")
    fun delete(
        @PathVariable unavailableTimeId: Long?
    ): ResponseEntity<ApiResponse<Void?>> {
        unavailableTimeService.delete(unavailableTimeId)

        return ResponseEntity.ok(
            ApiResponse.success(
                "200",
                "근무 불가능 일정이 삭제되었습니다."
            )
        )
    }
}
