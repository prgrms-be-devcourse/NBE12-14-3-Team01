package com.merge.backend.domain.shift.service

import com.merge.backend.domain.notification.repository.NotificationRepository
import com.merge.backend.domain.shift.dto.ShiftRequest
import com.merge.backend.domain.shift.dto.PublishedShiftCreateRequest
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.exception.ScheduleErrorCode
import com.merge.backend.domain.shift.exception.ShiftErrorCode
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@ActiveProfiles("test")
class ShiftScheduleConcurrencyTest {

    @Autowired
    private lateinit var shiftService: ShiftService

    @Autowired
    private lateinit var scheduleService: ScheduleService

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository
    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var workplaceRepository: WorkplaceRepository

    @Autowired
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Test
    @DisplayName("Schedule 공개와 마지막 Shift 삭제가 동시에 요청되어도 일관된 상태를 유지한다")
    fun publishAndDeleteAreSerializedByScheduleLock() {
        val fixture = createFixture("publish-delete")
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)

        try {
            val publishFuture = executor.submit<Throwable?> {
                ready.countDown()
                start.await()
                try {
                    scheduleService.publishSchedule(fixture.managerUserId, fixture.workplaceId, fixture.scheduleId, true)
                    null
                } catch (throwable: Throwable) {
                    throwable
                }
            }
            val deleteFuture = executor.submit<Throwable?> {
                ready.countDown()
                start.await()
                try {
                    shiftService.delete(fixture.workplaceId, fixture.scheduleId, fixture.shiftId, fixture.managerUserId)
                    null
                } catch (throwable: Throwable) {
                    throwable
                }
            }
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            val publishError = publishFuture.get(5, TimeUnit.SECONDS)
            val deleteError = deleteFuture.get(5, TimeUnit.SECONDS)
            val finalSchedule = scheduleRepository.findById(fixture.scheduleId).orElseThrow()
            val finalShiftExists = shiftRepository.existsById(fixture.shiftId)
            val publishSucceeded = publishError == null
            val deleteSucceeded = deleteError == null
            assertThat(publishSucceeded.xor(deleteSucceeded)).isTrue()
            if (publishSucceeded) {
                assertThat(deleteError).isInstanceOf(BusinessException::class.java)
                assertThat((deleteError as BusinessException).errorCode).isEqualTo(ShiftErrorCode.INVALID_STATUS_VALUE)
                assertThat(finalSchedule.status).isEqualTo(ScheduleStatus.PUBLISHED)
                assertThat(finalShiftExists).isTrue()
            } else {
                assertThat(publishError).isInstanceOf(BusinessException::class.java)
                assertThat((publishError as BusinessException).errorCode).isEqualTo(ScheduleErrorCode.EMPTY_SCHEDULE)
                assertThat(finalSchedule.status).isEqualTo(ScheduleStatus.DRAFT)
                assertThat(finalShiftExists).isFalse()
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    @DisplayName("같은 Schedule에 겹치는 Shift가 동시에 추가되어도 하나만 생성된다")
    fun concurrentCreateAllowsOnlyOneOverlappingShift() {
        val fixture = createFixture("create-create")
        val startAt = LocalDateTime.of(2026, 9, 21, 15, 0)
        val endAt = LocalDateTime.of(2026, 9, 21, 19, 0)
        val request = ShiftRequest(fixture.employeeMemberId, startAt, endAt, true)
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val firstFuture = executor.submit<Throwable?> { runCreate(ready, start, request, fixture) }
            val secondFuture = executor.submit<Throwable?> { runCreate(ready, start, request, fixture) }
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            val firstError = firstFuture.get(5, TimeUnit.SECONDS)
            val secondError = secondFuture.get(5, TimeUnit.SECONDS)
            assertThat((firstError == null).xor(secondError == null)).isTrue()
            val failedError = firstError ?: secondError
            assertThat(failedError).isInstanceOf(BusinessException::class.java)
            assertThat((failedError as BusinessException).errorCode).isEqualTo(ShiftErrorCode.DUPLICATE_LOCAL_SCHEDULE_TIME)
            val createdShiftCount = shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(fixture.scheduleId)
                .count { it.startAt == startAt && it.endAt == endAt }
            assertThat(createdShiftCount).isEqualTo(1)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    @DisplayName("공개된 같은 Schedule에 같은 직원의 겹치는 근무를 동시에 추가해도 MySQL 최종 상태에는 하나만 저장된다")
    fun concurrentPublishedCreateAllowsOnlyOneLocalOverlappingShift() {
        val fixture = createPublishedShiftFixture("published-local")
        val request = PublishedShiftCreateRequest(fixture.memberId, fixture.startAt, fixture.endAt, emptyList())
        val errors = concurrentlyCreatePublishedShift(fixture, fixture, request, request)

        assertThat(errors.count { it == null }).isEqualTo(1)
        assertBusinessError(errors.first { it != null }, ShiftErrorCode.DUPLICATE_LOCAL_SCHEDULE_TIME)
        assertThat(shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(fixture.scheduleId)
            .count { it.member.id == fixture.memberId && it.startAt == fixture.startAt && it.endAt == fixture.endAt })
            .isEqualTo(1)
    }

    @Test
    @DisplayName("서로 다른 근무지에서 같은 직원의 겹치는 공개 근무를 동시에 추가해도 MySQL 최종 상태에는 하나만 저장된다")
    fun concurrentPublishedCreateAllowsOnlyOneGlobalOverlappingShift() {
        val fixture = createCrossWorkplacePublishedShiftFixture("published-global")
        val firstRequest = PublishedShiftCreateRequest(
            fixture.first.memberId, fixture.first.startAt, fixture.first.endAt, emptyList(),
        )
        val secondRequest = PublishedShiftCreateRequest(
            fixture.second.memberId, fixture.second.startAt, fixture.second.endAt, emptyList(),
        )
        val errors = concurrentlyCreatePublishedShift(fixture.first, fixture.second, firstRequest, secondRequest)

        assertThat(errors.count { it == null }).isEqualTo(1)
        assertBusinessError(errors.first { it != null }, ShiftErrorCode.DUPLICATE_GLOBAL_SCHEDULE_TIME)
        val created = listOf(fixture.first, fixture.second).sumOf { published ->
            shiftRepository.findBySchedule_IdOrderByStartAtAscIdAsc(published.scheduleId)
                .count { it.member.id == published.memberId && it.startAt == published.startAt && it.endAt == published.endAt }
        }
        assertThat(created).isEqualTo(1)
    }

    @Test
    @DisplayName("Schedule 공개와 Shift 담당자 수정이 동시에 요청되어도 최종 담당자와 알림 수신자가 일치한다")
    fun publishAndModifyKeepAssigneeAndNotificationConsistent() {
        val fixture = createFixture("publish-modify")
        val modifyRequest = ShiftRequest(fixture.replacementMemberId, LocalDateTime.of(2026, 9, 21, 10, 0), LocalDateTime.of(2026, 9, 21, 14, 0), true)
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val publishFuture = executor.submit<Throwable?> {
                ready.countDown()
                start.await()
                try {
                    scheduleService.publishSchedule(fixture.managerUserId, fixture.workplaceId, fixture.scheduleId, true)
                    null
                } catch (throwable: Throwable) {
                    throwable
                }
            }
            val modifyFuture = executor.submit<Throwable?> {
                ready.countDown()
                start.await()
                try {
                    shiftService.modify(fixture.workplaceId, fixture.scheduleId, fixture.shiftId, modifyRequest, fixture.managerUserId)
                    null
                } catch (throwable: Throwable) {
                    throwable
                }
            }
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            val publishError = publishFuture.get(5, TimeUnit.SECONDS)
            val modifyError = modifyFuture.get(5, TimeUnit.SECONDS)
            assertThat(publishError).isNull()
            val finalSchedule = scheduleRepository.findById(fixture.scheduleId).orElseThrow()
            val finalShift = shiftRepository.findById(fixture.shiftId).orElseThrow()
            assertThat(finalSchedule.status).isEqualTo(ScheduleStatus.PUBLISHED)
            if (modifyError == null) {
                assertThat(finalShift.member.id).isEqualTo(fixture.replacementMemberId)
            } else {
                assertThat(modifyError).isInstanceOf(BusinessException::class.java)
                assertThat((modifyError as BusinessException).errorCode).isEqualTo(ShiftErrorCode.INVALID_STATUS_VALUE)
                assertThat(finalShift.member.id).isEqualTo(fixture.employeeMemberId)
            }
            val employeeNotifications = notificationRepository.findAllByUserId(fixture.employeeUserId)
            val replacementNotifications = notificationRepository.findAllByUserId(fixture.replacementUserId)
            val finalMemberId = finalShift.member.id
            if (finalMemberId == fixture.employeeMemberId) {
                assertThat(employeeNotifications).hasSize(1)
                assertThat(replacementNotifications).isEmpty()
            } else {
                assertThat(finalMemberId).isEqualTo(fixture.replacementMemberId)
                assertThat(employeeNotifications).isEmpty()
                assertThat(replacementNotifications).hasSize(1)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    @DisplayName("같은 Workplace와 주차의 Schedule을 동시에 생성해도 하나만 생성된다")
    fun concurrentScheduleCreateAllowsOnlyOneSchedule() {
        val fixture = createScheduleCreationFixture("same-week-create")
        val weekStartDate = LocalDate.of(2026, 9, 21)
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val firstFuture = executor.submit<Throwable?> { runCreateSchedule(ready, start, fixture, weekStartDate) }
            val secondFuture = executor.submit<Throwable?> { runCreateSchedule(ready, start, fixture, weekStartDate) }
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            val firstError = firstFuture.get(5, TimeUnit.SECONDS)
            val secondError = secondFuture.get(5, TimeUnit.SECONDS)
            assertThat((firstError == null).xor(secondError == null)).isTrue()
            val failedError = firstError ?: secondError
            assertThat(failedError).isInstanceOf(BusinessException::class.java)
            assertThat((failedError as BusinessException).errorCode).isEqualTo(ScheduleErrorCode.SCHEDULE_ALREADY_EXISTS)
            val finalSchedule = scheduleRepository.findByWorkplace_IdAndWeekStartDate(fixture.workplaceId, weekStartDate).orElseThrow()
            assertThat(finalSchedule.id).isNotNull()
            assertThat(finalSchedule.weekStartDate).isEqualTo(weekStartDate)
            assertThat(finalSchedule.status).isEqualTo(ScheduleStatus.DRAFT)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun runCreate(ready: CountDownLatch, start: CountDownLatch, request: ShiftRequest, fixture: Fixture): Throwable? {
        ready.countDown()
        start.await()
        return try {
            shiftService.create(request, fixture.workplaceId, fixture.scheduleId, fixture.managerUserId)
            null
        } catch (throwable: Throwable) {
            throwable
        }
    }

    private fun concurrentlyCreatePublishedShift(
        firstFixture: PublishedShiftFixture,
        secondFixture: PublishedShiftFixture,
        firstRequest: PublishedShiftCreateRequest,
        secondRequest: PublishedShiftCreateRequest,
    ): List<Throwable?> {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val first = executor.submit<Throwable?> {
                runPublishedCreate(ready, start, firstFixture, firstRequest)
            }
            val second = executor.submit<Throwable?> {
                runPublishedCreate(ready, start, secondFixture, secondRequest)
            }
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            return listOf(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }

    private fun runPublishedCreate(
        ready: CountDownLatch,
        start: CountDownLatch,
        fixture: PublishedShiftFixture,
        request: PublishedShiftCreateRequest,
    ): Throwable? {
        ready.countDown()
        start.await()
        return try {
            shiftService.createPublishedShift(fixture.workplaceId, fixture.scheduleId, request, fixture.managerUserId)
            null
        } catch (throwable: Throwable) {
            throwable
        }
    }

    private fun assertBusinessError(error: Throwable?, expected: ShiftErrorCode) {
        assertThat(error)
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(expected)
    }

    private fun runCreateSchedule(ready: CountDownLatch, start: CountDownLatch, fixture: ScheduleCreationFixture, weekStartDate: LocalDate): Throwable? {
        ready.countDown()
        start.await()
        return try {
            scheduleService.createDraftSchedule(fixture.managerUserId, fixture.workplaceId, weekStartDate)
            null
        } catch (throwable: Throwable) {
            throwable
        }
    }

    private fun createFixture(suffix: String): Fixture = requireNotNull(TransactionTemplate(transactionManager).execute {
        val managerUser = userRepository.save(User("manager-$suffix@test.com", "password-hash", "manager"))
        val employeeUser = userRepository.save(User("employee-$suffix@test.com", "password-hash", "employee"))
        val replacementUser = userRepository.save(User("replacement-$suffix@test.com", "password-hash", "replacement"))
        val workplace = workplaceRepository.save(Workplace("동시성 테스트 매장 $suffix", "INVITE-$suffix"))
        val joinedAt = LocalDateTime.of(2026, 9, 1, 9, 0)
        workplaceMemberRepository.save(WorkplaceMember(workplace, managerUser, WorkplaceRole.MANAGER, joinedAt))
        val employeeMember = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeUser, WorkplaceRole.EMPLOYEE, joinedAt))
        val replacementMember = workplaceMemberRepository.save(WorkplaceMember(workplace, replacementUser, WorkplaceRole.EMPLOYEE, joinedAt))
        val schedule = scheduleRepository.save(Schedule(workplace, LocalDate.of(2026, 9, 21)))
        val shift = shiftRepository.save(
            Shift(schedule,
                employeeMember,
                LocalDateTime.of(2026, 9, 21, 10, 0),
                LocalDateTime.of(2026, 9, 21, 14, 0),
                ShiftStatus.SCHEDULED)
            )
        Fixture(requireNotNull(workplace.id),
            requireNotNull(managerUser.id),
            requireNotNull(employeeUser.id),
            requireNotNull(employeeMember.id),
            requireNotNull(replacementUser.id),
            requireNotNull(replacementMember.id),
            requireNotNull(schedule.id),
            requireNotNull(shift.id)
        )
    })

    private fun createPublishedShiftFixture(suffix: String): PublishedShiftFixture = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val now = LocalDateTime.now()
            val startAt = now.plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0)
            val workplace = workplaceRepository.save(Workplace("공개 근무 동시성 매장 $suffix", "PUBLISHED-$suffix"))
            val managerUser = userRepository.save(User("published-manager-$suffix@test.com", "password-hash", "manager"))
            val employeeUser = userRepository.save(User("published-employee-$suffix@test.com", "password-hash", "employee"))
            workplaceMemberRepository.save(WorkplaceMember(workplace, managerUser, WorkplaceRole.MANAGER, now.minusDays(1)))
            val member = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeUser, WorkplaceRole.EMPLOYEE, now.minusDays(1)))
            val schedule = Schedule(workplace, startAt.toLocalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)))
                .also { it.publish(now) }
                .let { scheduleRepository.save(it) }
            PublishedShiftFixture(
                requireNotNull(workplace.id), requireNotNull(schedule.id), requireNotNull(managerUser.id),
                requireNotNull(member.id), startAt, startAt.plusHours(4),
            )
        },
    )

    private fun createCrossWorkplacePublishedShiftFixture(suffix: String): CrossWorkplacePublishedShiftFixture = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val now = LocalDateTime.now()
            val startAt = now.plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0)
            val employeeUser = userRepository.save(User("cross-employee-$suffix@test.com", "password-hash", "employee"))
            fun create(workplaceSuffix: String): PublishedShiftFixture {
                val workplace = workplaceRepository.save(Workplace("교차 공개 근무 매장 $workplaceSuffix", "CROSS-$workplaceSuffix"))
                val manager = userRepository.save(User("cross-manager-$workplaceSuffix@test.com", "password-hash", "manager"))
                workplaceMemberRepository.save(WorkplaceMember(workplace, manager, WorkplaceRole.MANAGER, now.minusDays(1)))
                val member = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeUser, WorkplaceRole.EMPLOYEE, now.minusDays(1)))
                val schedule = Schedule(workplace, startAt.toLocalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)))
                    .also { it.publish(now) }
                    .let { scheduleRepository.save(it) }
                return PublishedShiftFixture(
                    requireNotNull(workplace.id), requireNotNull(schedule.id), requireNotNull(manager.id),
                    requireNotNull(member.id), startAt, startAt.plusHours(4),
                )
            }
            CrossWorkplacePublishedShiftFixture(create("$suffix-a"), create("$suffix-b"))
        },
    )

    private fun createScheduleCreationFixture(suffix: String): ScheduleCreationFixture =
        requireNotNull(
            TransactionTemplate(transactionManager).execute {
                val managerUser = userRepository.save(
                    User(
                        "schedule-manager-$suffix@test.com",
                        "password-hash",
                        "manager")
                    )
                val workplace = workplaceRepository.save(Workplace("Schedule 생성 동시성 매장 $suffix",
                    "SCHEDULE-$suffix"))
                workplaceMemberRepository.save(
                    WorkplaceMember(
                        workplace,
                        managerUser,
                        WorkplaceRole.MANAGER,
                        LocalDateTime.of(
                            2026,
                            9,
                            1,
                            9,
                            0)
                    )
                )
                ScheduleCreationFixture(requireNotNull(workplace.id),
                    requireNotNull(managerUser.id))
            }
        )

    private data class Fixture(
        val workplaceId: Long,
        val managerUserId: Long,
        val employeeUserId: Long,
        val employeeMemberId: Long,
        val replacementUserId: Long,
        val replacementMemberId: Long,
        val scheduleId: Long,
        val shiftId: Long
    )

    private data class PublishedShiftFixture(
        val workplaceId: Long,
        val scheduleId: Long,
        val managerUserId: Long,
        val memberId: Long,
        val startAt: LocalDateTime,
        val endAt: LocalDateTime,
    )

    private data class CrossWorkplacePublishedShiftFixture(
        val first: PublishedShiftFixture,
        val second: PublishedShiftFixture,
    )

    private data class ScheduleCreationFixture(val workplaceId: Long, val managerUserId: Long)
}
