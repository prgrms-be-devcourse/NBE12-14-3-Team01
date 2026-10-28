package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.ScheduleStatus
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
import com.merge.backend.domain.shift.entity.UnavailableTime
import com.merge.backend.domain.shift.exception.ShiftErrorCode
import com.merge.backend.domain.shift.exception.UnavailableTimeErrorCode
import com.merge.backend.domain.shift.repository.ScheduleRepository
import com.merge.backend.domain.shift.repository.ShiftRepository
import com.merge.backend.domain.shift.repository.UnavailableTimeRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.config.TestTimeConfig
import com.merge.backend.global.exception.BusinessException
import com.merge.backend.global.rq.Rq
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@ActiveProfiles("test")
@Import(TestTimeConfig::class)
class UnavailableTimeConcurrencyTest {

    @Autowired
    private lateinit var unavailableTimeService: UnavailableTimeService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var scheduleService: ScheduleService

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository

    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Autowired
    private lateinit var workplaceRepository: WorkplaceRepository

    @Autowired
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Autowired
    private lateinit var unavailableTimeRepository: UnavailableTimeRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @MockitoBean
    private lateinit var rq: Rq

    @Test
    @DisplayName("같은 User의 겹치는 UnavailableTime을 동시에 등록해도 하나만 생성된다")
    fun concurrentRegisterAllowsOnlyOneOverlappingUnavailableTime() {
        // given
        val userId = createUser("register-register")
        given(rq.actorId).willReturn(userId)

        val firstStartAt = LocalDateTime.of(2026, 10, 10, 10, 0)
        val firstEndAt = LocalDateTime.of(2026, 10, 10, 14, 0)
        val secondStartAt = LocalDateTime.of(2026, 10, 10, 12, 0)
        val secondEndAt = LocalDateTime.of(2026, 10, 10, 16, 0)

        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)

