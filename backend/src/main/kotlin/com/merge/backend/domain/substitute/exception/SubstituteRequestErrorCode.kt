package com.merge.backend.domain.substitute.exception

import com.merge.backend.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class SubstituteRequestErrorCode(
    private val httpStatus: HttpStatus,
    private val code: String,
    private val message: String,
) : ErrorCode {

    SUBSTITUTE_REQUEST_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SUB-001",
        "존재하지 않는 대체근무 요청입니다."),
    INVALID_REQUEST(
        HttpStatus.BAD_REQUEST,
        "SUB-002",
        "후보자의 수락이 필요합니다."),
    VALIDATE_STATUS_CLOSED(
        HttpStatus.BAD_REQUEST,
        "SUB-003",
        "해당 요청 이미 수락되거나 승인되었습니다."),
    INVALID_MEMBER(
        HttpStatus.BAD_REQUEST,
        "SUB-004",
        "대체 요청자와 실제 근무 담당자가 같지 않습니다."),
    CONFLICT_SHIFT(
        HttpStatus.CONFLICT,
        "SUB-005",
        "해당 근무 시간에 중복되는 근무가 있습니다."),
    CONFLICT_UNAVAILABLE_TIME(
        HttpStatus.CONFLICT,
        "SUB-006",
        "등록한 불가능 시간과 중복됩니다."),
    CONFLICT_ACTIVE_SUBSTITUTE(
        HttpStatus.CONFLICT,
        "SUB-007",
        "이미 수락한 대체 근무 시간과 중복됩니다."),
    NOT_FOUND_CANDIDATE(
        HttpStatus.CONFLICT,
        "SUB-008",
        "수락한 후보자를 찾을 수 없습니다."),
    SHIFT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SUB-009",
        "해당 shift를 찾을 수 없습니다."),
    SHIFT_NOT_PUBLISHED(
        HttpStatus.BAD_REQUEST,
        "SUB-010",
        "아직 공개되지 않은 근무입니다."),
    NOT_OWN_SHIFT(
        HttpStatus.FORBIDDEN,
        "SUB-011",
        "본인 근무에 대해서만 대체근무 요청을 할 수 있습니다"),
    SHIFT_ALREADY_STARTED(
        HttpStatus.BAD_REQUEST,
        "SUB-012",
        "이미 시작된 근무는 대체근무를 요청할 수 없습니다"),
    ACTIVE_REQUEST_EXISTS(
        HttpStatus.CONFLICT,
        "SUB-013",
        "이미 진행중인 대체근무 요청이 있습니다"),
    SHIFT_CANCELLED(
        HttpStatus.BAD_REQUEST,
        "SUB-014",
        "취소된 근무에는 대체근무를 요청할 수 없습니다"),
    ACCEPTED_CANDIDATE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SUB-015",
        "ACCEPTED 상태인 요청의 수락자 정보를 찾을 수 없습니다."),
    ACCEPTED_CANDIDATE_DUPLICATED(
        HttpStatus.BAD_REQUEST,
        "SUB-016",
        "하나의 요청에 대해 수락자가 여러 명 조회되었습니다."),
    ALREADY_TERMINATED(
        HttpStatus.BAD_REQUEST,
        "SUB-017",
        "이미 종료되었거나 승인된 요청은 종료할 수 없습니다."),
    SHIFT_ALREADY_STARTED_NOT_CLOSE(
        HttpStatus.BAD_REQUEST,
        "SUB-018",
        "근무 시작 이후에는 요청을 종료할 수 없습니다."),
    CANDIDATE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SUB-019",
        "해당 대타 후보를 찾을 수 없습니다."),
    NOT_OWN_CANDIDATE(
        HttpStatus.FORBIDDEN,
        "SUB-020",
        "본인의 대타 요청에만 응답할 수 있습니다."),
    INVALID_CANDIDATE_MEMBER(
        HttpStatus.FORBIDDEN,
        "SUB-021",
        "현재 유효한 직원만 대타 요청에 응답할 수 있습니다."),
    CANDIDATE_ALREADY_RESPONDED(
        HttpStatus.CONFLICT,
        "SUB-022",
        "이미 응답한 대타 요청입니다."),
    REQUEST_NOT_OPEN(
        HttpStatus.CONFLICT,
        "SUB-023",
        "더 이상 응답할 수 없는 대타 요청입니다."),
    ;

    override fun getHttpStatus(): HttpStatus = httpStatus

    override fun getCode(): String = code

    override fun getMessage(): String = message
}
