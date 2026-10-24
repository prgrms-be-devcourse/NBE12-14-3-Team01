package com.merge.backend.domain.substitute.controller

import com.merge.backend.domain.substitute.dto.AcceptedPendingSubstituteRequestResponse
import com.merge.backend.domain.substitute.dto.ReceivedSubstituteRequestResponse
import com.merge.backend.domain.substitute.dto.SubstituteRequestCloseResponse
import com.merge.backend.domain.substitute.dto.SubstituteRequestListResponse
import com.merge.backend.domain.substitute.dto.SubstituteRequestResponse
import com.merge.backend.domain.substitute.service.SubstituteCandidateService
import com.merge.backend.domain.substitute.service.SubstituteRequestService
import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.rq.Rq
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class SubstituteRequestController(
    private val rq: Rq,
    private val substituteCandidateService: SubstituteCandidateService,
    private val substituteRequestService: SubstituteRequestService,
) {

    // SUB-02 - 내가 받은 응답 가능한 대타 요청 조회
    @GetMapping("/substitute-requests/received")
    fun getReceivedRequests():
        ResponseEntity<ApiResponse<List<ReceivedSubstituteRequestResponse>>> {
        val userId = rq.actorId

        val response = substituteCandidateService
            .findReceivedRequests(userId)
            .map(ReceivedSubstituteRequestResponse::from)

        return ResponseEntity.ok(
            ApiResponse.success("200", "응답 가능한 대타 요청을 조회했습니다.", response),
        )
    }

    // SUB-07 - 내가 수락한 승인 대기 대타 요청 조회
    @GetMapping("/substitute-requests/accepted")
    fun getAcceptedPendingRequests():
        ResponseEntity<ApiResponse<List<AcceptedPendingSubstituteRequestResponse>>> {
        val userId = rq.actorId

        val response = substituteCandidateService
            .findAcceptedPendingRequests(userId)
            .map(AcceptedPendingSubstituteRequestResponse::from)

        return ResponseEntity.ok(
            ApiResponse.success("200", "승인 대기 중인 대타 요청을 조회했습니다.", response),
        )
    }

    // MANAGER - 진행 중인 대타 요청 조회
    @GetMapping("/workplaces/{workplaceId}/substitute-requests/ongoing")
    fun list(
        @PathVariable workplaceId: Long,
    ): ResponseEntity<ApiResponse<List<SubstituteRequestListResponse>>> {
        val actorId = rq.actorId

        val response = substituteRequestService.list(workplaceId, actorId)

        return ResponseEntity.status(200).body(
            ApiResponse.success("200", "진행 중인 대타 요청을 조회했습니다.", response),
        )
    }

    // MANAGER - 대타 요청 최종 승인
    @PatchMapping("/substitute-requests/{requestId}/approve")
    fun approve(
        @PathVariable requestId: Long,
    ): ResponseEntity<ApiResponse<SubstituteRequestResponse>> {
        val actorId = rq.actorId

        val request = substituteRequestService.approve(requestId, actorId)

        return ResponseEntity.status(200).body(
            ApiResponse.success(
                "200",
                "대타 요청이 최종 승인되었습니다.",
                SubstituteRequestResponse.from(request),
            ),
        )
    }

    @PatchMapping("/substitute-requests/{requestId}/close")
    fun close(
        @PathVariable requestId: Long,
    ): ResponseEntity<ApiResponse<SubstituteRequestCloseResponse>> {
        val actorId = rq.actorId

        val request = substituteRequestService.close(requestId, actorId)

        return ResponseEntity.status(200).body(
            ApiResponse.success(
                "200",
                "대타 요청이 종료되었습니다.",
                SubstituteRequestCloseResponse.from(request),
            ),
        )
    }
}
