package com.merge.backend.domain.notification.event

import com.merge.backend.domain.notification.service.PlannedNotification

// 업무 Transaction 안에서 받는 사람·문구까지 정한 알림 묶음. Commit 후 저장한다
data class PlannedNotificationsEvent(
    val source: String, // 로그용 사건 설명
    val notifications: List<PlannedNotification>,
)
