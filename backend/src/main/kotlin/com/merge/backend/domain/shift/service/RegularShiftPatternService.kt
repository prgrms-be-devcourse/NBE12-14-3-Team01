package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.dto.RegularShiftPatternListResponse
import com.merge.backend.domain.shift.dto.RegularShiftPatternReqBody
import com.merge.backend.domain.shift.dto.RegularShiftPatternResponse
import com.merge.backend.domain.shift.entity.RegularShiftPattern
import com.merge.backend.domain.shift.exception.RegularShiftErrorCode
import com.merge.backend.domain.shift.repository.RegularShiftPatternRepository
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.rq.Rq
import com.merge.backend.global.util.TimeRangeUtils
import jakarta.transaction.Transactional
import java.time.LocalTime
import java.util.Collections
import org.springframework.stereotype.Service

@Service
@Transactional
class RegularShiftPatternService(
    private val regularShiftPatternRepository: RegularShiftPatternRepository,
    private val workplaceMemberRepository: WorkplaceMemberRepository,
    private val workplaceRepository: WorkplaceRepository,
    private val rq: Rq
) {
    // 기존 Java 호출 호환성과 검증 순서를 유지하기 위해 nullable 인자를 사용한다.
    fun register(workplaceId: Long?, reqBody: RegularShiftPatternReqBody?): RegularShiftPatternResponse {
        validateWorkplaceExists(workplaceId)
        requireManager(workplaceId)

        requireNotNull(reqBody) { "reqBody must not be null" }
        val workplaceMember = getActiveMember(workplaceId, reqBody.memberId)
        val (startTime, endTime) = validateTime(reqBody)
        validateOverlap(reqBody, startTime, endTime, null)

        val pattern = RegularShiftPattern(
            workplaceMember,
            requireNotNull(reqBody.dayOfWeek) { "dayOfWeek must not be null" },
            startTime,
            endTime
        )
        val savedPattern = regularShiftPatternRepository.save(pattern)
        return RegularShiftPatternResponse(
            savedPattern.id,
            workplaceMember.id,
            workplaceMember.user.name,
            savedPattern.dayOfWeek,
            savedPattern.startTime,
            savedPattern.endTime
        )
    }

    fun update(
        workplaceId: Long?,
        patternId: Long?,
        reqBody: RegularShiftPatternReqBody?
    ): RegularShiftPatternResponse {
        validateWorkplaceExists(workplaceId)
        requireManager(workplaceId)
        val pattern = getPatternInWorkplace(workplaceId, patternId)
        requireNotNull(reqBody) { "reqBody must not be null" }
        val member = getActiveMember(workplaceId, reqBody.memberId)
        val (startTime, endTime) = validateTime(reqBody)
        validateOverlap(reqBody, startTime, endTime, patternId)

        pattern.update(
            member,
            requireNotNull(reqBody.dayOfWeek) { "dayOfWeek must not be null" },
            startTime,
            endTime
        )
        return RegularShiftPatternResponse(
            pattern.id,
            member.id,
            member.user.name,
            pattern.dayOfWeek,
            pattern.startTime,
            pattern.endTime
        )
    }

    fun findAll(workplaceId: Long?): List<RegularShiftPatternListResponse> {
        validateWorkplaceExists(workplaceId)
        requireManager(workplaceId)
        val patterns = regularShiftPatternRepository
            .findByMemberWorkplaceIdAndMemberLeftAtIsNull(workplaceId)

        // 기존 Java Stream.toList()와 동일하게 수정 불가능한 목록을 반환한다.
        return Collections.unmodifiableList(
            patterns.map { pattern ->
                val member = pattern.member
                RegularShiftPatternListResponse(
                    pattern.id,
                    member.id,
                    member.user.name,
                    member.role,
                    pattern.dayOfWeek,
                    pattern.startTime,
                    pattern.endTime
                )
            }
        )
    }

    fun delete(workplaceId: Long?, patternId: Long?) {
        validateWorkplaceExists(workplaceId)
        requireManager(workplaceId)
        val pattern = getPatternInWorkplace(workplaceId, patternId)
        regularShiftPatternRepository.delete(pattern)
    }

    private fun validateWorkplaceExists(workplaceId: Long?) {
        if (!workplaceRepository.existsById(requireNotNull(workplaceId) { "workplaceId must not be null" })) {
            throw BusinessException(RegularShiftErrorCode.WORKPLACE_NOT_FOUND)
        }
    }

    private fun getActiveMember(workplaceId: Long?, memberId: Long?): WorkplaceMember {
        val member = workplaceMemberRepository.findById(requireNotNull(memberId) { "memberId must not be null" })
            .orElseThrow { BusinessException(RegularShiftErrorCode.MEMBER_NOT_FOUND) }
        val memberWorkplaceId = checkNotNull(member.workplace.id) { "Stored member must have a persisted workplace" }
        if (memberWorkplaceId != workplaceId) {
            throw BusinessException(RegularShiftErrorCode.MEMBER_NOT_IN_WORKPLACE)
        }
        if (member.leftAt != null) {
            throw BusinessException(RegularShiftErrorCode.MEMBER_NOT_ACTIVE)
        }
        return member
    }

    private fun getPatternInWorkplace(workplaceId: Long?, patternId: Long?): RegularShiftPattern {
        val pattern = regularShiftPatternRepository.findById(requireNotNull(patternId) { "patternId must not be null" })
            .orElseThrow { BusinessException(RegularShiftErrorCode.PATTERN_NOT_FOUND) }
        val member = pattern.member
        val patternWorkplaceId = checkNotNull(member.workplace.id) { "Stored pattern must have a persisted workplace" }
        if (patternWorkplaceId != workplaceId) {
            throw BusinessException(RegularShiftErrorCode.PATTERN_NOT_IN_WORKPLACE)
        }
        return pattern
    }

    private fun validateTime(reqBody: RegularShiftPatternReqBody): Pair<LocalTime, LocalTime> {
        val startTime = requireNotNull(reqBody.startTime) { "startTime must not be null" }
        val endTime = requireNotNull(reqBody.endTime) { "endTime must not be null" }
        if (!TimeRangeUtils.isValidRange(startTime, endTime)) {
            throw BusinessException(RegularShiftErrorCode.INVALID_PATTERN_TIME)
        }
        return startTime to endTime
    }

    private fun validateOverlap(
        reqBody: RegularShiftPatternReqBody,
        startTime: LocalTime,
        endTime: LocalTime,
        excludePatternId: Long?
    ) {
        val patterns = regularShiftPatternRepository
            .findByMemberIdAndDayOfWeek(reqBody.memberId, reqBody.dayOfWeek)
        val overlap = patterns.asSequence()
            // 수정 시 현재 패턴은 중복 검사에서 제외한다.
            .filter { pattern ->
                excludePatternId == null ||
                    checkNotNull(pattern.id) { "Stored pattern must have an ID for exclusion" } != excludePatternId
            }
            .any { pattern ->
                TimeRangeUtils.overlaps(
                    startTime,
                    endTime,
                    pattern.startTime,
                    pattern.endTime
                )
            }
        if (overlap) {
            throw BusinessException(RegularShiftErrorCode.PATTERN_TIME_OVERLAP)
        }
    }

    private fun requireManager(workplaceId: Long?) {
        val actorId = rq.actorId
        val actorMember = workplaceMemberRepository
            .findByWorkplace_IdAndUser_IdAndLeftAtIsNull(workplaceId, actorId)
            .orElseThrow {
                // 종료 소속만 있는 경우의 기존 오류 계약을 단건 이력 조회 없이 보존한다.
                val errorCode = if (workplaceMemberRepository.existsByWorkplaceIdAndUserId(workplaceId, actorId)) {
                    RegularShiftErrorCode.MEMBER_NOT_ACTIVE
                } else {
                    RegularShiftErrorCode.MEMBER_NOT_IN_WORKPLACE
                }
                BusinessException(errorCode)
            }
        if (actorMember.role != WorkplaceRole.MANAGER) {
            throw BusinessException(RegularShiftErrorCode.MANAGER_REQUIRED)
        }
    }
}
