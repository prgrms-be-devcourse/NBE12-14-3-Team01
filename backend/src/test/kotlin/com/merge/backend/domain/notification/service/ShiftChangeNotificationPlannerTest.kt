package com.merge.backend.domain.notification.service

import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.shift.event.PublishedShiftChangeKind
import com.merge.backend.domain.substitute.service.ShiftChangeClosedRequest
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

// 공개 후 근무 변경 알림의 받는 사람·합치기 규칙 (NOTIFICATION-TYPES §3-3·3-4, §6-2·6-3)
class ShiftChangeNotificationPlannerTest {

    @Test
    @DisplayName("문구의 근무 표시는 '10월 30일(금) 09:00~13:00' 형식이다")
    fun shiftLabelFormat() {
        assertThat(ShiftChangeNotificationPlanner.shiftLabel(START, END)).isEqualTo("10월 30일(금) 09:00~13:00")
    }

    @Test
    @DisplayName("§3-4 표: A→C 인계, B가 수락한 A의 요청 → A는 합친 1건, B·C·다른 관리자는 각 1건, 실행 관리자 M과 PENDING 후보 D는 0건")
    fun reassignTableOfSection34() {
        val result = plan(
            kind = PublishedShiftChangeKind.REASSIGNED,
            previous = A,
            new = C,
            closed = listOf(ShiftChangeClosedRequest(REQUEST, A, B)),
            current = setOf(A, B, C, D, M, M2),
        )

        assertThat(result.map { it.recipientMemberId to it.type }).containsExactlyInAnyOrder(
            A to NotificationType.SHIFT_REASSIGNED_AWAY,
            C to NotificationType.SHIFT_ASSIGNED,
            B to NotificationType.SUBSTITUTE_SHIFT_CHANGED,
            M2 to NotificationType.SUBSTITUTE_SHIFT_CHANGED,
        )
        assertThat(messageOf(result, A))
            .isEqualTo("10월 30일(금) 09:00~13:00 근무가 다른 직원에게 넘어갔습니다. 진행 중이던 대타 요청도 종료되었습니다.")
        assertThat(messageOf(result, C)).isEqualTo("10월 30일(금) 09:00~13:00 근무가 배정되었습니다.")
        assertThat(messageOf(result, B)).isEqualTo("10월 30일(금) 09:00~13:00 근무의 담당자가 바뀌어 대타 요청이 종료되었습니다.")
    }

    @Test
    @DisplayName("새 담당 C가 그 요청의 ACCEPTED 후보였으면 SHIFT_ASSIGNED 1건에 '수락했던 대타 요청은 종료' 문구를 덧붙인다")
    fun newAssigneeWasAccepter() {
        val result = plan(
            kind = PublishedShiftChangeKind.REASSIGNED,
            previous = A,
            new = C,
            closed = listOf(ShiftChangeClosedRequest(REQUEST, A, C)),
        )

        assertThat(result.filter { it.recipientMemberId == C }).singleElement()
            .extracting { it.type to it.message }
            .isEqualTo(NotificationType.SHIFT_ASSIGNED to "10월 30일(금) 09:00~13:00 근무가 배정되었습니다. 수락했던 대타 요청은 종료되었습니다.")
    }

    @Test
    @DisplayName("새 담당 C가 PENDING 후보였으면(종료 목록의 역할 없음) SHIFT_ASSIGNED만, 종료 문구를 덧붙이지 않는다")
    fun newAssigneeWasPendingCandidate() {
        val result = plan(
            kind = PublishedShiftChangeKind.REASSIGNED,
            previous = A,
            new = C,
            closed = listOf(ShiftChangeClosedRequest(REQUEST, A, null)),
        )

        assertThat(messageOf(result, C)).isEqualTo("10월 30일(금) 09:00~13:00 근무가 배정되었습니다.")
    }

    @Test
    @DisplayName("취소: 원래 담당 A는 SHIFT_CANCELLED에 대타 종료를 합친 1건, 수락자 B는 SUBSTITUTE_SHIFT_CHANGED (취소 문구)")
    fun cancelMergesRequesterNotice() {
        val result = plan(
            kind = PublishedShiftChangeKind.CANCELLED,
            previous = A,
            closed = listOf(ShiftChangeClosedRequest(REQUEST, A, B)),
        )

        assertThat(result.map { it.recipientMemberId to it.type }).containsExactlyInAnyOrder(
            A to NotificationType.SHIFT_CANCELLED,
            B to NotificationType.SUBSTITUTE_SHIFT_CHANGED,
            M2 to NotificationType.SUBSTITUTE_SHIFT_CHANGED,
        )
        assertThat(messageOf(result, A)).isEqualTo("10월 30일(금) 09:00~13:00 근무가 취소되었습니다. 진행 중이던 대타 요청도 종료되었습니다.")
        assertThat(messageOf(result, B)).isEqualTo("10월 30일(금) 09:00~13:00 근무가 취소되어 대타 요청이 종료되었습니다.")
    }

