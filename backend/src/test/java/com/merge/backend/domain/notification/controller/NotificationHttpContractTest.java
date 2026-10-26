package com.merge.backend.domain.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.jayway.jsonpath.JsonPath;
import com.merge.backend.domain.notification.entity.Notification;
import com.merge.backend.domain.notification.entity.NotificationType;
import com.merge.backend.domain.notification.repository.NotificationRepository;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.user.repository.UserRepository;
import com.merge.backend.domain.user.service.UserService;
import com.merge.backend.domain.workplace.entity.Workplace;
import com.merge.backend.domain.workplace.entity.WorkplaceMember;
import com.merge.backend.domain.workplace.entity.WorkplaceRole;
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository;
import com.merge.backend.domain.workplace.repository.WorkplaceRepository;
import com.merge.backend.global.config.TestClock;
import com.merge.backend.global.config.TestTimeConfig;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeType;

/**
 * 알림 HTTP 계약 고정 (현재 Java 동작 기준, V5-J2).
 * Context Path(/api/v1)를 포함해 호출해 실제 인증 Filter와 Security 설정을 거친다.
 * 정상 Token 요청이 성공하는 것은 CustomAuthenticationFilter가 인증 정보를 넣었다는 뜻이다.
 * Test Transaction 안에서 실행하고 Rollback하므로 AFTER_COMMIT 알림 생성은 검증하지 않는다 (V5-J3).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
@Transactional
class NotificationHttpContractTest {

    private static final String CONTEXT_PATH = "/api/v1";

    // Asia/Seoul 기준 2026-09-15 10:00
    private static final Instant BASE_INSTANT = Instant.parse("2026-09-15T01:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkplaceRepository workplaceRepository;

    @Autowired
    private WorkplaceMemberRepository workplaceMemberRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestClock testClock;

    @BeforeEach
    void resetClock() {
        // TestClock은 Context가 공유하는 Bean이다. 앞선 Test가 바꾼 시각이 남지 않도록 매번 되돌린다
        testClock.setInstant(BASE_INSTANT);
    }

    @Test
    @DisplayName("Token 없이 알림 목록을 조회하면 401 AUTH-004를 돌려준다")
    void getNotificationsRequiresAuthentication() throws Exception {
        // when
        MvcResult result = mvc.perform(api(get(CONTEXT_PATH + "/notifications")))
            .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertErrorBody(body(result), "AUTH-004", "로그인 후 이용해주세요.");
    }

    @Test
    @DisplayName("Token 없이 읽음 처리하면 401 AUTH-004를 돌려주고 readAt은 바뀌지 않는다")
    void markAsReadRequiresAuthentication() throws Exception {
        // given
        WorkplaceMember member = activeMember(newUser(), newWorkplace());
        Notification notification = notification(member, BASE_INSTANT);
        flushAndClear();

        // when
        MvcResult result = mvc.perform(api(patch(readUri(notification.getId()))))
            .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertErrorBody(body(result), "AUTH-004", "로그인 후 이용해주세요.");
        assertThat(reloadReadAt(notification.getId())).isNull();
    }

    @Test
    @DisplayName("현재 소속의 내 알림만 최신순으로 돌려주고 날짜는 시간대 없는 문자열이다")
    void getNotificationsReturnsOnlyOwnActiveMembershipNotifications() throws Exception {
        // given
        User me = newUser();
        User other = newUser();
        Workplace workplace = newWorkplace();
        Workplace leftWorkplace = newWorkplace();
        WorkplaceMember myMember = activeMember(me, workplace);
        WorkplaceMember myLeftMember = leftMember(me, leftWorkplace);
        WorkplaceMember otherMember = activeMember(other, workplace);

        Notification oldest = notification(myMember, BASE_INSTANT);
        Instant laterInstant = Instant.parse("2026-09-15T02:00:00Z");
        Notification sameTimeFirst = notification(myMember, laterInstant);
        Notification sameTimeSecond = notification(myMember, laterInstant);
        oldest.markAsRead(LocalDateTime.of(2026, 9, 15, 12, 30));
        notification(myLeftMember, laterInstant);
        notification(otherMember, laterInstant);
        flushAndClear();

        // when
        MvcResult result = mvc.perform(api(get(CONTEXT_PATH + "/notifications"))
                .header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andReturn();
        String json = body(result);
        JsonNode data = objectMapper.readTree(json).get("data");

        // then: 공통 응답
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertSuccessBody(json, "알림 목록을 조회했습니다.");

        // then: 퇴사한 소속·다른 사용자의 알림 제외, createdAt desc → id desc
        assertThat(data.getNodeType()).isEqualTo(JsonNodeType.ARRAY);
        List<Long> ids = JsonPath.<List<Number>>read(json, "$.data[*].notificationId")
            .stream()
            .map(Number::longValue)
            .toList();
        assertThat(ids).containsExactly(
            sameTimeSecond.getId(),
            sameTimeFirst.getId(),
            oldest.getId()
        );

        // then: 항목 Field 이름과 자료형
        for (JsonNode item : data) {
            assertThat(fieldNames(item)).containsExactlyInAnyOrder(
                "notificationId", "workplaceId", "workplaceName",
                "type", "message", "createdAt", "readAt"
            );
            assertThat(item.get("notificationId").getNodeType()).isEqualTo(JsonNodeType.NUMBER);
            assertThat(item.get("workplaceId").getNodeType()).isEqualTo(JsonNodeType.NUMBER);
            assertThat(item.get("workplaceName").getNodeType()).isEqualTo(JsonNodeType.STRING);
            assertThat(item.get("type").getNodeType()).isEqualTo(JsonNodeType.STRING);
            assertThat(item.get("message").getNodeType()).isEqualTo(JsonNodeType.STRING);
            assertThat(item.get("createdAt").getNodeType()).isEqualTo(JsonNodeType.STRING);
        }
        assertThat(((Number) JsonPath.read(json, "$.data[0].workplaceId")).longValue())
            .isEqualTo(workplace.getId());
        assertThat((String) JsonPath.read(json, "$.data[0].workplaceName"))
            .isEqualTo(workplace.getName());
        assertThat((String) JsonPath.read(json, "$.data[0].type"))
            .isEqualTo("SCHEDULE_PUBLISHED");
        assertThat((String) JsonPath.read(json, "$.data[0].message"))
            .isEqualTo("알림 HTTP 계약 테스트");

        // then: 날짜 문자열 (V5-N-30). Asia/Seoul 현지 시각, 시간대 표기 없음
        assertThat((String) JsonPath.read(json, "$.data[0].createdAt"))
            .isEqualTo("2026-09-15T11:00:00");
        assertThat((String) JsonPath.read(json, "$.data[2].createdAt"))
            .isEqualTo("2026-09-15T10:00:00");
        assertThat((String) JsonPath.read(json, "$.data[2].readAt"))
            .isEqualTo("2026-09-15T12:30:00");

        // then: 읽지 않은 알림은 readAt key를 null로 유지 (Frontend는 readAt === null로 구분)
        assertThat(data.get(0).get("readAt").getNodeType()).isEqualTo(JsonNodeType.NULL);
        assertThat(data.get(1).get("readAt").getNodeType()).isEqualTo(JsonNodeType.NULL);
    }

    @Test
    @DisplayName("알림이 없으면 data는 빈 배열이다")
    void getNotificationsReturnsEmptyArrayWhenNoNotification() throws Exception {
        // given
        User me = newUser();
        activeMember(me, newWorkplace());
        flushAndClear();

        // when
        MvcResult result = mvc.perform(api(get(CONTEXT_PATH + "/notifications"))
                .header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andReturn();
        String json = body(result);
        JsonNode data = objectMapper.readTree(json).get("data");

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertSuccessBody(json, "알림 목록을 조회했습니다.");
        assertThat(data.getNodeType()).isEqualTo(JsonNodeType.ARRAY);
        assertThat(data.size()).isZero();
    }

    @Test
    @DisplayName("내 알림을 읽음 처리하면 응답과 DB의 readAt이 같고 다른 알림은 바뀌지 않는다")
    void markAsReadUpdatesOnlyTargetNotification() throws Exception {
        // given
        User me = newUser();
        WorkplaceMember member = activeMember(me, newWorkplace());
        Notification target = notification(member, BASE_INSTANT);
        Notification untouched = notification(member, BASE_INSTANT);
        flushAndClear();
        testClock.setInstant(Instant.parse("2026-09-15T03:00:00Z"));

        // when
        MvcResult result = mvc.perform(api(patch(readUri(target.getId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andReturn();
        String json = body(result);
        JsonNode data = objectMapper.readTree(json).get("data");

        // then: HTTP 응답
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertSuccessBody(json, "알림을 읽음 처리했습니다.");
        assertThat(fieldNames(data)).containsExactlyInAnyOrder("notificationId", "readAt");
        assertThat(((Number) JsonPath.read(json, "$.data.notificationId")).longValue())
            .isEqualTo(target.getId());
        // V5-N-30: readAt 날짜 문자열
        assertThat((String) JsonPath.read(json, "$.data.readAt"))
            .isEqualTo("2026-09-15T12:00:00");

        // then: DB
        flushAndClear();
        assertThat(reloadReadAt(target.getId()))
            .isEqualTo(LocalDateTime.of(2026, 9, 15, 12, 0));
        assertThat(reloadReadAt(untouched.getId())).isNull();
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 읽음 처리해도 200이고 최초 readAt을 유지한다")
    void markAsReadTwiceKeepsFirstReadAt() throws Exception {
        // given
        User me = newUser();
        WorkplaceMember member = activeMember(me, newWorkplace());
        Notification target = notification(member, BASE_INSTANT);
        flushAndClear();

        testClock.setInstant(Instant.parse("2026-09-15T03:00:00Z"));
        mvc.perform(api(patch(readUri(target.getId())))
            .header(HttpHeaders.AUTHORIZATION, bearer(me)));
        flushAndClear();
        testClock.setInstant(Instant.parse("2026-09-15T05:00:00Z"));

        // when
        MvcResult result = mvc.perform(api(patch(readUri(target.getId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andReturn();
        String json = body(result);

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertSuccessBody(json, "알림을 읽음 처리했습니다.");
        assertThat((String) JsonPath.read(json, "$.data.readAt"))
            .isEqualTo("2026-09-15T12:00:00");
        flushAndClear();
        assertThat(reloadReadAt(target.getId()))
            .isEqualTo(LocalDateTime.of(2026, 9, 15, 12, 0));
    }

    @Test
    @DisplayName("소수 초가 있는 시각도 읽음 응답과 DB 재조회 목록에서 같은 문자열로 나간다")
    void dateTimeWithFractionalSecondsContract() throws Exception {
        // given: 운영 Clock처럼 소수 초가 있는 시각
        User me = newUser();
        WorkplaceMember member = activeMember(me, newWorkplace());
        Notification target =
            notification(member, Instant.parse("2026-09-15T01:00:00.123456Z"));
        flushAndClear();
        testClock.setInstant(Instant.parse("2026-09-15T03:00:00.500Z"));

        // when: 읽음 응답은 메모리의 값, 목록은 DB에서 다시 읽은 값
        MvcResult readResult = mvc.perform(api(patch(readUri(target.getId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andReturn();
        flushAndClear();
        MvcResult listResult = mvc.perform(api(get(CONTEXT_PATH + "/notifications"))
                .header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andReturn();
        String readJson = body(readResult);
        String listJson = body(listResult);

        // then (V5-N-30)
        assertThat(readResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) JsonPath.read(readJson, "$.data.readAt"))
            .isEqualTo("2026-09-15T12:00:00.5");
        assertThat((String) JsonPath.read(listJson, "$.data[0].createdAt"))
            .isEqualTo("2026-09-15T10:00:00.123456");
        assertThat((String) JsonPath.read(listJson, "$.data[0].readAt"))
            .isEqualTo("2026-09-15T12:00:00.5");
        assertThat(reloadReadAt(target.getId()))
            .isEqualTo(LocalDateTime.of(2026, 9, 15, 12, 0, 0, 500_000_000));
    }

    @Test
    @DisplayName("존재하지 않는 알림을 읽음 처리하면 404 NOT-001을 돌려준다")
    void markAsReadFailsWhenNotificationNotFound() throws Exception {
        // given
        User me = newUser();
        activeMember(me, newWorkplace());
        flushAndClear();

        // when
        MvcResult result = mvc.perform(api(patch(readUri(Long.MAX_VALUE)))
                .header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertErrorBody(body(result), "NOT-001", "알림을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("다른 사용자의 알림을 읽음 처리하면 403 NOT-002이고 readAt은 바뀌지 않는다")
    void markAsReadFailsWhenNotificationBelongsToAnotherUser() throws Exception {
        // given
        User me = newUser();
        User owner = newUser();
        Workplace workplace = newWorkplace();
        activeMember(me, workplace);
        Notification othersNotification =
            notification(activeMember(owner, workplace), BASE_INSTANT);
        flushAndClear();

        // when
        MvcResult result = mvc.perform(api(patch(readUri(othersNotification.getId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertErrorBody(body(result), "NOT-002", "해당 알림에 접근할 권한이 없습니다.");
        flushAndClear();
        assertThat(reloadReadAt(othersNotification.getId())).isNull();
    }

    @Test
    @DisplayName("퇴사한 소속의 내 알림을 읽음 처리하면 403 NOT-002이고 readAt은 바뀌지 않는다")
    void markAsReadFailsWhenRecipientMembershipLeft() throws Exception {
        // given
        User me = newUser();
        activeMember(me, newWorkplace());
        Notification leftNotification =
            notification(leftMember(me, newWorkplace()), BASE_INSTANT);
        flushAndClear();

        // when
        MvcResult result = mvc.perform(api(patch(readUri(leftNotification.getId())))
                .header(HttpHeaders.AUTHORIZATION, bearer(me)))
            .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertErrorBody(body(result), "NOT-002", "해당 알림에 접근할 권한이 없습니다.");
        flushAndClear();
        assertThat(reloadReadAt(leftNotification.getId())).isNull();
    }

    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.contextPath(CONTEXT_PATH);
    }

    private static String readUri(Long notificationId) {
        return CONTEXT_PATH + "/notifications/" + notificationId + "/read";
    }

    private String bearer(User user) {
        return "Bearer " + userService.genAccessToken(user);
    }

    private User newUser() {
        String key = UUID.randomUUID().toString();
        return userRepository.save(
            new User("notification-http-" + key + "@test.com", "password-hash", "알림사용자")
        );
    }

    private Workplace newWorkplace() {
        String key = UUID.randomUUID().toString();
        return workplaceRepository.save(
            new Workplace("알림 계약 매장 " + key.substring(0, 8), "NOT-HTTP-" + key)
        );
    }

    private WorkplaceMember activeMember(User user, Workplace workplace) {
        return workplaceMemberRepository.save(
            new WorkplaceMember(
                workplace,
                user,
                WorkplaceRole.EMPLOYEE,
                LocalDateTime.of(2026, 9, 1, 9, 0)
            )
        );
    }

    private WorkplaceMember leftMember(User user, Workplace workplace) {
        return workplaceMemberRepository.save(
            new WorkplaceMember(
                workplace,
                user,
                WorkplaceRole.EMPLOYEE,
                LocalDateTime.of(2026, 9, 1, 9, 0),
                LocalDateTime.of(2026, 9, 10, 18, 0)
            )
        );
    }

    // createdAt은 Auditing이 Clock 기준으로 저장 시점에 정한다
    private Notification notification(WorkplaceMember member, Instant createdInstant) {
        testClock.setInstant(createdInstant);
        return notificationRepository.save(
            new Notification(member, NotificationType.SCHEDULE_PUBLISHED, "알림 HTTP 계약 테스트")
        );
    }

    // 메모리의 Entity가 아니라 DB 값을 읽도록 영속성 Context를 비운다
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private LocalDateTime reloadReadAt(Long notificationId) {
        return notificationRepository.findById(notificationId)
            .orElseThrow()
            .getReadAt();
    }

    private void assertSuccessBody(String json, String message) {
        JsonNode root = objectMapper.readTree(json);
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data");
        assertThat((Boolean) JsonPath.read(json, "$.success")).isTrue();
        assertThat((String) JsonPath.read(json, "$.code")).isEqualTo("200");
        assertThat((String) JsonPath.read(json, "$.message")).isEqualTo(message);
    }

    private void assertErrorBody(String json, String code, String message) {
        JsonNode root = objectMapper.readTree(json);
        assertThat(fieldNames(root))
            .containsExactlyInAnyOrder("success", "code", "message", "data");
        assertThat((Boolean) JsonPath.read(json, "$.success")).isFalse();
        assertThat((String) JsonPath.read(json, "$.code")).isEqualTo(code);
        assertThat((String) JsonPath.read(json, "$.message")).isEqualTo(message);
        assertThat(root.get("data").getNodeType()).isEqualTo(JsonNodeType.NULL);
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.properties().forEach(entry -> names.add(entry.getKey()));
        return names;
    }
}
