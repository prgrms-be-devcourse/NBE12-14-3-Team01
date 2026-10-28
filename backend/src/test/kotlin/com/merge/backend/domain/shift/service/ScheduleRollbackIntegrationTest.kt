package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.event.SchedulePublishedEvent
import com.merge.backend.domain.shift.exception.ScheduleErrorCode
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.exception.BusinessException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(ScheduleRollbackIntegrationTest.FailingEventConfig::class)
class ScheduleRollbackIntegrationTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var workplaceRepository: WorkplaceRepository

    @Autowired
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Autowired
    private lateinit var scheduleService: ScheduleService

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository

    @Autowired
    private lateinit var failingEventListener: FailingEventListener

    @BeforeEach
    fun setUp() {
        failingEventListener.setShouldFail(false)
    }

    @Test
    @DisplayName("근무표 공개 실패 시 상태 롤백")
    fun t1() {
        val uniqueId = UUID.randomUUID().toString()

        val manager = userRepository.save(User("manager-$uniqueId@test.com", "password", "관리자"))
        val employee = userRepository.save(User("employee-$uniqueId@test.com", "password", "직원"))

        val workplace = workplaceRepository.save(Workplace("테스트 근무지", uniqueId))

        workplaceMemberRepository.save(
            WorkplaceMember(workplace, manager, WorkplaceRole.MANAGER, LocalDateTime.now()),
        )

        val employeeMember = workplaceMemberRepository.save(
            WorkplaceMember(workplace, employee, WorkplaceRole.EMPLOYEE, LocalDateTime.now()),
        )

        val schedule = scheduleRepository.save(Schedule(workplace, LocalDate.of(2026, 10, 19)))

        shiftRepository.save(
            Shift(
                schedule,
                employeeMember,
                LocalDateTime.of(2026, 10, 19, 9, 0),
                LocalDateTime.of(2026, 10, 19, 14, 0),
                ShiftStatus.SCHEDULED,
            ),
        )

        val scheduleId = requireNotNull(schedule.id)

        failingEventListener.setShouldFail(true)

        val exception = assertThrows(RuntimeException::class.java) {
            scheduleService.publishSchedule(
                requireNotNull(manager.id),
                requireNotNull(workplace.id),
                scheduleId,
                false,
            )
        }

        assertEquals("테스트용 강제 오류", exception.message)

        val result = scheduleRepository.findById(scheduleId).orElseThrow()

        assertEquals(ScheduleStatus.DRAFT, result.status)
        assertNull(result.publishedAt)
    }

    @Test
    @DisplayName("근무표 공개 성공 시 PUBLISHED 상태 저장")
    fun t2() {
        val uniqueId = UUID.randomUUID().toString()

        val manager = userRepository.save(User("manager-$uniqueId@test.com", "password", "관리자"))
        val employee = userRepository.save(User("employee-$uniqueId@test.com", "password", "직원"))

        val workplace = workplaceRepository.save(Workplace("테스트 근무지", uniqueId))

        workplaceMemberRepository.save(
            WorkplaceMember(workplace, manager, WorkplaceRole.MANAGER, LocalDateTime.now()),
        )

        val employeeMember = workplaceMemberRepository.save(
            WorkplaceMember(workplace, employee, WorkplaceRole.EMPLOYEE, LocalDateTime.now()),
        )

        val schedule = scheduleRepository.save(Schedule(workplace, LocalDate.of(2026, 10, 19)))

        shiftRepository.save(
            Shift(
                schedule,
                employeeMember,
                LocalDateTime.of(2026, 10, 19, 9, 0),
                LocalDateTime.of(2026, 10, 19, 14, 0),
                ShiftStatus.SCHEDULED,
            ),
        )

        val scheduleId = requireNotNull(schedule.id)

        failingEventListener.setShouldFail(false)

        scheduleService.publishSchedule(
            requireNotNull(manager.id),
            requireNotNull(workplace.id),
            scheduleId,
            false,
        )

        val result = scheduleRepository.findById(scheduleId).orElseThrow()

        assertEquals(ScheduleStatus.PUBLISHED, result.status)
        assertNotNull(result.publishedAt)
    }

    @Test
    @DisplayName("이미 공개된 근무표는 다시 공개할 수 없다")
    fun t3() {
        val uniqueId = UUID.randomUUID().toString()

        val manager = userRepository.save(User("manager-$uniqueId@test.com", "password", "관리자"))
        val employee = userRepository.save(User("employee-$uniqueId@test.com", "password", "직원"))

        val workplace = workplaceRepository.save(Workplace("테스트 근무지", uniqueId))

        workplaceMemberRepository.save(
            WorkplaceMember(workplace, manager, WorkplaceRole.MANAGER, LocalDateTime.now()),
        )

        val employeeMember = workplaceMemberRepository.save(
            WorkplaceMember(workplace, employee, WorkplaceRole.EMPLOYEE, LocalDateTime.now()),
        )

        val schedule = scheduleRepository.save(Schedule(workplace, LocalDate.of(2026, 10, 19)))

        shiftRepository.save(
            Shift(
                schedule,
                employeeMember,
                LocalDateTime.of(2026, 10, 19, 9, 0),
                LocalDateTime.of(2026, 10, 19, 14, 0),
                ShiftStatus.SCHEDULED,
            ),
        )

        val scheduleId = requireNotNull(schedule.id)
        val managerId = requireNotNull(manager.id)
        val workplaceId = requireNotNull(workplace.id)

        failingEventListener.setShouldFail(false)

        scheduleService.publishSchedule(managerId, workplaceId, scheduleId, false)

        val firstResult = scheduleRepository.findById(scheduleId).orElseThrow()
        val firstPublishedAt = firstResult.publishedAt

        val exception = assertThrows(BusinessException::class.java) {
            scheduleService.publishSchedule(managerId, workplaceId, scheduleId, false)
        }

        assertEquals(ScheduleErrorCode.SCHEDULE_NOT_DRAFT, exception.errorCode)

        val result = scheduleRepository.findById(scheduleId).orElseThrow()

        assertEquals(ScheduleStatus.PUBLISHED, result.status)
        assertEquals(firstPublishedAt, result.publishedAt)
    }

    @TestConfiguration
    class FailingEventConfig {

        @Bean
        fun failingEventListener(): FailingEventListener = FailingEventListener()
    }

    class FailingEventListener {

        private var shouldFail = false

        fun setShouldFail(shouldFail: Boolean) {
            this.shouldFail = shouldFail
        }

        @EventListener
        fun onSchedulePublished(event: SchedulePublishedEvent) {
            if (shouldFail) {
                throw RuntimeException("테스트용 강제 오류")
            }
        }
    }
}
