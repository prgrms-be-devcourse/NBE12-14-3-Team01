package com.merge.backend.domain.shift.entity

import com.merge.backend.domain.shift.entity.ShiftStatus.CANCELLED
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
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
}
