package com.merge.backend.domain.substitute.dto

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.substitute.entity.RequestCloseReason
import com.merge.backend.domain.substitute.entity.RequestStatus
import com.merge.backend.domain.substitute.entity.SubstituteRequest
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.LocalDateTime

class SentSubstituteRequestResponseTest {

    @Test
    @DisplayName("OPEN 상태이고 아직 근무 시작 전이면 그대로 OPEN으로 변환된다")
    fun test1() {
        val request = createRequest(RequestStatus.OPEN)
        val now = SHIFT_START.minusHours(1)

        val response = SentSubstituteRequestResponse.from(request, now)

        assertThat(response.status).isEqualTo(RequestStatus.OPEN)
        assertThat(response.closeReason).isNull()
        assertThat(response.closedAt).isNull()
    }

    @Test
    @DisplayName("OPEN 상태인데 근무 시작 시각이 지났으면 CLOSED/EXPIRED로 변환된다")
    fun test2() {
        val request = createRequest(RequestStatus.OPEN)
        val now = SHIFT_START.plusMinutes(1)

        val response = SentSubstituteRequestResponse.from(request, now)

        assertThat(response.status).isEqualTo(RequestStatus.CLOSED)
        assertThat(response.closeReason).isEqualTo(RequestCloseReason.EXPIRED)
        assertThat(response.closedAt).isEqualTo(now)
    }

    @Test
    @DisplayName("ACCEPTED 상태에서 now == startAt(경계값)이면 만료로 처리된다")
    fun test3() {
        val request = createRequest(RequestStatus.ACCEPTED)
        val now = SHIFT_START

        val response = SentSubstituteRequestResponse.from(request, now)

        assertThat(response.status).isEqualTo(RequestStatus.CLOSED)
        assertThat(response.closeReason).isEqualTo(RequestCloseReason.EXPIRED)
    }

    @Test
    @DisplayName("APPROVED 상태는 근무 시작 시각이 지나도 그대로 유지된다")
    fun test4() {
        val approvedAt = SHIFT_START.minusMinutes(10)
        val request = createRequest(
            RequestStatus.APPROVED,
            approvedAt = approvedAt,
            closedAt = approvedAt,
        )
        val now = SHIFT_START.plusHours(1)

        val response = SentSubstituteRequestResponse.from(request, now)

        assertThat(response.status).isEqualTo(RequestStatus.APPROVED)
        assertThat(response.closeReason).isNull()
        assertThat(response.approvedAt).isEqualTo(approvedAt)
        assertThat(response.closedAt).isEqualTo(approvedAt)
    }

    @Test
    @DisplayName("CLOSED 상태는 저장된 closeReason·closedAt이 그대로 유지된다")
    fun test5() {
        val closedAt = SHIFT_START.minusMinutes(5)
        val request = createRequest(
            RequestStatus.CLOSED,
            closeReason = RequestCloseReason.MANAGER_CLOSED,
            closedAt = closedAt,
        )
        val now = SHIFT_START.plusHours(1)

        val response = SentSubstituteRequestResponse.from(request, now)

        assertThat(response.status).isEqualTo(RequestStatus.CLOSED)
        assertThat(response.closeReason).isEqualTo(RequestCloseReason.MANAGER_CLOSED)
        assertThat(response.closedAt).isEqualTo(closedAt)
    }

    @Test
    @DisplayName("requestId·shiftId·workplace 정보가 올바르게 매핑된다")
    fun test6() {
        val request = createRequest(RequestStatus.OPEN)
        val now = SHIFT_START.minusHours(1)

        val response = SentSubstituteRequestResponse.from(request, now)

        assertThat(response.requestId).isEqualTo(REQUEST_ID)
        assertThat(response.shiftId).isEqualTo(SHIFT_ID)
        assertThat(response.workplaceName).isEqualTo("SWITCH 카페")
        assertThat(response.startAt).isEqualTo(SHIFT_START)
        assertThat(response.endAt).isEqualTo(SHIFT_END)
    }

    private fun createRequest(
        status: RequestStatus,
        closeReason: RequestCloseReason? = null,
        approvedAt: LocalDateTime? = null,
        closedAt: LocalDateTime? = null,
    ): SubstituteRequest {
        val workplace = Workplace("SWITCH 카페", "INVITE01")
        val schedule = Schedule(workplace, LocalDate.now()).apply {
            publish(LocalDateTime.now())
        }

        val member = WorkplaceMember(
            workplace,
            User(1L, "owner@example.com", "담당자"),
            WorkplaceRole.EMPLOYEE,
            LocalDateTime.now().minusDays(30),
            null,
        )
        val shift = Shift(schedule, member, SHIFT_START, SHIFT_END, ShiftStatus.SCHEDULED).apply {
            ReflectionTestUtils.setField(this, "id", SHIFT_ID)
        }

        return SubstituteRequest(shift, member, status).apply {
            ReflectionTestUtils.setField(this, "id", REQUEST_ID)
            ReflectionTestUtils.setField(this, "closeReason", closeReason)
            ReflectionTestUtils.setField(this, "approvedAt", approvedAt)
            ReflectionTestUtils.setField(this, "closedAt", closedAt)
            ReflectionTestUtils.setField(this, "createDate", LocalDateTime.now())
        }
    }

    companion object {
        private val SHIFT_START: LocalDateTime = LocalDateTime.of(2026, 9, 25, 9, 0)
        private val SHIFT_END: LocalDateTime = SHIFT_START.plusHours(4)
        private const val REQUEST_ID = 77L
        private const val SHIFT_ID = 10L
    }
}