        try {
            val firstFuture: Future<Throwable?> = executor.submit(
                Callable {
                    ready.countDown()
                    start.await()

                    try {
                        unavailableTimeService.register(firstStartAt, firstEndAt, false)
                        null
                    } catch (throwable: Throwable) {
                        throwable
                    }
                }
            )
            val secondFuture: Future<Throwable?> = executor.submit(
                Callable {
                    ready.countDown()
                    start.await()

                    try {
                        unavailableTimeService.register(secondStartAt, secondEndAt, false)
                        null
                    } catch (throwable: Throwable) {
                        throwable
                    }
                }
            )

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue()

            start.countDown()

            val firstError = firstFuture.get(5, TimeUnit.SECONDS)
            val secondError = secondFuture.get(5, TimeUnit.SECONDS)

            val firstSucceeded = firstError == null
            val secondSucceeded = secondError == null

            // 둘 중 정확히 하나만 성공
            assertThat(firstSucceeded xor secondSucceeded).isTrue()

            // 실패한 하나는 overlap 비즈니스 예외
            val failedError = requireNotNull(firstError ?: secondError)
            assertThat(failedError).isInstanceOf(BusinessException::class.java)

            val businessException = failedError as BusinessException
            assertThat(businessException.errorCode).isEqualTo(UnavailableTimeErrorCode.TIME_OVERLAP)

            // 최종 DB에도 하나만 존재
            val finalUnavailableTimes = unavailableTimeRepository.findByUserId(userId)
            assertThat(finalUnavailableTimes).hasSize(1)

            val finalUnavailableTime = finalUnavailableTimes[0]
            val firstPersisted =
                finalUnavailableTime.startAt == firstStartAt && finalUnavailableTime.endAt == firstEndAt
            val secondPersisted =
                finalUnavailableTime.startAt == secondStartAt && finalUnavailableTime.endAt == secondEndAt

            assertThat(firstPersisted || secondPersisted).isTrue()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    @DisplayName("같은 User의 등록과 수정이 동시에 겹치는 시간을 만들려 해도 하나만 성공한다")
    fun concurrentRegisterAndUpdateDoNotCreateOverlap() {
        // given
        val userId = createUser("register-update")
        given(rq.actorId).willReturn(userId)

        val originalStartAt = LocalDateTime.of(2026, 10, 11, 9, 0)
        val originalEndAt = LocalDateTime.of(2026, 10, 11, 10, 0)
        val unavailableTimeId = createUnavailableTime(userId, originalStartAt, originalEndAt)

        /*
         * 새 등록
         * 12:00 ~ 14:00
         */
        val registerStartAt = LocalDateTime.of(2026, 10, 11, 12, 0)
        val registerEndAt = LocalDateTime.of(2026, 10, 11, 14, 0)

        /*
         * 기존 일정 수정
         * 09:00 ~ 10:00
         *        ↓
         * 13:00 ~ 15:00
         *
         * 새 등록 시간과 13:00 ~ 14:00이 겹친다.
         */
        val updateStartAt = LocalDateTime.of(2026, 10, 11, 13, 0)
        val updateEndAt = LocalDateTime.of(2026, 10, 11, 15, 0)

        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)

        try {
            val registerFuture: Future<Throwable?> = executor.submit(
                Callable {
                    ready.countDown()
                    start.await()

                    try {
                        unavailableTimeService.register(registerStartAt, registerEndAt, false)
                        null
                    } catch (throwable: Throwable) {
                        throwable
                    }
                }
            )
            val updateFuture: Future<Throwable?> = executor.submit(
                Callable {
                    ready.countDown()
                    start.await()

                    try {
                        unavailableTimeService.update(unavailableTimeId, updateStartAt, updateEndAt, false)
                        null
                    } catch (throwable: Throwable) {
                        throwable
                    }
                }
            )

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue()

            start.countDown()

            val registerError = registerFuture.get(5, TimeUnit.SECONDS)
            val updateError = updateFuture.get(5, TimeUnit.SECONDS)

            val registerSucceeded = registerError == null
            val updateSucceeded = updateError == null

            // 둘 중 정확히 하나만 성공
            assertThat(registerSucceeded xor updateSucceeded).isTrue()

            val failedError = requireNotNull(registerError ?: updateError)
            assertThat(failedError).isInstanceOf(BusinessException::class.java)

            val businessException = failedError as BusinessException
            assertThat(businessException.errorCode).isEqualTo(UnavailableTimeErrorCode.TIME_OVERLAP)

            // 최종 DB 상태 확인
            val finalUnavailableTimes = unavailableTimeRepository.findByUserId(userId)

            val registerWon =
                finalUnavailableTimes.size == 2 &&
                    finalUnavailableTimes.any { unavailableTime ->
                        unavailableTime.id == unavailableTimeId &&
                            unavailableTime.startAt == originalStartAt &&
                            unavailableTime.endAt == originalEndAt
                    } &&
                    finalUnavailableTimes.any { unavailableTime ->
                        unavailableTime.startAt == registerStartAt &&
                            unavailableTime.endAt == registerEndAt
                    }

            val updateWon =
                finalUnavailableTimes.size == 1 &&
                    finalUnavailableTimes[0].id == unavailableTimeId &&
                    finalUnavailableTimes[0].startAt == updateStartAt &&
                    finalUnavailableTimes[0].endAt == updateEndAt

            assertThat(registerWon || updateWon).isTrue()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    @DisplayName("Schedule 공개와 UnavailableTime 등록이 동시에 실행되어도 확인 없는 충돌 상태는 만들어지지 않는다")
    fun concurrentPublishAndRegisterDoNotCreateUnconfirmedConflict() {
        // given
        val fixture = createPublishFixture("publish-register")
        given(rq.actorId).willReturn(fixture.employeeUserId)

        val unavailableStartAt = LocalDateTime.of(2026, 10, 6, 10, 0)
        val unavailableEndAt = LocalDateTime.of(2026, 10, 6, 14, 0)

        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)

        try {
            val publishFuture: Future<Throwable?> = executor.submit(
                Callable {
                    ready.countDown()
                    start.await()

                    try {
                        scheduleService.publishSchedule(
                            fixture.managerUserId,
                            fixture.workplaceId,
                            fixture.scheduleId,
                            false,
                        )
                        null
                    } catch (throwable: Throwable) {
                        throwable
                    }
                }
            )
            val registerFuture: Future<Throwable?> = executor.submit(
                Callable {
                    ready.countDown()
                    start.await()

                    try {
                        unavailableTimeService.register(unavailableStartAt, unavailableEndAt, false)
                        null
                    } catch (throwable: Throwable) {
                        throwable
                    }
                }
            )

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue()

            start.countDown()

            val publishError = publishFuture.get(5, TimeUnit.SECONDS)
            val registerError = registerFuture.get(5, TimeUnit.SECONDS)

            val publishSucceeded = publishError == null
            val registerSucceeded = registerError == null

            /*
             * 둘 중 정확히 하나만 성공해야 한다.
             *
             * publish 먼저:
             *   publish 성공
             *   register → OFFICIAL_SHIFT_CONFLICT
             *
             * register 먼저:
             *   register 성공
             *   publish → UNAVAILABLE_TIME_CONFLICT
             */
            assertThat(publishSucceeded xor registerSucceeded).isTrue()

            val finalSchedule = scheduleRepository.findById(fixture.scheduleId).orElseThrow()
            val finalUnavailableTimes = unavailableTimeRepository.findByUserId(fixture.employeeUserId)

            if (publishSucceeded) {
                assertThat(registerError).isInstanceOf(BusinessException::class.java)

                val businessException = registerError as BusinessException
                assertThat(businessException.errorCode)
                    .isEqualTo(UnavailableTimeErrorCode.OFFICIAL_SHIFT_CONFLICT)

                assertThat(finalSchedule.status).isEqualTo(ScheduleStatus.PUBLISHED)
                assertThat(finalUnavailableTimes).isEmpty()
            } else {
                assertThat(publishError).isInstanceOf(BusinessException::class.java)

                val businessException = publishError as BusinessException
                assertThat(businessException.errorCode).isEqualTo(ShiftErrorCode.UNAVAILABLE_TIME_CONFLICT)

                assertThat(finalSchedule.status).isEqualTo(ScheduleStatus.DRAFT)
                assertThat(finalUnavailableTimes).hasSize(1)

                val savedUnavailableTime = finalUnavailableTimes[0]
                assertThat(savedUnavailableTime.startAt).isEqualTo(unavailableStartAt)
                assertThat(savedUnavailableTime.endAt).isEqualTo(unavailableEndAt)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun createUser(suffix: String): Long = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val user = userRepository.save(
                User(
                    "unavailable-concurrency-$suffix@test.com",
                    "password-hash",
                    "test-user",
                )
            )

            user.id
        }
    )

    private fun createUnavailableTime(
        userId: Long,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
    ): Long = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val user = userRepository.findById(userId).orElseThrow()
            val unavailableTime = unavailableTimeRepository.save(UnavailableTime(user, startAt, endAt))

            unavailableTime.id
        }
    )

