package com.merge.backend.domain.workplace.controller

import com.merge.backend.domain.user.service.UserService
import com.merge.backend.domain.workplace.dto.request.WorkplaceCreateRequest
import com.merge.backend.domain.workplace.dto.request.WorkplaceJoinRequest
import com.merge.backend.domain.workplace.dto.response.MyWorkplaceResponse
import com.merge.backend.domain.workplace.dto.response.WorkplaceCreateResponse
import com.merge.backend.domain.workplace.dto.response.WorkplaceInviteCodeResponse
import com.merge.backend.domain.workplace.dto.response.WorkplaceJoinResponse
import com.merge.backend.domain.workplace.dto.response.WorkplaceMemberResponse
import com.merge.backend.domain.workplace.service.WorkplaceService
import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.rq.Rq
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/workplaces")
class WorkplaceController(
    private val workplaceService: WorkplaceService,
    private val userService: UserService,
    private val rq: Rq
) {
    @PostMapping
    fun createWorkplace(
        @Valid @RequestBody request: WorkplaceCreateRequest
    ): ResponseEntity<ApiResponse<WorkplaceCreateResponse>> {
        val userId = rq.actorId
        val user = userService.getById(userId)
        val member = workplaceService.createWorkplace(request.name, user)
        val response = WorkplaceCreateResponse.from(member)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success("201", "Workplace가 생성되었습니다.", response)
        )
    }

    @PostMapping("/join")
    fun joinWorkplace(
        @Valid @RequestBody request: WorkplaceJoinRequest
    ): ResponseEntity<ApiResponse<WorkplaceJoinResponse>> {
        val userId = rq.actorId
        val user = userService.getById(userId)
        val member = workplaceService.joinWorkplace(request.inviteCode, user)
        val response = WorkplaceJoinResponse.from(member)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success("201", "Workplace 참여가 완료되었습니다.", response)
        )
    }

    @GetMapping
    fun getMyWorkplaces(): ResponseEntity<ApiResponse<List<MyWorkplaceResponse>>> {
        val userId = rq.actorId
        val user = userService.getById(userId)
        val response = workplaceService.getMyWorkplaces(user).map(MyWorkplaceResponse::from)
        return ResponseEntity.ok(
            ApiResponse.success("200", "소속 Workplace 목록을 조회했습니다.", response)
        )
    }

    @GetMapping("/{workplaceId}/members")
    fun getWorkplaceMembers(
        @PathVariable("workplaceId") workplaceId: Long
    ): ResponseEntity<ApiResponse<List<WorkplaceMemberResponse>>> {
        val actorUserId = rq.actorId
        val response = workplaceService.getWorkplaceMembers(workplaceId, actorUserId)
            .map(WorkplaceMemberResponse::from)
        return ResponseEntity.ok(
            ApiResponse.success("200", "Workplace 구성원 목록을 조회했습니다.", response)
        )
    }

    @GetMapping("/{workplaceId}/invite-code")
    fun getInviteCode(
        @PathVariable("workplaceId") workplaceId: Long
    ): ResponseEntity<ApiResponse<WorkplaceInviteCodeResponse>> {
        val actorUserId = rq.actorId
        val workplace = workplaceService.getInviteCode(workplaceId, actorUserId)
        val response = WorkplaceInviteCodeResponse.from(workplace)
        return ResponseEntity.ok(
            ApiResponse.success("200", "Workplace 초대 코드를 조회했습니다.", response)
        )
    }
}
