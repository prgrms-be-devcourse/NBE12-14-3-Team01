package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.dto.response.MyWorkplaceResponse
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.config.TimeConfig
import com.merge.backend.global.exception.BusinessException
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowingConsumer
import org.hibernate.TransientPropertyValueException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(
    TimeConfig::class,
    WorkplaceService::class,
    WorkplaceMemberService::class
)
class WorkplaceServiceIntegrationTest {

    @Autowired
    private lateinit var workplaceService: WorkplaceService

    @Autowired
    private lateinit var workplaceRepository: WorkplaceRepository

    @Autowired
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Test
    @DisplayName("근무지 생성 시 관리자 멤버십도 DB에 저장된다")
    fun createWorkplace() {
        val user = entityManager.persist(
            User("manager@test.com", "password", "관리자")
        )

        val created = workplaceService.createWorkplace("테스트 매장", user)

        val workplaceId = requireNotNull(created.workplace.id)
        val memberId = requireNotNull(created.id)

        entityManager.flush()
        entityManager.clear()

        val workplace = workplaceRepository.findById(workplaceId).orElseThrow()

        val member = workplaceMemberRepository.findById(memberId).orElseThrow()

        assertThat(workplace.name).isEqualTo("테스트 매장")
        assertThat(member.role).isEqualTo(WorkplaceRole.MANAGER)
        assertThat(member.user.id).isEqualTo(user.id)
        assertThat(member.workplace.id).isEqualTo(workplaceId)
        assertThat(member.joinedAt).isNotNull()
    }

    @Test
    @DisplayName("초대코드로 참여하면 직원 멤버십이 DB에 저장된다")
    fun joinWorkplace() {
        val user = entityManager.persist(
            User("employee@test.com", "password", "직원")
        )

        val workplace = entityManager.persist(
            Workplace("테스트 매장", "JOIN1234")
        )

        val joined = workplaceService.joinWorkplace("JOIN1234", user)

        val memberId = requireNotNull(joined.id)

        entityManager.flush()
        entityManager.clear()

        val member = workplaceMemberRepository.findById(memberId).orElseThrow()

        assertThat(member.user.id).isEqualTo(user.id)
        assertThat(member.workplace.id)
            .isEqualTo(workplace.id)
        assertThat(member.role).isEqualTo(WorkplaceRole.EMPLOYEE)
        assertThat(member.leftAt).isNull()
    }

    @Test
    @DisplayName("이미 참여한 사용자는 중복 참여할 수 없다")
    fun rejectDuplicateJoin() {
        val user = entityManager.persist(
            User("duplicate@test.com", "password", "직원")
        )

        entityManager.persist(
            Workplace("테스트 매장", "JOIN5678")
        )

        workplaceService.joinWorkplace("JOIN5678", user)
        entityManager.flush()

        val exception = assertThrows(BusinessException::class.java) {
            workplaceService.joinWorkplace("JOIN5678", user)
        }

        assertThat(exception.errorCode)
            .isEqualTo(WorkplaceErrorCode.ALREADY_JOINED_WORKPLACE)
    }

    @Test
    @DisplayName("퇴사한 관리자는 초대코드를 조회할 수 없다")
    fun rejectRetiredManagerAccess() {
        val user = entityManager.persist(
            User("retired@test.com", "password", "퇴사 관리자")
        )

        val workplace = entityManager.persist(
            Workplace("테스트 매장", "RETIRE01")
        )

        // 이미 퇴사 처리된 멤버십을 테스트 데이터로 저장
        entityManager.persist(
            WorkplaceMember(
                workplace,
                user,
                WorkplaceRole.MANAGER,
                LocalDateTime.of(2026, 9, 1, 9, 0),
                LocalDateTime.of(2026, 9, 15, 18, 0)
            )
        )

        val workplaceId = requireNotNull(workplace.id)
        val userId = requireNotNull(user.id)

        entityManager.flush()
        entityManager.clear()

        val exception = assertThrows(BusinessException::class.java) {
            workplaceService.getInviteCode(workplaceId, userId)
        }

        assertThat(exception.errorCode)
            .isEqualTo(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER)
    }

    @Test
    @DisplayName("다른 근무지의 관리자는 초대코드를 조회할 수 없다")
    fun rejectOtherWorkplaceManagerAccess() {
        val managerA = entityManager.persist(
            User("managerA@test.com", "password", "A 관리자")
        )

        val managerB = entityManager.persist(
            User("managerB@test.com", "password", "B 관리자")
        )

        val workplaceA = entityManager.persist(
            Workplace("매장 A", "WORKA001")
        )

        val workplaceB = entityManager.persist(
            Workplace("매장 B", "WORKB001")
        )

        val joinedAt = LocalDateTime.of(2026, 9, 1, 9, 0)

        entityManager.persist(
            WorkplaceMember(
                workplaceA, managerA,
                WorkplaceRole.MANAGER, joinedAt
            )
        )

        entityManager.persist(
            WorkplaceMember(
                workplaceB, managerB,
                WorkplaceRole.MANAGER, joinedAt
            )
        )

        val managerAId = requireNotNull(managerA.id)
        val workplaceBId = requireNotNull(workplaceB.id)

        entityManager.flush()
        entityManager.clear()

        // A 매장 관리자가 B 매장의 초대코드에 접근
        val exception = assertThrows(BusinessException::class.java) {
            workplaceService.getInviteCode(workplaceBId, managerAId)
        }

        assertThat(exception.errorCode)
            .isEqualTo(WorkplaceErrorCode.NOT_WORKPLACE_MEMBER)
    }

