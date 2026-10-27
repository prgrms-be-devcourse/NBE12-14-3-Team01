package com.merge.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.user.repository.UserRepository;
import com.merge.backend.domain.workplace.entity.Workplace;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.entity.WorkplaceRole;
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository;
import com.merge.backend.domain.workplace.repository.WorkplaceRepository;
import com.merge.backend.global.config.InitData.InitDataService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InitDataServiceTest {

    private static final String RAW_PASSWORD = "raw-password";
    private static final String ENCODED_PASSWORD = "encoded-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkplaceRepository workplaceRepository;

    @Mock
    private WorkplaceMemberRepository workplaceMemberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private InitDataService initDataService;

    @BeforeEach
    void setUp() {
        initDataService = new InitDataService(
            userRepository,
            workplaceRepository,
            workplaceMemberRepository,
            passwordEncoder
        );
        ReflectionTestUtils.setField(initDataService, "rawDefaultPassword", RAW_PASSWORD);
    }

    @Test
    @DisplayName("User가 하나라도 있으면 아무것도 만들지 않는다")
    void skipWhenDataExists() {
        given(userRepository.count()).willReturn(1L);

        initDataService.init();

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
        verify(workplaceRepository, never()).save(any(Workplace.class));
        verify(workplaceMemberRepository, never()).save(any(WorkplaceMember.class));
    }

    @Test
    @DisplayName("데이터가 없으면 매니저 1명·직원 2명과 근무지 1개를 만든다")
    void createInitialData() {
        given(userRepository.count()).willReturn(0L);
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);
        given(userRepository.save(any(User.class)))
            .willAnswer(invocation -> invocation.getArgument(0));
        given(workplaceRepository.save(any(Workplace.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        initDataService.init();

        verify(passwordEncoder, times(1)).encode(RAW_PASSWORD);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(3)).save(userCaptor.capture());
        List<User> users = userCaptor.getAllValues();
        assertThat(users).extracting(User::getEmail)
            .containsExactly("manager@test.com", "worker1@test.com", "worker2@test.com");
        assertThat(users).extracting(User::getName)
            .containsExactly("김점장", "이알바", "박알바");
        assertThat(users).extracting(User::getPasswordHash)
            .containsOnly(ENCODED_PASSWORD);

        ArgumentCaptor<Workplace> workplaceCaptor = ArgumentCaptor.forClass(Workplace.class);
        verify(workplaceRepository).save(workplaceCaptor.capture());
        Workplace workplace = workplaceCaptor.getValue();
        assertThat(workplace.getName()).isEqualTo("강남 1호점");
        assertThat(workplace.getInviteCode()).isEqualTo("1234");

        ArgumentCaptor<WorkplaceMember> memberCaptor =
            ArgumentCaptor.forClass(WorkplaceMember.class);
        verify(workplaceMemberRepository, times(3)).save(memberCaptor.capture());
        List<WorkplaceMember> members = memberCaptor.getAllValues();
        assertThat(members).extracting(WorkplaceMember::getUser)
            .containsExactlyElementsOf(users);
        assertThat(members).extracting(WorkplaceMember::getRole)
            .containsExactly(WorkplaceRole.MANAGER, WorkplaceRole.EMPLOYEE, WorkplaceRole.EMPLOYEE);
        assertThat(members).extracting(WorkplaceMember::getWorkplace)
            .containsOnly(workplace);
        assertThat(members).extracting(WorkplaceMember::getLeftAt)
            .containsOnlyNulls();
    }
}