    private fun createPublishFixture(suffix: String): PublishFixture = requireNotNull(
        TransactionTemplate(transactionManager).execute {
            val managerUser = userRepository.save(
                User("publish-manager-$suffix@test.com", "password-hash", "manager")
            )
            val employeeUser = userRepository.save(
                User("publish-employee-$suffix@test.com", "password-hash", "employee")
            )
            val workplace = workplaceRepository.save(
                Workplace("UNA 공개 동시성 테스트 매장 $suffix", "UNA-PUBLISH-$suffix")
            )
            val joinedAt = LocalDateTime.of(2026, 9, 1, 9, 0)

            workplaceMemberRepository.save(
                WorkplaceMember(workplace, managerUser, WorkplaceRole.MANAGER, joinedAt)
            )
            val employeeMember = workplaceMemberRepository.save(
                WorkplaceMember(workplace, employeeUser, WorkplaceRole.EMPLOYEE, joinedAt)
            )
            val schedule = scheduleRepository.save(
                Schedule(workplace, LocalDate.of(2026, 10, 5))
            )

            shiftRepository.save(
                Shift(
                    schedule,
                    employeeMember,
                    LocalDateTime.of(2026, 10, 6, 10, 0),
                    LocalDateTime.of(2026, 10, 6, 14, 0),
                    ShiftStatus.SCHEDULED,
                )
            )

            PublishFixture(
                requireNotNull(managerUser.id),
                requireNotNull(employeeUser.id),
                requireNotNull(workplace.id),
                requireNotNull(schedule.id),
            )
        }
    )

    private data class PublishFixture(
        val managerUserId: Long,
        val employeeUserId: Long,
        val workplaceId: Long,
        val scheduleId: Long,
    )
}
