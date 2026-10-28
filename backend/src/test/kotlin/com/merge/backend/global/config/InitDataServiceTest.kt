package com.merge.backend.global.config

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.config.InitData.InitDataService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.given
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
class InitDataServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var workplaceRepository: WorkplaceRepository

    @Mock
    private lateinit var workplaceMemberRepository: WorkplaceMemberRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var initDataService: InitDataService

    @BeforeEach
    fun setUp() {
        initDataService = InitDataService(
            userRepository,
            workplaceRepository,
            workplaceMemberRepository,
            passwordEncoder,
        )
        ReflectionTestUtils.setField(initDataService, "rawDefaultPassword", RAW_PASSWORD)
    }

    @Test
    @DisplayName("User가 하나라도 있으면 아무것도 만들지 않는다")
    fun skipWhenDataExists() {
        given(userRepository.count()).willReturn(1L)

        initDataService.init()

        verify(passwordEncoder, never()).encode(anyString())
        verify(userRepository, never()).save(any<User>())
        verify(workplaceRepository, never()).save(any<Workplace>())
        verify(workplaceMemberRepository, never()).save(any<WorkplaceMember>())
    }

    @Test
    @DisplayName("데이터가 없으면 매니저 1명·직원 2명과 근무지 1개를 만든다")
    fun createInitialData() {
        given(userRepository.count()).willReturn(0L)
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD)
        given(userRepository.save(any<User>()))
            .willAnswer { invocation -> invocation.getArgument<User>(0) }
        given(workplaceRepository.save(any<Workplace>()))
            .willAnswer { invocation -> invocation.getArgument<Workplace>(0) }

        initDataService.init()

        verify(passwordEncoder, times(1)).encode(RAW_PASSWORD)

        val userCaptor = argumentCaptor<User>()
        verify(userRepository, times(3)).save(userCaptor.capture())
        val users = userCaptor.allValues
        assertThat(users.map { it.email })
            .containsExactly("manager@test.com", "worker1@test.com", "worker2@test.com")
        assertThat(users.map { it.name })
            .containsExactly("김점장", "이알바", "박알바")
        assertThat(users.map { it.passwordHash })
            .containsOnly(ENCODED_PASSWORD)

        val workplaceCaptor = argumentCaptor<Workplace>()
        verify(workplaceRepository).save(workplaceCaptor.capture())
        val workplace = workplaceCaptor.lastValue
        assertThat(workplace.name).isEqualTo("강남 1호점")
        assertThat(workplace.inviteCode).isEqualTo("1234")

        val memberCaptor = argumentCaptor<WorkplaceMember>()
        verify(workplaceMemberRepository, times(3)).save(memberCaptor.capture())
        val members = memberCaptor.allValues
        assertThat(members.map { it.user })
            .containsExactlyElementsOf(users)
        assertThat(members.map { it.role })
            .containsExactly(WorkplaceRole.MANAGER, WorkplaceRole.EMPLOYEE, WorkplaceRole.EMPLOYEE)
        assertThat(members.map { it.workplace })
            .containsOnly(workplace)
        assertThat(members.map { it.leftAt })
            .containsOnlyNulls()
    }

    companion object {
        private const val RAW_PASSWORD = "raw-password"
        private const val ENCODED_PASSWORD = "encoded-password"
    }
}