    @Test
    @DisplayName("DB 재조회 후에도 현재 소속 근무지 정보를 DTO로 변환할 수 있다")
    fun getMyWorkplacesAfterReload() {
        val user = entityManager.persist(
            User("reload@test.com", "password", "직원")
        )

        val active = entityManager.persist(
            Workplace("현재 매장", "ACTIVE01")
        )

        val retired = entityManager.persist(
            Workplace("퇴사 매장", "RETIRED1")
        )

        val joinedAt = LocalDateTime.of(2026, 9, 1, 9, 0)

        entityManager.persist(
            WorkplaceMember(
                active, user, WorkplaceRole.MANAGER, joinedAt
            )
        )

        entityManager.persist(
            WorkplaceMember(
                retired,
                user,
                WorkplaceRole.EMPLOYEE,
                joinedAt,
                joinedAt.plusDays(10)
            )
        )

        val activeId = requireNotNull(active.id)

        entityManager.flush()
        entityManager.clear()

        // 실제 Service와 Repository를 통해 다시 조회
        val members = workplaceService.getMyWorkplaces(user)

        // 영속성 컨텍스트를 비워 프록시 의존 문제 확인
        entityManager.clear()

        assertThat(members).hasSize(1)

        val response = MyWorkplaceResponse.from(members[0])

        assertThat(response.workplaceId).isEqualTo(activeId)
        assertThat(response.name).isEqualTo("현재 매장")
        assertThat(response.role).isEqualTo(WorkplaceRole.MANAGER)
    }

    @Test
    @DisplayName("DB 재조회 후에도 현재 구성원의 User 정보를 정상 조회할 수 있다")
    fun getWorkplaceMembersAfterReload() {
        val manager = entityManager.persist(
            User("manager-load@test.com", "password", "관리자")
        )

        val employee = entityManager.persist(
            User("employee-load@test.com", "password", "홍길동")
        )

        val retired = entityManager.persist(
            User("retired-load@test.com", "password", "퇴사자")
        )

        val workplace = entityManager.persist(
            Workplace("조회 테스트 매장", "MEMBER01")
        )

        val joinedAt = LocalDateTime.of(2026, 9, 1, 9, 0)

        // 재직 중인 관리자
        entityManager.persist(
            WorkplaceMember(
                workplace, manager, WorkplaceRole.MANAGER, joinedAt
            )
        )

        // 재직 중인 직원
        entityManager.persist(
            WorkplaceMember(
                workplace, employee, WorkplaceRole.EMPLOYEE, joinedAt
            )
        )

        // 퇴사한 직원
        entityManager.persist(
            WorkplaceMember(
                workplace,
                retired,
                WorkplaceRole.EMPLOYEE,
                joinedAt,
                joinedAt.plusDays(10)
            )
        )

        val workplaceId = requireNotNull(workplace.id)
        val managerId = requireNotNull(manager.id)

        entityManager.flush()
        entityManager.clear()

        // 실제 관리자 권한 검증 + 구성원 조회
        val members = workplaceService.getWorkplaceMembers(workplaceId, managerId)

        // 조회한 엔티티를 영속성 컨텍스트에서 분리
        entityManager.clear()

        // 퇴사자는 제외되고 재직자 2명만 조회
        assertThat(members).hasSize(2)

        // User 연관관계를 통해 이메일을 정상적으로 읽는지 검증
        assertThat(members.map { member -> member.user.email })
            .containsExactlyInAnyOrder(
                "manager-load@test.com",
                "employee-load@test.com"
            )

        // 직원의 이름과 역할도 유지되는지 검증
        assertThat(members)
            .filteredOn { member: WorkplaceMember -> member.user.email.equals("employee-load@test.com") }
            .singleElement()
            .satisfies(
                ThrowingConsumer<WorkplaceMember> { member ->
                    assertThat(member.user.name)
                        .isEqualTo("홍길동")

                    assertThat(member.role)
                        .isEqualTo(WorkplaceRole.EMPLOYEE)
                }
            )
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("관리자 멤버십 저장 실패 시 근무지 생성도 롤백된다")
    fun rollbackWorkplaceCreation() {
        val workplaceName = "롤백 검증 매장"
        val beforeCount = workplaceRepository.count()

        val unsavedUser = User(
            "rollback@test.com",
            "password",
            "테스트 관리자"
        )

        // 멤버십 저장 실패 시 정확한 예외와 원인 검증
        assertThatThrownBy {
            workplaceService.createWorkplace(workplaceName, unsavedUser)
        }
            .isInstanceOf(InvalidDataAccessApiUsageException::class.java)
            .hasRootCauseInstanceOf(TransientPropertyValueException::class.java)

        // 실패 이후 DB에 근무지가 남아 있지 않아야 한다
        assertThat(workplaceRepository.count())
            .isEqualTo(beforeCount)

        assertThat(workplaceRepository.findAll())
            .noneMatch { workplace -> workplaceName.equals(workplace.name) }
    }

}
