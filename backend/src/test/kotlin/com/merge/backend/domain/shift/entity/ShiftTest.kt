package com.merge.backend.domain.shift.entity

import com.merge.backend.domain.shift.entity.ShiftStatus.CANCELLED
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.global.exception.BusinessException
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ShiftTest {

    @Test
    fun 취소하면_취소_메타데이터를_함께_저장한다() {
        val schedule = mock<Schedule>()
        val assignedMember = mock<WorkplaceMember>()
        val actorMember = mock<WorkplaceMember>()
        val cancelledAt = LocalDateTime.of(2026, 10, 28, 15, 0)
        val shift = Shift(
            schedule,
            assignedMember,
            cancelledAt.plusDays(1),
            cancelledAt.plusDays(1).plusHours(8),
            ShiftStatus.SCHEDULED
        )

        shift.cancel(
            ShiftCancellationReason.MANAGER_CANCELLED,
            cancelledAt,
            actorMember
        )

        assertThat(shift.status).isEqualTo(CANCELLED)
        assertThat(shift.cancellationReason).isEqualTo(ShiftCancellationReason.MANAGER_CANCELLED)
        assertThat(shift.cancelledAt).isEqualTo(cancelledAt)
        assertThat(shift.cancelledBy).isSameAs(actorMember)
        assertThat(shift.member).isSameAs(assignedMember)
    }

    @Test
    fun 이미_취소된_Shift는_다시_취소할_수_없고_기존_취소_정보를_유지한다() {
        val schedule = mock<Schedule>()
        val assignedMember = mock<WorkplaceMember>()
        val firstActorMember = mock<WorkplaceMember>()
        val secondActorMember = mock<WorkplaceMember>()
        val firstCancelledAt = LocalDateTime.of(2026, 10, 28, 15, 0)
        val shift = Shift(
            schedule,
            assignedMember,
            firstCancelledAt.plusDays(1),
            firstCancelledAt.plusDays(1).plusHours(8),
            ShiftStatus.SCHEDULED
        )
        shift.cancel(
            ShiftCancellationReason.MANAGER_CANCELLED,
            firstCancelledAt,
            firstActorMember
        )

        assertThatThrownBy {
            shift.cancel(
                ShiftCancellationReason.MEMBER_LEFT,
                firstCancelledAt.plusHours(1),
                secondActorMember
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)

        assertThat(shift.status).isEqualTo(CANCELLED)
        assertThat(shift.cancellationReason).isEqualTo(ShiftCancellationReason.MANAGER_CANCELLED)
        assertThat(shift.cancelledAt).isEqualTo(firstCancelledAt)
        assertThat(shift.cancelledBy).isSameAs(firstActorMember)
    }

    @Test
    fun SCHEDULED가_아닌_Shift는_취소할_수_없다() {
        val schedule = mock<Schedule>()
        val assignedMember = mock<WorkplaceMember>()
        val actorMember = mock<WorkplaceMember>()
        val cancelledAt = LocalDateTime.of(2026, 10, 28, 15, 0)
        val shift = Shift(
            schedule,
            assignedMember,
            cancelledAt.plusDays(1),
            cancelledAt.plusDays(1).plusHours(8),
            CANCELLED
        )

        assertThatThrownBy {
            shift.cancel(ShiftCancellationReason.MANAGER_CANCELLED, cancelledAt, actorMember)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)

        assertThat(shift.status).isEqualTo(CANCELLED)
        assertThat(shift.cancellationReason).isNull()
        assertThat(shift.cancelledAt).isNull()
        assertThat(shift.cancelledBy).isNull()
    }

    @Test
    fun 시작_시각과_같거나_지난_시각에는_Shift를_취소할_수_없다() {
        val schedule = mock<Schedule>()
        val assignedMember = mock<WorkplaceMember>()
        val actorMember = mock<WorkplaceMember>()
        val startAt = LocalDateTime.of(2026, 10, 29, 9, 0)
        val shift = Shift(
            schedule,
            assignedMember,
            startAt,
            startAt.plusHours(8),
            ShiftStatus.SCHEDULED
        )

        assertThatThrownBy {
            shift.cancel(ShiftCancellationReason.MANAGER_CANCELLED, startAt, actorMember)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ShiftErrorCode.PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED)

        assertThat(shift.status).isEqualTo(ShiftStatus.SCHEDULED)
        assertThat(shift.cancellationReason).isNull()
        assertThat(shift.cancelledAt).isNull()
        assertThat(shift.cancelledBy).isNull()
    }
}
