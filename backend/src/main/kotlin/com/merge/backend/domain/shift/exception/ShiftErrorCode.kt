package com.merge.backend.domain.shift.exception

import com.merge.backend.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ShiftErrorCode(
    private val httpStatus: HttpStatus,
    private val code: String,
    private val message: String
) : ErrorCode {
    INVALID_INPUT_VALUE(
        HttpStatus.BAD_REQUEST,
        "SFT-001",
        "입력값이 올바르지 않습니다."
    ),
    INVALID_WORKPLACE_VALUE(
        HttpStatus.BAD_REQUEST,
        "SFT-002",
        "스케줄이 해당 workplace 소속이 아닙니다."
    ),
    INVALID_SHIFT_VALUE(
        HttpStatus.BAD_REQUEST,
        "SFT-003",
        "업무가 해당 스케줄 소속이 아닙니다."
    ),
    INVALID_WORKPLACE_MEMBER_VALUE(
        HttpStatus.BAD_REQUEST,
        "SFT-004",
        "멤버가 해당 workplace 소속이 아닙니다."
    ),
    INVALID_STATUS_VALUE(
        HttpStatus.BAD_REQUEST,
        "SFT-005",
        "잘못된 상태입니다."
    ),
    INVALID_TIME_VALUE(
        HttpStatus.BAD_REQUEST,
        "SFT-006",
        "시간 형식이나 값이 올바르지 않습니다."
    ),
    INVALID_BEFORE_AFTER_VALUE(
        HttpStatus.BAD_REQUEST,
        "SFT-007",
        "근무 시작 시간은 종료 시간보다 이전이어야 합니다."
    ),
    INVALID_SHIFT_INSCHEDULE(
        HttpStatus.BAD_REQUEST,
        "SFT-008",
        "설정한 근무시간이 스케줄 주간에서 벗어나있습니다."
    ),
    DUPLICATE_LOCAL_SCHEDULE_TIME(
        HttpStatus.BAD_REQUEST,
        "SFT-009",
        "동일인물의 근무는 중복될 수 없습니다."
    ),
    DUPLICATE_GLOBAL_SCHEDULE_TIME(
        HttpStatus.BAD_REQUEST,
        "SFT-010",
        "다른 근무지에서 중복되는 근무가 있습니다."
    ),
    INVALID_SHIFT_SAME_DAY(
        HttpStatus.BAD_REQUEST,
        "SFT-011",
        "근무 시작일과 종료일은 같아야 합니다."
    ),
    NOT_PUBLISHED_SHIFT(
        HttpStatus.NOT_FOUND,
        "SFT-012",
        "해당 근무는 확정되지 않았습니다."
    ),
    IS_CANCELED_SHIFT(
        HttpStatus.NOT_FOUND,
        "SFT-013",
        "해당 근무는 취소되었습니다."
    ),
    FORBIDDEN_ACCESS(
        HttpStatus.FORBIDDEN,
        "SFT-014",
        "잘못된 권한입니다."
    ),
    UNAVAILABLE_TIME_CONFLICT(
        HttpStatus.CONFLICT,
        "SFT-015",
        "근무 불가능 일정과 충돌하는 Shift가 있습니다."
    ),
    NOT_FOUND_ERROR(
        HttpStatus.NOT_FOUND,
        "SFT-016",
        "대상을 찾을 수 없습니다."
    ),
    NOT_FOUND_SCHEDULE_ERROR(
        HttpStatus.NOT_FOUND,
        "SFT-017",
        "스케줄이 없거나 잘못된 스케줄 ID입니다."
    ),
    NOT_FOUND_SHIFT_ERROR(
        HttpStatus.NOT_FOUND,
        "SFT-018",
        "해당 근무를 찾을 수 없습니다."
    ),
    NOT_FOUND_WORKPLACE_ERROR(
        HttpStatus.NOT_FOUND,
        "SFT-019",
        "해당 근무지를 찾을 수 없습니다."
    ),
    PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED(
        HttpStatus.CONFLICT,
        "SFT-020",
        "공개 후 변경 불가 상태입니다."
    ),
    UNAVAILABLE_TIME_CONFLICT_CONFIRMATION_REQUIRED(
        HttpStatus.CONFLICT,
        "SFT-021",
        "근무 불가능 일정 충돌 확인이 필요합니다."
    ),
    SAME_SHIFT_MEMBER_REASSIGNMENT(
        HttpStatus.CONFLICT,
        "SFT-022",
        "담당자 변경 대상이 현재 담당자와 같습니다."
    ),
    PUBLISHED_SHIFT_TIME_CONFLICT(
        HttpStatus.CONFLICT,
        "SFT-023",
        "공개 근무 시간이 기존 근무와 겹칩니다."
    );

    override fun getHttpStatus(): HttpStatus = httpStatus

    override fun getCode(): String = code

    override fun getMessage(): String = message
}
