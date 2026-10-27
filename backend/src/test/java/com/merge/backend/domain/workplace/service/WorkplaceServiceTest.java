
package com.merge.backend.domain.workplace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.workplace.entity.Workplace;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.entity.WorkplaceRole;
import com.merge.backend.domain.workplace.exception.WorkplaceErrorCode;
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository;
import com.merge.backend.domain.workplace.repository.WorkplaceRepository;
import com.merge.backend.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WorkplaceServiceTest {

    @Mock
    private WorkplaceRepository workplaceRepository;

    @Mock
    private WorkplaceMemberRepository workplaceMemberRepository;

    @Mock
    private WorkplaceMemberService workplaceMemberService;

    @Mock
    private User user;

    private WorkplaceService workplaceService;

    // 가입 시간 검증을 위해 시계를 고정 (한국 시간 09:00)
    private final Clock clock = Clock.fixed(
        Instant.parse("2026-09-16T00:00:00Z"),
        ZoneId.of("Asia/Seoul")
    );

    @BeforeEach
    void setUp() {
        workplaceService = new WorkplaceService(
            clock,
            workplaceRepository,
            workplaceMemberRepository,
            workplaceMemberService
        );
    }

    @Test
    @DisplayName("초대코드로 참여하면 EMPLOYEE 멤버십이 생성된다")
    void joinAsEmployee() {
        Workplace workplace =
            new Workplace("테스트 매장", "ABCD1234");

        when(workplaceRepository.findByInviteCode("ABCD1234"))
            .thenReturn(Optional.of(workplace));

        // 실제 DB 대신 전달받은 멤버십을 반환하도록 Mock 설정
        when(workplaceMemberRepository.save(any(WorkplaceMember.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        WorkplaceMember result =
            workplaceService.joinWorkplace("ABCD1234", user);

        assertSame(workplace, result.getWorkplace());
        assertSame(user, result.getUser());
        assertEquals(WorkplaceRole.EMPLOYEE, result.getRole());

        assertEquals(
            LocalDateTime.of(2026, 9, 16, 9, 0),
            result.getJoinedAt()
        );

        assertNull(result.getLeftAt());

        verify(workplaceMemberRepository)
            .existsByWorkplaceAndUser(workplace, user);

        verify(workplaceMemberRepository).save(result);
    }

    @Test
    @DisplayName("유효하지 않은 초대코드로 참여하면 저장하지 않는다")
    void rejectInvalidInviteCode() {
        when(workplaceRepository.findByInviteCode("INVALID"))
            .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> workplaceService.joinWorkplace("INVALID", user)
        );

        assertSame(
            WorkplaceErrorCode.INVALID_INVITE_CODE,
            exception.getErrorCode()
        );

        verifyNoInteractions(workplaceMemberRepository);
    }

    @Test
    @DisplayName("존재하지 않는 근무지의 구성원은 조회할 수 없다")
    void rejectMissingWorkplace() {
        when(workplaceRepository.findById(10L))
            .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> workplaceService.getWorkplaceMembers(10L, 1L)
        );

        assertSame(
            WorkplaceErrorCode.WORKPLACE_NOT_FOUND,
            exception.getErrorCode()
        );

        verifyNoInteractions(workplaceMemberService);
        verifyNoInteractions(workplaceMemberRepository);
    }

    @Test
    @DisplayName("근무지를 생성하면 생성자가 MANAGER로 등록된다")
    void createWorkplaceWithManager() {
        when(workplaceRepository.existsByInviteCode(any()))
            .thenReturn(false);

        when(workplaceMemberRepository.save(any(WorkplaceMember.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        WorkplaceMember result =
            workplaceService.createWorkplace("테스트 매장", user);

        // Mock 저장소로 전달된 객체를 캡처해 내부 값을 검증
        ArgumentCaptor<Workplace> workplaceCaptor =
            ArgumentCaptor.forClass(Workplace.class);

        ArgumentCaptor<WorkplaceMember> memberCaptor =
            ArgumentCaptor.forClass(WorkplaceMember.class);

        verify(workplaceRepository).save(workplaceCaptor.capture());
        verify(workplaceMemberRepository).save(memberCaptor.capture());

        Workplace savedWorkplace = workplaceCaptor.getValue();
        WorkplaceMember savedMember = memberCaptor.getValue();

        assertEquals("테스트 매장", savedWorkplace.getName());

        assertTrue(
            savedWorkplace.getInviteCode().matches("[A-Z0-9]{8}")
        );

        assertSame(savedWorkplace, savedMember.getWorkplace());
        assertSame(user, savedMember.getUser());
        assertEquals(WorkplaceRole.MANAGER, savedMember.getRole());

        assertEquals(
            LocalDateTime.of(2026, 9, 16, 9, 0),
            savedMember.getJoinedAt()
        );

        assertNull(savedMember.getLeftAt());

        assertSame(savedMember, result);
    }

    @Test
    @DisplayName("이미 참여 중인 사용자는 다시 참여할 수 없다")
    void rejectDuplicateJoin() {
        Workplace workplace = mock(Workplace.class);

        when(workplaceRepository.findByInviteCode("ABC123"))
            .thenReturn(Optional.of(workplace));

        when(workplaceMemberRepository.existsByWorkplaceAndUser(workplace, user))
            .thenReturn(true);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> workplaceService.joinWorkplace("ABC123", user)
        );

        assertSame(
            WorkplaceErrorCode.ALREADY_JOINED_WORKPLACE,
            exception.getErrorCode()
        );

        verify(workplaceMemberRepository, never())
            .save(any(WorkplaceMember.class));
    }

    @Test
    @DisplayName("사용자의 소속 Workplace 목록을 조회한다")
    void getMyWorkplaces() {
        Workplace workplace = mock(Workplace.class);
        WorkplaceMember member = mock(WorkplaceMember.class);

        when(user.getId()).thenReturn(1L);
        when(workplaceMemberRepository.findAllByUser_IdAndLeftAtIsNull(1L))
            .thenReturn(List.of(member));

        when(member.getWorkplace()).thenReturn(workplace);
        when(workplace.getId()).thenReturn(10L);
        when(workplace.getName()).thenReturn("테스트 매장");
        when(member.getRole()).thenReturn(WorkplaceRole.MANAGER);

        List<WorkplaceMember> response =
            workplaceService.getMyWorkplaces(user);

        assertEquals(1, response.size());
        assertEquals(10L, response.get(0).getWorkplace().getId());
        assertEquals("테스트 매장", response.get(0).getWorkplace().getName());
        assertEquals(WorkplaceRole.MANAGER, response.get(0).getRole());
    }

    @Test
    @DisplayName("소속 Workplace가 없으면 빈 목록을 반환한다")
    void getEmptyWorkplaces() {
        when(user.getId()).thenReturn(1L);
        when(workplaceMemberRepository.findAllByUser_IdAndLeftAtIsNull(1L))
            .thenReturn(List.of());

        List<WorkplaceMember> response =
            workplaceService.getMyWorkplaces(user);

        assertEquals(0, response.size());
    }

    @Test
    @DisplayName("관리자는 Workplace의 현재 멤버 목록을 조회한다")
    void getCurrentMembers() {
        Workplace workplace = mock(Workplace.class);
        User memberUser = mock(User.class);
        WorkplaceMember member = mock(WorkplaceMember.class);

        when(workplaceRepository.findById(10L))
            .thenReturn(Optional.of(workplace));

        when(workplaceMemberRepository
            .findAllByWorkplace_IdAndLeftAtIsNull(10L))
            .thenReturn(List.of(member));

        when(member.getId()).thenReturn(100L);
        when(member.getUser()).thenReturn(memberUser);
        when(memberUser.getName()).thenReturn("홍길동");
        when(memberUser.getEmail()).thenReturn("test@test.com");
        when(member.getRole()).thenReturn(WorkplaceRole.EMPLOYEE);

        List<WorkplaceMember> response =
            workplaceService.getWorkplaceMembers(10L, 1L);

        assertEquals(100L, response.get(0).getId());
        assertEquals("홍길동", response.get(0).getUser().getName());
        assertEquals("test@test.com", response.get(0).getUser().getEmail());
        assertEquals(WorkplaceRole.EMPLOYEE, response.get(0).getRole());

        verify(workplaceMemberService).requireManager(1L, 10L);
    }

    @Test
    @DisplayName("관리자는 Workplace의 초대 코드를 조회한다")
    void getInviteCode() {
        Workplace workplace = mock(Workplace.class);

        when(workplaceRepository.findById(10L))
            .thenReturn(Optional.of(workplace));
        when(workplace.getId()).thenReturn(10L);
        when(workplace.getInviteCode()).thenReturn("ABCD1234");

        Workplace response =
            workplaceService.getInviteCode(10L, 1L);

        assertEquals(10L, response.getId());
        assertEquals("ABCD1234", response.getInviteCode());

        verify(workplaceMemberService).requireManager(1L, 10L);
    }

    @Test
    @DisplayName("관리자 권한 검증 실패 시 구성원 목록을 조회하지 않는다")
    void rejectUnauthorizedMemberList() {
        Workplace workplace =
            new Workplace("테스트 매장", "ABCD1234");

        when(workplaceRepository.findById(10L))
            .thenReturn(Optional.of(workplace));

        when(workplaceMemberService.requireManager(1L, 10L))
            .thenThrow(new BusinessException(
                WorkplaceErrorCode.MANAGER_REQUIRED
            ));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> workplaceService.getWorkplaceMembers(10L, 1L)
        );

        assertSame(
            WorkplaceErrorCode.MANAGER_REQUIRED,
            exception.getErrorCode()
        );

        verify(workplaceMemberRepository, never())
            .findAllByWorkplace_IdAndLeftAtIsNull(10L);
    }

    @Test
    @DisplayName("중복 초대코드가 생성되면 다시 생성한다")
    void retryDuplicateInviteCode() {
        // 첫 번째는 중복, 두 번째는 사용 가능하도록 설정
        when(workplaceRepository.existsByInviteCode(any()))
            .thenReturn(true, false);

        when(workplaceMemberRepository.save(any(WorkplaceMember.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        workplaceService.createWorkplace("테스트 매장", user);

        verify(workplaceRepository, times(2))
            .existsByInviteCode(any());

        verify(workplaceRepository)
            .save(any(Workplace.class));

        ArgumentCaptor<String> codeCaptor =
            ArgumentCaptor.forClass(String.class);

        verify(workplaceRepository, times(2))
            .existsByInviteCode(codeCaptor.capture());

        List<String> checkedCodes = codeCaptor.getAllValues();

        ArgumentCaptor<Workplace> workplaceCaptor =
            ArgumentCaptor.forClass(Workplace.class);

        verify(workplaceRepository)
            .save(workplaceCaptor.capture());

        Workplace savedWorkplace = workplaceCaptor.getValue();

        // 중복 검사에서 최종 사용한 코드가 실제 저장된 코드여야 한다
        assertEquals(
            checkedCodes.get(1),
            savedWorkplace.getInviteCode()
        );

    }


    @Test
    @DisplayName("관리자 권한이 없으면 초대코드를 조회할 수 없다")
    void rejectUnauthorizedInviteCode() {
        // 관리자 권한 검증 실패 상황
        Workplace workplace =
            new Workplace("테스트 매장", "ABCD1234");

        when(workplaceRepository.findById(10L))
            .thenReturn(Optional.of(workplace));

        when(workplaceMemberService.requireManager(1L, 10L))
            .thenThrow(new BusinessException(
                WorkplaceErrorCode.MANAGER_REQUIRED
            ));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> workplaceService.getInviteCode(10L, 1L)
        );

        assertSame(
            WorkplaceErrorCode.MANAGER_REQUIRED,
            exception.getErrorCode()
        );
    }


    @Test
    @DisplayName("존재하지 않는 근무지의 초대코드는 조회할 수 없다")
    void rejectMissingWorkplaceInviteCode() {
        when(workplaceRepository.findById(999L))
            .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> workplaceService.getInviteCode(999L, 1L)
        );

        assertSame(
            WorkplaceErrorCode.WORKPLACE_NOT_FOUND,
            exception.getErrorCode()
        );

        // 근무지가 없으면 권한 검증까지 진행하지 않는다
        verifyNoInteractions(workplaceMemberService);
        verifyNoInteractions(workplaceMemberRepository);
    }


    @Test
    @DisplayName("구성원이 없는 근무지는 빈 목록을 반환한다")
    void getEmptyWorkplaceMembers() {
        Workplace workplace = mock(Workplace.class);

        when(workplaceRepository.findById(10L))
            .thenReturn(Optional.of(workplace));

        when(workplaceMemberRepository
            .findAllByWorkplace_IdAndLeftAtIsNull(10L))
            .thenReturn(List.of());

        List<WorkplaceMember> result =
            workplaceService.getWorkplaceMembers(10L, 1L);

        assertTrue(result.isEmpty());

        // 구성원 목록이 비어 있어도 관리자 권한을 확인한다
        verify(workplaceMemberService)
            .requireManager(1L, 10L);

        verify(workplaceMemberRepository)
            .findAllByWorkplace_IdAndLeftAtIsNull(10L);
    }


}
