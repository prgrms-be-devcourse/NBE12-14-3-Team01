package com.merge.backend.domain.shift.service

import com.merge.backend.domain.shift.dto.ShiftRequest
import com.merge.backend.domain.shift.entity.Schedule
import com.merge.backend.domain.shift.entity.Shift
import com.merge.backend.domain.shift.entity.ShiftStatus
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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@ActiveProfiles("test")
class ShiftModifyIntegrationTest {

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var shiftService: ShiftService

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var workplaceRepository: WorkplaceRepository

    @Autowired
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository

    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Test
    @DisplayName("근무 담당자를 A에서 B로 수정한다")
    fun t1() {
        val uniqueId = UUID.randomUUID().toString()

        val manager = userRepository.save(User("manager-$uniqueId@test.com", "password", "관리자"))
        val employeeA = userRepository.save(User("employeeA-$uniqueId@test.com", "password", "직원A"))
        val employeeB = userRepository.save(User("employeeB-$uniqueId@test.com", "password", "직원B"))
        val workplace = workplaceRepository.save(Workplace("테스트 근무지", uniqueId))

        workplaceMemberRepository.save(WorkplaceMember(workplace, manager, WorkplaceRole.MANAGER, LocalDateTime.now()))
        val memberA = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeA, WorkplaceRole.EMPLOYEE, LocalDateTime.now()))
        val memberB = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeB, WorkplaceRole.EMPLOYEE, LocalDateTime.now()))
        val schedule = scheduleRepository.save(Schedule(workplace, LocalDate.of(2026, 10, 19)))
        val shift = shiftRepository.save(
            Shift(
                schedule,
                memberA,
                LocalDateTime.of(2026, 10, 19, 9, 0),
                LocalDateTime.of(2026, 10, 19, 14, 0),
                ShiftStatus.SCHEDULED
            )
        )
        val request = ShiftRequest(
            memberB.id,
            LocalDateTime.of(2026, 10, 19, 9, 0),
            LocalDateTime.of(2026, 10, 19, 14, 0),
            false
        )

        shiftService.modify(workplace.id, schedule.id, shift.id, request, manager.id)

        val result = shiftRepository.findById(requireNotNull(shift.id)).orElseThrow()

        assertEquals(memberB.id, result.member.id)
        assertEquals(LocalDateTime.of(2026, 10, 19, 9, 0), result.startAt)
        assertEquals(LocalDateTime.of(2026, 10, 19, 14, 0), result.endAt)
    }

    @Test
    @DisplayName("근무 시간이 겹치면 담당자 수정에 실패하고 기존 데이터가 유지된다")
    fun t2() {
        val uniqueId = UUID.randomUUID().toString()

        val manager = userRepository.save(User("manager-$uniqueId@test.com", "password", "관리자"))
        val employeeA = userRepository.save(User("employeeA-$uniqueId@test.com", "password", "직원A"))
        val employeeB = userRepository.save(User("employeeB-$uniqueId@test.com", "password", "직원B"))
        val workplace = workplaceRepository.save(Workplace("테스트 근무지", uniqueId))

        workplaceMemberRepository.save(WorkplaceMember(workplace, manager, WorkplaceRole.MANAGER, LocalDateTime.now()))
        val memberA = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeA, WorkplaceRole.EMPLOYEE, LocalDateTime.now()))
        val memberB = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeB, WorkplaceRole.EMPLOYEE, LocalDateTime.now()))
        val schedule = scheduleRepository.save(Schedule(workplace, LocalDate.of(2026, 10, 19)))
        val shift = shiftRepository.save(
            Shift(schedule, memberA, LocalDateTime.of(2026, 10, 19, 9, 0), LocalDateTime.of(2026, 10, 19, 14, 0), ShiftStatus.SCHEDULED)
        )
        shiftRepository.save(
            Shift(schedule, memberB, LocalDateTime.of(2026, 10, 19, 10, 0), LocalDateTime.of(2026, 10, 19, 15, 0), ShiftStatus.SCHEDULED)
        )
        val request = ShiftRequest(
            memberB.id,
            LocalDateTime.of(2026, 10, 19, 9, 0),
            LocalDateTime.of(2026, 10, 19, 14, 0),
            false
        )

        val exception = assertThrows(BusinessException::class.java) {
            shiftService.modify(workplace.id, schedule.id, shift.id, request, manager.id)
        }

        assertEquals(ShiftErrorCode.DUPLICATE_LOCAL_SCHEDULE_TIME, exception.errorCode)

        val result = shiftRepository.findById(requireNotNull(shift.id)).orElseThrow()

        assertEquals(memberA.id, result.member.id)
        assertEquals(LocalDateTime.of(2026, 10, 19, 9, 0), result.startAt)
        assertEquals(LocalDateTime.of(2026, 10, 19, 14, 0), result.endAt)
    }

    @Test
    @DisplayName("두 트랜잭션이 같은 근무표에 접근하면 비관적 락으로 대기한다")
    fun t3() {
        val uniqueId = UUID.randomUUID().toString()
        val workplace = workplaceRepository.save(Workplace("동시성 테스트 근무지", uniqueId))
        val schedule = scheduleRepository.save(Schedule(workplace, LocalDate.of(2026, 10, 19)))
        val scheduleId = schedule.id

        val lockAcquired = CountDownLatch(1)
        val releaseLock = CountDownLatch(1)
        val bAttempting = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futureA = executor.submit {
                val template = TransactionTemplate(transactionManager)
                template.executeWithoutResult {
                    scheduleRepository.findByIdForUpdate(scheduleId).orElseThrow()
                    println("A: 락 획득")
                    lockAcquired.countDown()
                    try {
                        if (!releaseLock.await(10, TimeUnit.SECONDS)) {
                            throw IllegalStateException("A 락 유지 시간 초과")
                        }
                    } catch (exception: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw RuntimeException(exception)
                    }
                    println("A: 트랜잭션 종료")
                }
            }

            assertTrue(lockAcquired.await(5, TimeUnit.SECONDS))

            val futureB = executor.submit {
                val template = TransactionTemplate(transactionManager)
                template.executeWithoutResult {
                    bAttempting.countDown()
                    scheduleRepository.findByIdForUpdate(scheduleId).orElseThrow()
                    println("B: 락 획득")
                }
            }

            assertTrue(bAttempting.await(5, TimeUnit.SECONDS))
            Thread.sleep(500)
            assertFalse(futureB.isDone, "A가 락을 보유하는 동안 B는 완료되면 안 됨")
            println("검증 성공: B가 대기 중")

            releaseLock.countDown()
            futureA.get(10, TimeUnit.SECONDS)
            futureB.get(10, TimeUnit.SECONDS)
            println("검증 성공: A 종료 후 B도 완료")
        } finally {
            releaseLock.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    @DisplayName("두 스레드에서 동일 근무를 수정한 후 최종 데이터를 확인한다")
    fun t4() {
        val uniqueId = UUID.randomUUID().toString()
        val manager = userRepository.save(User("manager-$uniqueId@test.com", "password", "관리자"))
        val employeeA = userRepository.save(User("employeeA-$uniqueId@test.com", "password", "직원A"))
        val employeeB = userRepository.save(User("employeeB-$uniqueId@test.com", "password", "직원B"))
        val employeeC = userRepository.save(User("employeeC-$uniqueId@test.com", "password", "직원C"))
        val workplace = workplaceRepository.save(Workplace("동시 수정 테스트 근무지", uniqueId))

        workplaceMemberRepository.save(WorkplaceMember(workplace, manager, WorkplaceRole.MANAGER, LocalDateTime.now()))
        val memberA = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeA, WorkplaceRole.EMPLOYEE, LocalDateTime.now()))
        val memberB = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeB, WorkplaceRole.EMPLOYEE, LocalDateTime.now()))
        val memberC = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeC, WorkplaceRole.EMPLOYEE, LocalDateTime.now()))
        val schedule = scheduleRepository.save(Schedule(workplace, LocalDate.of(2026, 10, 19)))
        val shift = shiftRepository.save(
            Shift(schedule, memberA, LocalDateTime.of(2026, 10, 19, 9, 0), LocalDateTime.of(2026, 10, 19, 14, 0), ShiftStatus.SCHEDULED)
        )
        val requestB = ShiftRequest(memberB.id, LocalDateTime.of(2026, 10, 19, 9, 0), LocalDateTime.of(2026, 10, 19, 14, 0), false)
        val requestC = ShiftRequest(memberC.id, LocalDateTime.of(2026, 10, 19, 9, 0), LocalDateTime.of(2026, 10, 19, 14, 0), false)
        val workplaceId = workplace.id
        val scheduleId = schedule.id
        val shiftId = requireNotNull(shift.id)
        val managerId = manager.id
        val startLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futureB = executor.submit {
                startLatch.await()
                shiftService.modify(workplaceId, scheduleId, shiftId, requestB, managerId)
                null
            }
            val futureC = executor.submit {
                startLatch.await()
                shiftService.modify(workplaceId, scheduleId, shiftId, requestC, managerId)
                null
            }
            startLatch.countDown()
            futureB.get(10, TimeUnit.SECONDS)
            futureC.get(10, TimeUnit.SECONDS)
        } finally {
            startLatch.countDown()
            executor.shutdownNow()
        }

        val result = shiftRepository.findById(shiftId).orElseThrow()
        val finalMemberId = result.member.id

        assertTrue(finalMemberId == memberB.id || finalMemberId == memberC.id, "최종 담당자는 B 또는 C여야 함")
        assertEquals(LocalDateTime.of(2026, 10, 19, 9, 0), result.startAt)
        assertEquals(LocalDateTime.of(2026, 10, 19, 14, 0), result.endAt)
        println("최종 담당자 ID = $finalMemberId")
        println("동시 수정 후 데이터 검증 성공")
    }

    @Test
    @DisplayName("두 근무의 담당자 변경 요청 후 시간 중복 여부를 확인한다")
    fun t5() {
        val uniqueId = UUID.randomUUID().toString()
        val manager = userRepository.save(User("manager-$uniqueId@test.com", "password", "관리자"))
        val employeeA = userRepository.save(User("employeeA-$uniqueId@test.com", "password", "직원A"))
        val employeeB = userRepository.save(User("employeeB-$uniqueId@test.com", "password", "직원B"))
        val employeeC = userRepository.save(User("employeeC-$uniqueId@test.com", "password", "직원C"))
        val workplace = workplaceRepository.save(Workplace("근무 중복 동시성 테스트", uniqueId))

        workplaceMemberRepository.save(WorkplaceMember(workplace, manager, WorkplaceRole.MANAGER, LocalDateTime.now()))
        val memberA = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeA, WorkplaceRole.EMPLOYEE, LocalDateTime.now()))
        val memberB = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeB, WorkplaceRole.EMPLOYEE, LocalDateTime.now()))
        val memberC = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeC, WorkplaceRole.EMPLOYEE, LocalDateTime.now()))
        val schedule = scheduleRepository.save(Schedule(workplace, LocalDate.of(2026, 10, 19)))
        val shift1 = shiftRepository.save(
            Shift(schedule, memberB, LocalDateTime.of(2026, 10, 19, 9, 0), LocalDateTime.of(2026, 10, 19, 14, 0), ShiftStatus.SCHEDULED)
        )
        val shift2 = shiftRepository.save(
            Shift(schedule, memberC, LocalDateTime.of(2026, 10, 19, 10, 0), LocalDateTime.of(2026, 10, 19, 15, 0), ShiftStatus.SCHEDULED)
        )
        val request1 = ShiftRequest(memberA.id, LocalDateTime.of(2026, 10, 19, 9, 0), LocalDateTime.of(2026, 10, 19, 14, 0), false)
        val request2 = ShiftRequest(memberA.id, LocalDateTime.of(2026, 10, 19, 10, 0), LocalDateTime.of(2026, 10, 19, 15, 0), false)
        val workplaceId = workplace.id
        val scheduleId = schedule.id
        val managerId = manager.id
        val shift1Id = requireNotNull(shift1.id)
        val shift2Id = requireNotNull(shift2.id)
        val startLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val future1 = executor.submit<Boolean> {
                startLatch.await()
                try {
                    shiftService.modify(workplaceId, scheduleId, shift1Id, request1, managerId)
                    println("근무 1 수정 성공")
                    true
                } catch (exception: BusinessException) {
                    println("근무 1 수정 실패: ${exception.message}")
                    false
                }
            }
            val future2 = executor.submit<Boolean> {
                startLatch.await()
                try {
                    shiftService.modify(workplaceId, scheduleId, shift2Id, request2, managerId)
                    println("근무 2 수정 성공")
                    true
                } catch (exception: BusinessException) {
                    println("근무 2 수정 실패: ${exception.message}")
                    false
                }
            }
            startLatch.countDown()
            val success1 = future1.get(10, TimeUnit.SECONDS)
            val success2 = future2.get(10, TimeUnit.SECONDS)
            assertTrue(success1 != success2, "두 수정 요청 중 정확히 하나만 성공해야 함")
            println("검증 성공: 두 요청 중 하나만 수정됨")
        } finally {
            startLatch.countDown()
            executor.shutdownNow()
        }

        val result1 = shiftRepository.findById(shift1Id).orElseThrow()
        val result2 = shiftRepository.findById(shift2Id).orElseThrow()
        var assignedCount = 0
        if (result1.member.id == memberA.id) {
            assignedCount++
        }
        if (result2.member.id == memberA.id) {
            assignedCount++
        }
        assertEquals(1, assignedCount)
        println("직원 A에게 배정된 근무 개수 = $assignedCount")
        println("검증 성공: 중복 근무가 저장되지 않음")
    }

    @Test
    @DisplayName("JPA 엔티티 변경 후 예외 발생 시 트랜잭션이 롤백된다")
    fun t6() {
        val uniqueId = UUID.randomUUID().toString()
        val employeeA = userRepository.save(User("employeeA-$uniqueId@test.com", "password", "직원A"))
        val employeeB = userRepository.save(User("employeeB-$uniqueId@test.com", "password", "직원B"))
        val workplace = workplaceRepository.save(Workplace("롤백 테스트", uniqueId))
        val memberA = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeA, WorkplaceRole.EMPLOYEE, LocalDateTime.now()))
        val memberB = workplaceMemberRepository.save(WorkplaceMember(workplace, employeeB, WorkplaceRole.EMPLOYEE, LocalDateTime.now()))
        val schedule = scheduleRepository.save(Schedule(workplace, LocalDate.of(2026, 10, 19)))
        val shift = shiftRepository.save(
            Shift(schedule, memberB, LocalDateTime.of(2026, 10, 19, 9, 0), LocalDateTime.of(2026, 10, 19, 14, 0), ShiftStatus.SCHEDULED)
        )
        val shiftId = requireNotNull(shift.id)
        val transactionTemplate = TransactionTemplate(transactionManager)

        assertThrows(RuntimeException::class.java) {
            transactionTemplate.executeWithoutResult {
                val target = shiftRepository.findById(shiftId).orElseThrow()
                target.update(memberA, target.startAt, target.endAt)
                shiftRepository.flush()
                println("직원 A로 수정 후 flush 완료")
                throw RuntimeException("강제 롤백 테스트")
            }
        }

        val result = shiftRepository.findById(shiftId).orElseThrow()
        assertEquals(memberB.id, result.member.id)
        println("롤백 성공: 기존 직원 B 유지")
    }
}
