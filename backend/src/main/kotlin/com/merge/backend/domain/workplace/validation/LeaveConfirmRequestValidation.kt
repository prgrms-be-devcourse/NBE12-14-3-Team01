package com.merge.backend.domain.workplace.validation

import com.merge.backend.domain.workplace.dto.request.LeaveConfirmRequest
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [LeaveConfirmRequestValidator::class])
annotation class ValidLeaveConfirmRequest(
    val message: String = "퇴사 확정 요청의 필수 항목을 확인해 주세요.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

/** nullable DTO의 목록 원소 및 REASSIGN의 조건부 필수 값만 검증한다. 미정 정책은 다루지 않는다. */
class LeaveConfirmRequestValidator : ConstraintValidator<ValidLeaveConfirmRequest, LeaveConfirmRequest> {
    override fun isValid(value: LeaveConfirmRequest?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        var valid = true
        context.disableDefaultConstraintViolation()
        value.decisions?.forEachIndexed { index, decision ->
            if (decision == null) {
                context.buildConstraintViolationWithTemplate("근무별 결정은 null일 수 없습니다.")
                    .addPropertyNode("decisions").addBeanNode().inIterable().atIndex(index).addConstraintViolation()
                valid = false
            } else if (decision.action == "REASSIGN" && decision.targetMemberId == null) {
                context.buildConstraintViolationWithTemplate("인계 대상 소속이 필요합니다.")
                    .addPropertyNode("decisions").addPropertyNode("targetMemberId")
                    .inIterable().atIndex(index).addConstraintViolation()
                valid = false
            }
        }
        value.acknowledgedUnavailableConflicts.forEachIndexed { index, acknowledgement ->
            if (acknowledgement == null) {
                context.buildConstraintViolationWithTemplate("충돌 확인 항목은 null일 수 없습니다.")
                    .addPropertyNode("acknowledgedUnavailableConflicts").addBeanNode()
                    .inIterable().atIndex(index).addConstraintViolation()
                valid = false
            } else {
                acknowledgement.overlaps?.forEachIndexed { overlapIndex, overlap ->
                    if (overlap == null) {
                        context.buildConstraintViolationWithTemplate("확인 구간은 null일 수 없습니다.")
                            .addPropertyNode("acknowledgedUnavailableConflicts").addPropertyNode("overlaps")
                            .inIterable().atIndex(index).addBeanNode().inIterable().atIndex(overlapIndex)
                            .addConstraintViolation()
                        valid = false
                    }
                }
            }
        }
        return valid
    }
}
