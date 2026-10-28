package com.merge.backend.domain.substitute.entity

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.substitute.exception.SubstituteRequestErrorCode
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.global.exception.BusinessException
import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.test.util.ReflectionTestUtils

class SubstituteRequestTest {

    @ParameterizedTest
    @EnumSource(value = RequestStatus::class, names = ["ACCEPTED", "APPROVED", "CLOSED"])
    @DisplayName("OPEN이 아닌 요청은 수락할 수 없고 상태가 바뀌지 않는다")
    fun accept_NotOpen_ThrowsAndKeepsState(status: RequestStatus) {
        // given
        val request = createRequest(status)

        // when & then
        assertThatThrownBy { request.validateOpening() }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(SubstituteRequestErrorCode.VALIDATE_STATUS_CLOSED)
        assertThatThrownBy { request.accept() }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(SubstituteRequestErrorCode.VALIDATE_STATUS_CLOSED)

        assertThat(SubstituteRequestErrorCode.VALIDATE_STATUS_CLOSED.getCode()).isEqualTo("SUB-003")
        assertThat(request.status).isEqualTo(status)
        assertThat(request.closeReason).isEqualTo(CLOSE_REASON)
        assertThat(request.approvedAt).isEqualTo(APPROVED_AT)
        assertThat(request.closedAt).isEqualTo(CLOSED_AT)
    }

    private fun createRequest(status: RequestStatus): SubstituteRequest {
        val workplace = Workplace("SWITCH 카페", "INVITE01")
        val schedule = Schedule(workplace, SHIFT_START.toLocalDate()).apply {
            publish(SHIFT_START.minusDays(3))
        }
        val member = WorkplaceMember(
            workplace,
            User(1L, "requester@example.com", "요청자"),
            WorkplaceRole.EMPLOYEE,
            SHIFT_START.minusDays(30),
        )
        val shift = Shift(schedule, member, SHIFT_START, SHIFT_START.plusHours(4), ShiftStatus.SCHEDULED)

        // 상태 외 Field도 그대로인지 보려고 값을 채워 둔다
        return SubstituteRequest(shift, member, status).apply {
            ReflectionTestUtils.setField(this, "closeReason", CLOSE_REASON)
            ReflectionTestUtils.setField(this, "approvedAt", APPROVED_AT)
            ReflectionTestUtils.setField(this, "closedAt", CLOSED_AT)
        }
    }

    companion object {
        private val SHIFT_START: LocalDateTime = LocalDate.of(2026, 9, 25).atTime(9, 0)
        private val CLOSE_REASON = RequestCloseReason.MANAGER_CLOSED
        private val APPROVED_AT: LocalDateTime = SHIFT_START.minusDays(1)
        private val CLOSED_AT: LocalDateTime = SHIFT_START.minusDays(1)
    }
}
