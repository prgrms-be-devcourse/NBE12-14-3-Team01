package com.merge.backend.domain.substitute.controller

import com.merge.backend.domain.substitute.dto.SubstituteCandidateRespondRequest
import com.merge.backend.domain.substitute.dto.SubstituteCandidateRespondResponse
import com.merge.backend.domain.substitute.service.SubstituteCandidateService
import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.rq.Rq
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/substitute-candidates")
class SubstituteCandidateController(
    private val substituteCandidateService: SubstituteCandidateService,
    private val rq: Rq,
) {

    @PatchMapping("/{candidateId}/response")
    fun respond(
        @PathVariable candidateId: Long, // 응답할 Candidate ID
        @Valid @RequestBody request: SubstituteCandidateRespondRequest,
    ): ResponseEntity<ApiResponse<SubstituteCandidateRespondResponse>> {
        val userId = rq.actorId // 현재 로그인한 User ID

        val candidate =
            if (request.decision == SubstituteCandidateRespondRequest.Decision.ACCEPT) {
                // 사용자가 수락을 선택한 경우
                substituteCandidateService.acceptCandidate(candidateId, userId)
            } else {
                // 사용자가 거절을 선택한 경우
                substituteCandidateService.rejectCandidate(candidateId, userId)
            }

        // Service가 반환한 Entity를 API 응답 DTO로 변환
        val response = SubstituteCandidateRespondResponse.from(candidate)

        val message =
            if (request.decision == SubstituteCandidateRespondRequest.Decision.ACCEPT) {
                "대타 요청을 수락했습니다."
            } else {
                "대타 요청을 거절했습니다."
            }

        return ResponseEntity.ok(ApiResponse.success("200", message, response))
    }
}
