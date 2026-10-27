package com.merge.backend.domain.shift.exception

import com.merge.backend.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ScheduleErrorCode(
    private val status: HttpStatus,
    private val errorCode: String,
    private val errorMessage: String
) : ErrorCode {
    INVALID_WEEK_START_DATE(
        HttpStatus.BAD_REQUEST,
        "SCH-001",
        "주 시작일은 월요일이어야 합니다."
    ),

    SCHEDULE_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "SCH-002",
        "해당 주차의 근무표가 이미 존재합니다."
    ),

    SCHEDULE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SCH-003",
        "근무표를 찾을 수 없습니다."
    ),

    SCHEDULE_NOT_DRAFT(
        HttpStatus.CONFLICT,
        "SCH-004",
        "DRAFT 상태의 근무표만 공개할 수 있습니다."
    ),

    EMPTY_SCHEDULE(
        HttpStatus.CONFLICT,
        "SCH-005",
        "근무가 없는 근무표는 공개할 수 없습니다."
    ),

    INVALID_SHIFT_MEMBER(
        HttpStatus.CONFLICT,
        "SCH-006",
        "현재 구성원이 아닌 담당자가 포함되어 있어 근무표를 공개할 수 없습니다."
    ),

    INVALID_SHIFT_TIME(
        HttpStatus.CONFLICT,
        "SCH-007",
        "유효하지 않은 근무 시간이 포함되어 있어 근무표를 공개할 수 없습니다."
    ),

    INVALID_SHIFT_SAME_DAY(
        HttpStatus.CONFLICT,
        "SCH-008",
        "자정을 넘는 근무가 포함되어 있어 근무표를 공개할 수 없습니다."
    ),

    INVALID_SHIFT_WEEK(
        HttpStatus.CONFLICT,
        "SCH-009",
        "근무표 주차 범위를 벗어난 근무가 포함되어 있습니다."
    ),

    SHIFT_OVERLAP(
        HttpStatus.CONFLICT,
        "SCH-010",
        "같은 담당자의 근무 시간이 겹쳐 근무표를 공개할 수 없습니다."
    ),

    OFFICIAL_SHIFT_CONFLICT(
        HttpStatus.CONFLICT,
        "SCH-011",
        "다른 공식 근무와 시간이 겹쳐 근무표를 공개할 수 없습니다."
    ),

    NOT_PUBLISHED(
        HttpStatus.BAD_REQUEST,
        "SCH-012",
        "해당 요청의 스케줄은 공개되지 않았습니다."
    );

    override fun getHttpStatus(): HttpStatus = status

    override fun getCode(): String = errorCode

    override fun getMessage(): String = errorMessage
}
