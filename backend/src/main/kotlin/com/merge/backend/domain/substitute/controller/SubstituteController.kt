package com.merge.backend.domain.substitute.controller

import com.merge.backend.domain.substitute.dto.SentSubstituteRequestResponse
import com.merge.backend.domain.substitute.dto.SubstituteRequestCreateResponse
import com.merge.backend.domain.substitute.service.SubstituteRequestService
import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.rq.Rq
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SubstituteController(
    private val substituteRequestService: SubstituteRequestService,
    private val rq: Rq,
) {

    @PostMapping("/shifts/{shiftId}/substitute-requests")
    fun create(
        @PathVariable shiftId: Long,
    ): ResponseEntity<ApiResponse<SubstituteRequestCreateResponse>> {
        val response = substituteRequestService.create(shiftId, rq.actorId)

        if (response.requestCreated) {
            return ResponseEntity.status(201).body(
                ApiResponse.success("201", "대타 요청이 생성되었습니다.", response),
            )
        }

        return ResponseEntity.status(200).body(
            ApiResponse.success("200", "현재 대타 요청을 보낼 수 있는 후보가 없습니다.", response),
        )
    }

    @GetMapping("/substitute-requests/sent")
    fun getSentRequests(): ResponseEntity<ApiResponse<List<SentSubstituteRequestResponse>>> {
        val responses = substituteRequestService.getSentRequests(rq.actorId)

        return ResponseEntity.status(200).body(
            ApiResponse.success("200", "보낸 대타 요청 목록을 조회했습니다.", responses),
        )
    }
}
