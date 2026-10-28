package com.merge.backend.domain.workplace.controller

import com.merge.backend.domain.user.service.UserService
import com.merge.backend.domain.workplace.dto.response.MembershipHistoryListResponse
import com.merge.backend.domain.workplace.dto.response.MembershipHistoryResponse
import com.merge.backend.domain.workplace.service.MembershipHistoryService
import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.exception.GlobalErrorCode
import com.merge.backend.global.rq.Rq
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/me/memberships")
class MembershipHistoryController(
    private val membershipHistoryService: MembershipHistoryService,
    private val userService: UserService,
    private val rq: Rq
) {
    @GetMapping
    fun getMyMemberships(
        @RequestParam("status") status: String
    ): ResponseEntity<ApiResponse<MembershipHistoryListResponse>> {
        if (status != "ENDED") {
            throw BusinessException(GlobalErrorCode.INVALID_REQUEST_PARAMETER)
        }

        val user = userService.getById(rq.actorId)
        val memberships = membershipHistoryService.getMyEndedMemberships(user)
            .map(MembershipHistoryResponse::from)
        val response = MembershipHistoryListResponse(memberships)

        return ResponseEntity.ok(
            ApiResponse.success("200", "종료된 소속 이력을 조회했습니다.", response)
        )
    }
}
