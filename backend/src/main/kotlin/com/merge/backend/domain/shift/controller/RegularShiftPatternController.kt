package com.merge.backend.domain.shift.controller

import com.merge.backend.domain.shift.dto.RegularShiftPatternListResponse
import com.merge.backend.domain.shift.dto.RegularShiftPatternReqBody
import com.merge.backend.domain.shift.dto.RegularShiftPatternResponse
import com.merge.backend.domain.shift.service.RegularShiftPatternService
import com.merge.backend.global.dto.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/workplaces/{workplaceId}/regular-shift-patterns")
class RegularShiftPatternController(
    private val regularShiftPatternService: RegularShiftPatternService
) {
    @GetMapping
    fun list(@PathVariable("workplaceId") workplaceId: Long?): ApiResponse<List<RegularShiftPatternListResponse>> {
        val regularShiftPatternList = regularShiftPatternService.findAll(workplaceId)
        return ApiResponse.success("200", regularShiftPatternList)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("")
    fun register(
        @PathVariable("workplaceId") workplaceId: Long?,
        @Valid @RequestBody reqBody: RegularShiftPatternReqBody
    ): ApiResponse<RegularShiftPatternResponse> {
        val newRegularShiftPattern = regularShiftPatternService.register(workplaceId, reqBody)
        return ApiResponse.success("201", "정기 근무가 등록되었습니다.", newRegularShiftPattern)
    }

    @PutMapping("/{patternId}")
    fun update(
        @PathVariable("workplaceId") workplaceId: Long?,
        @PathVariable("patternId") patternId: Long?,
        @Valid @RequestBody reqBody: RegularShiftPatternReqBody
    ): ApiResponse<RegularShiftPatternResponse> {
        val updatedRegularShiftPattern = regularShiftPatternService.update(workplaceId, patternId, reqBody)
        return ApiResponse.success("200", "정기 근무가 수정되었습니다", updatedRegularShiftPattern)
    }

    @DeleteMapping("/{patternId}")
    fun delete(
        @PathVariable("workplaceId") workplaceId: Long?,
        @PathVariable("patternId") patternId: Long?
    ): ApiResponse<Void?> {
        regularShiftPatternService.delete(workplaceId, patternId)
        return ApiResponse.success("200", "%d번 정기 근무가 삭제되었습니다".format(patternId))
    }
}
