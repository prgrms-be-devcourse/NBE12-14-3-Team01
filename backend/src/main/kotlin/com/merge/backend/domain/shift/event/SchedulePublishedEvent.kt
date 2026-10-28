package com.merge.backend.domain.shift.event

class SchedulePublishedEvent(
    val scheduleId: Long?,
    recipientMemberIds: List<Long>,
) {
    private val recipientMemberIdsSnapshot: List<Long> =
        java.util.List.copyOf(recipientMemberIds)

    val recipientMemberIds: List<Long>
        get() = recipientMemberIdsSnapshot

    fun scheduleId(): Long? = scheduleId

    fun recipientMemberIds(): List<Long> = recipientMemberIdsSnapshot

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is SchedulePublishedEvent &&
                scheduleId == other.scheduleId &&
                recipientMemberIdsSnapshot == other.recipientMemberIdsSnapshot
            )

    override fun hashCode(): Int {
        var result = scheduleId?.hashCode() ?: 0
        result = 31 * result + recipientMemberIdsSnapshot.hashCode()
        return result
    }

    override fun toString(): String =
        "SchedulePublishedEvent[scheduleId=$scheduleId, recipientMemberIds=$recipientMemberIdsSnapshot]"
}
