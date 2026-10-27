package com.merge.backend.domain.notification.controller

import com.merge.backend.domain.notification.dto.NotificationReadResponse
import com.merge.backend.domain.notification.dto.NotificationResponse
import com.merge.backend.domain.notification.service.NotificationService
import com.merge.backend.global.dto.ApiResponse
import com.merge.backend.global.rq.Rq
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/notifications")
class NotificationController(
    private val notificationService: NotificationService,
    private val rq: Rq,
) {
    @PatchMapping("/{notificationId}/read")
    fun markAsRead(
        @PathVariable("notificationId") notificationId: Long,
    ): ResponseEntity<ApiResponse<NotificationReadResponse>> {
        val response = notificationService.markAsRead(
            rq.actorId,
            notificationId,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                "200",
                "알림을 읽음 처리했습니다.",
                response,
            ),
        )
    }

    @GetMapping
    fun getNotifications(): ResponseEntity<ApiResponse<List<NotificationResponse>>> {
        val response = notificationService.getNotifications(rq.actorId)

        return ResponseEntity.ok(
            ApiResponse.success(
                "200",
                "알림 목록을 조회했습니다.",
                response,
            ),
        )
    }
}