    @Test
    @DisplayName("종료된 대타 요청이 없으면 관리자에게는 알림이 없고 근무 알림만 간다 (NT-T16 ②)")
    fun noClosedRequestNoManagerNotice() {
        val result = plan(kind = PublishedShiftChangeKind.CANCELLED, previous = A, closed = emptyList())

        assertThat(result.map { it.recipientMemberId to it.type }).containsExactly(A to NotificationType.SHIFT_CANCELLED)
        assertThat(messageOf(result, A)).isEqualTo("10월 30일(금) 09:00~13:00 근무가 취소되었습니다.")
    }

    @Test
    @DisplayName("근무 추가는 새 담당에게 SHIFT_ASSIGNED 1건만")
    fun addedShift() {
        val result = plan(kind = PublishedShiftChangeKind.ADDED, new = C, closed = emptyList())

        assertThat(result.map { it.recipientMemberId to it.type }).containsExactly(C to NotificationType.SHIFT_ASSIGNED)
    }

    @Test
    @DisplayName("실행 관리자는 관리자 수신에서만 빠지고, 자기가 요청자인 요청의 근무가 넘어가면 요청자로서 받는다 (§6-3-2)")
    fun actorExcludedOnlyFromManagerRole() {
        val result = plan(
            kind = PublishedShiftChangeKind.REASSIGNED,
            previous = M,
            new = C,
            closed = listOf(ShiftChangeClosedRequest(REQUEST, M, null)),
        )

        assertThat(result.filter { it.recipientMemberId == M }).singleElement()
            .extracting { it.type }
            .isEqualTo(NotificationType.SHIFT_REASSIGNED_AWAY)
        assertThat(result.filter { it.recipientMemberId == M2 }).singleElement()
            .extracting { it.type }
            .isEqualTo(NotificationType.SUBSTITUTE_SHIFT_CHANGED)
    }

    @Test
    @DisplayName("현재 소속이 아닌 사람은 어떤 역할이어도 받지 않는다 (R1, NT-T2)")
    fun nonCurrentMembersExcluded() {
        val result = plan(
            kind = PublishedShiftChangeKind.REASSIGNED,
            previous = A,
            new = C,
            closed = listOf(ShiftChangeClosedRequest(REQUEST, A, B)),
            current = setOf(C, M, M2),
        )

        assertThat(result.map { it.recipientMemberId }).containsExactlyInAnyOrder(C, M2)
    }

    @Test
    @DisplayName("문구에 새 담당자 정보가 없다 (NT-T4): 넘어감 알림은 근무 시간과 사실만 담는다")
    fun reassignedAwayHasNoNewAssigneeInfo() {
        val result = plan(kind = PublishedShiftChangeKind.REASSIGNED, previous = A, new = C, closed = emptyList())

        assertThat(messageOf(result, A)).isEqualTo("10월 30일(금) 09:00~13:00 근무가 다른 직원에게 넘어갔습니다.")
    }

    @Test
    @DisplayName("가입 문구는 신규·재가입을 구분하고, 긴 이름은 50자로 줄여 message Column(255)을 넘지 않는다")
    fun memberJoinedMessage() {
        assertThat(ShiftChangeNotificationPlanner.memberJoinedMessage("홍길동", false)).isEqualTo("홍길동님이 근무지에 가입했습니다.")
        assertThat(ShiftChangeNotificationPlanner.memberJoinedMessage("홍길동", true)).isEqualTo("홍길동님이 근무지에 다시 가입했습니다.")

        val longName = "가".repeat(300)
        val message = ShiftChangeNotificationPlanner.memberJoinedMessage(longName, true)
        assertThat(message).startsWith("가".repeat(50) + "…님이")
        assertThat(message.length).isLessThanOrEqualTo(255)
    }

    private fun plan(
        kind: PublishedShiftChangeKind,
        previous: Long? = null,
        new: Long? = null,
        closed: List<ShiftChangeClosedRequest>,
        current: Set<Long> = setOf(A, B, C, D, M, M2),
    ): List<PlannedNotification> =
        ShiftChangeNotificationPlanner.plan(
            kind = kind,
            shiftStartAt = START,
            shiftEndAt = END,
            previousMemberId = previous,
            newMemberId = new,
            actorMemberId = M,
            closedRequests = closed,
            currentManagerMemberIds = listOf(M, M2),
            currentMemberIds = current,
        )

    private fun messageOf(result: List<PlannedNotification>, memberId: Long): String =
        result.single { it.recipientMemberId == memberId }.message

    companion object {
        private val START = LocalDateTime.of(2026, 10, 30, 9, 0)
        private val END = LocalDateTime.of(2026, 10, 30, 13, 0)
        private const val REQUEST = 100L

        // A 원래 담당·요청자, B 수락 후보, C 새 담당, D 대기 후보, M 실행 관리자, M2 다른 관리자
        private const val A = 1L
        private const val B = 2L
        private const val C = 3L
        private const val D = 4L
        private const val M = 10L
        private const val M2 = 11L
    }
}
