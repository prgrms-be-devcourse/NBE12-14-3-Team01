package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.dto.request.LeaveConfirmRequest
import com.merge.backend.domain.workplace.dto.response.LeaveConfirmResponse
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.global.exception.BusinessException
import jakarta.validation.Validation
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.module.kotlin.jacksonObjectMapper

class LeaveConfirmDtoTest {
    private val now = LocalDateTime.of(2026, 10, 29, 12, 0)

    @Test
    fun requestDefaultsToNoInviteRotationAndEmptyAcknowledgements() {
        val request = jacksonObjectMapper().readValue(
            """{"previewContext":"test-context","decisions":[]}""", LeaveConfirmRequest::class.java)
        assertThat(request.regenerateInviteCode).isFalse()
        assertThat(request.acknowledgedUnavailableConflicts).isEmpty()
    }

    @Test
    fun validatesRequiredAndNestedDecisionFields() {
        Validation.buildDefaultValidatorFactory().use { factory ->
            val validator = factory.validator
            assertThat(validator.validate(LeaveConfirmRequest(null, null))).hasSize(2)
            val invalid = LeaveConfirmRequest("context", listOf(
                LeaveConfirmRequest.Decision(null, null, null, "UNKNOWN")))
            assertThat(validator.validate(invalid)).hasSize(4)
            assertThat(validator.validate(LeaveConfirmRequest("context", emptyList()))).isEmpty()
        }
    }

    @Test
    fun responseCountsDistinctRequestsByExclusiveReasonAndOmitsAbsentInviteCode() {
        val counts = LeaveConfirmResponse.ClosedRequests.from(setOf(1, 2, 2), setOf(3))
        assertThat(counts.total).isEqualTo(3)
        assertThat(counts.byReason).containsEntry("SHIFT_CHANGED", 2).containsEntry("CANDIDATE_INELIGIBLE", 1)
        assertThrows<IllegalArgumentException> { LeaveConfirmResponse.ClosedRequests.from(setOf(1), setOf(1)) }
        val response = LeaveConfirmResponse(now, emptyList(), emptyList(), emptyList(), counts, 0, null)
        val mapper = jacksonObjectMapper()
        val json = mapper.readTree(mapper.writeValueAsString(response))
        assertThat(json.has("newInviteCode")).isFalse()
        assertThat(json["closedRequests"]["total"].asInt()).isEqualTo(3)
        assertThat(mapper.readTree(mapper.writeValueAsString(response.copy(newInviteCode = "A1B2C3D4")))
            ["newInviteCode"].asText()).isEqualTo("A1B2C3D4")
    }

    @Test
    fun membershipTerminationPreservesOriginalEndAndRejectsManager() {
        val workplace = Workplace("매장", "A1B2C3D4")
        val user = User("leave-entity@test.com", "pw", "직원")
        val employee = WorkplaceMember(workplace, user, WorkplaceRole.EMPLOYEE, now.minusDays(1))
        employee.endMembership(now)
        val repeated = assertThrows<BusinessException> { employee.endMembership(now.plusHours(1)) }
        assertThat(repeated.errorCode).isEqualTo(WorkplaceErrorCode.MEMBERSHIP_ALREADY_ENDED)
        assertThat(employee.leftAt).isEqualTo(now)
        val manager = WorkplaceMember(workplace, user, WorkplaceRole.MANAGER, now.minusDays(1))
        val forbidden = assertThrows<BusinessException> { manager.endMembership(now) }
        assertThat(forbidden.errorCode).isEqualTo(WorkplaceErrorCode.MANAGER_CANNOT_LEAVE)
        assertThat(manager.leftAt).isNull()
    }
}
