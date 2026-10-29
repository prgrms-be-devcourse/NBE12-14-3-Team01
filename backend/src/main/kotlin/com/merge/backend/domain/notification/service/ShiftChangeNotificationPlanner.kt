package com.merge.backend.domain.notification.service

import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.shift.event.PublishedShiftChangeKind
import com.merge.backend.domain.substitute.service.ShiftChangeClosedRequest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// 받는 사람 한 명에게 저장할 알림 한 건
data class PlannedNotification(
    val recipientMemberId: Long,
    val type: NotificationType,
    val message: String,
)

// 공개 후 근무 변경의 알림 계획 (NOTIFICATION-TYPES §3-3·3-4, §6-2·6-3). DB를 보지 않는 순수 계산
// 순서 (§6-3 S3-B): ① 역할별 받는 사람 집합 → ② 역할마다 Type → ③ 받는 사람별로 합치기
object ShiftChangeNotificationPlanner {

    fun plan(
        kind: PublishedShiftChangeKind,
        shiftStartAt: LocalDateTime,
        shiftEndAt: LocalDateTime,
        previousMemberId: Long?,
        newMemberId: Long?,
        actorMemberId: Long?,
        closedRequests: List<ShiftChangeClosedRequest>,
        currentManagerMemberIds: Collection<Long>,
        currentMemberIds: Set<Long>, // 현재 소속만 받는다 (R1). 이 집합에 없는 소속은 모든 역할에서 빠진다
    ): List<PlannedNotification> {
        val shiftLabel = shiftLabel(shiftStartAt, shiftEndAt)

        // ① 역할별 집합. 실행한 관리자는 관리자 집합에서만 뺀다 (§6-3-2)
        val shiftNotices = buildMap {
            when (kind) {
                PublishedShiftChangeKind.ADDED -> newMemberId?.let { put(it, NotificationType.SHIFT_ASSIGNED) }
                PublishedShiftChangeKind.CANCELLED -> previousMemberId?.let { put(it, NotificationType.SHIFT_CANCELLED) }
                PublishedShiftChangeKind.REASSIGNED -> {
                    previousMemberId?.let { put(it, NotificationType.SHIFT_REASSIGNED_AWAY) }
                    newMemberId?.let { put(it, NotificationType.SHIFT_ASSIGNED) }
                }
            }
        }
        val requesterIds = closedRequests.map { it.requesterMemberId }.toSet()
        val accepterIds = closedRequests.mapNotNull { it.acceptedMemberId }.toSet()
        val managerIds = if (closedRequests.isEmpty()) emptySet() else currentManagerMemberIds.toSet() - setOfNotNull(actorMemberId)

        val recipients = (shiftNotices.keys + requesterIds + accepterIds + managerIds)
            .filter { it in currentMemberIds }
            .distinct()

        // ②·③ 근무 알림을 받는 사람은 대타 종료를 문구에 덧붙여 한 건으로 합치고, 나머지는 요청마다 종료 알림 한 건
        return recipients.flatMap { recipientId ->
            val shiftType = shiftNotices[recipientId]
            if (shiftType != null) {
                listOf(PlannedNotification(recipientId, shiftType, shiftMessage(shiftType, shiftLabel, recipientId, requesterIds, accepterIds, managerIds)))
            } else {
                closedRequests
                    .filter { recipientId == it.requesterMemberId || recipientId == it.acceptedMemberId || recipientId in managerIds }
                    .map { PlannedNotification(recipientId, NotificationType.SUBSTITUTE_SHIFT_CHANGED, substituteShiftChangedMessage(kind, shiftLabel)) }
            }
        }
    }

    // 이름 길이 제한이 없어(@NotBlank만) message Column(255) 초과로 저장이 실패하지 않게 이름을 줄인다
    fun memberJoinedMessage(name: String, rejoined: Boolean): String {
        val shownName = if (name.length > MAX_NAME_LENGTH) name.take(MAX_NAME_LENGTH) + "…" else name
        return if (rejoined) "${shownName}님이 근무지에 다시 가입했습니다." else "${shownName}님이 근무지에 가입했습니다."
    }

    fun shiftLabel(startAt: LocalDateTime, endAt: LocalDateTime): String =
        "${startAt.format(DATE_TIME)}~${endAt.format(TIME)}"

    private fun shiftMessage(
        type: NotificationType,
        shiftLabel: String,
        recipientId: Long,
        requesterIds: Set<Long>,
        accepterIds: Set<Long>,
        managerIds: Set<Long>,
    ): String {
        val base = when (type) {
            NotificationType.SHIFT_ASSIGNED -> "$shiftLabel 근무가 배정되었습니다."
            NotificationType.SHIFT_CANCELLED -> "$shiftLabel 근무가 취소되었습니다."
            NotificationType.SHIFT_REASSIGNED_AWAY -> "$shiftLabel 근무가 다른 직원에게 넘어갔습니다."
            else -> error("근무 알림 Type이 아닙니다: $type")
        }
        // 그 사람의 실제 역할에 맞는 문구만 덧붙인다 (§3-4 문구 작성 주의). PENDING 후보는 역할 집합에 없으므로 덧붙이지 않는다
        val suffix = when (recipientId) {
            in accepterIds -> " 수락했던 대타 요청은 종료되었습니다."
            in requesterIds, in managerIds -> " 진행 중이던 대타 요청도 종료되었습니다."
            else -> ""
        }
        return base + suffix
    }

    private fun substituteShiftChangedMessage(kind: PublishedShiftChangeKind, shiftLabel: String): String =
        when (kind) {
            PublishedShiftChangeKind.CANCELLED -> "$shiftLabel 근무가 취소되어 대타 요청이 종료되었습니다."
            else -> "$shiftLabel 근무의 담당자가 바뀌어 대타 요청이 종료되었습니다."
        }

    private const val MAX_NAME_LENGTH = 50
    private val DATE_TIME = DateTimeFormatter.ofPattern("M월 d일(E) HH:mm", Locale.KOREAN)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
}
