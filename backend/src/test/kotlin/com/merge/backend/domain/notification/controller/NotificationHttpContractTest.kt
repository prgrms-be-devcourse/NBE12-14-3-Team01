package com.merge.backend.domain.notification.controller

import com.jayway.jsonpath.JsonPath
import com.merge.backend.domain.notification.entity.Notification
import com.merge.backend.domain.notification.entity.NotificationType
import com.merge.backend.domain.notification.repository.NotificationRepository
import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.user.service.UserService
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import jakarta.persistence.EntityManager
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.JsonNodeType

/**
 * 알림 HTTP 계약 고정. Context Path를 포함해 실제 인증 Filter와 Security 설정을 거친다.
 * Test Transaction은 Rollback되므로 AFTER_COMMIT 알림 생성은 여기서 검증하지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestTimeConfig::class)
@Transactional
class NotificationHttpContractTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var workplaceRepository: WorkplaceRepository
    @Autowired private lateinit var workplaceMemberRepository: WorkplaceMemberRepository
    @Autowired private lateinit var notificationRepository: NotificationRepository
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var testClock: TestClock

    @BeforeEach
    fun resetClock() {
        testClock.setInstant(BASE_INSTANT)
    }

    @Test
    @DisplayName("Token 없이 알림 목록을 조회하면 401 AUTH-004를 돌려준다")
    fun getNotificationsRequiresAuthentication() {
        val result = mvc.perform(api(get("$CONTEXT_PATH/notifications"))).andReturn()
        assertThat(result.response.status).isEqualTo(401)
        assertErrorBody(body(result), "AUTH-004", "로그인 후 이용해주세요.")
    }

    @Test
    @DisplayName("Token 없이 읽음 처리하면 401 AUTH-004를 돌려주고 readAt은 바뀌지 않는다")
    fun markAsReadRequiresAuthentication() {
        val member = activeMember(newUser(), newWorkplace())
        val notification = notification(member, BASE_INSTANT)
        flushAndClear()
        val result = mvc.perform(api(patch(readUri(checkNotNull(notification.id))))).andReturn()
        assertThat(result.response.status).isEqualTo(401)
        assertErrorBody(body(result), "AUTH-004", "로그인 후 이용해주세요.")
        assertThat(reloadReadAt(checkNotNull(notification.id))).isNull()
    }

    @Test
    @DisplayName("현재 소속의 내 알림만 최신순으로 돌려주고 날짜는 시간대 없는 문자열이다")
    fun getNotificationsReturnsOnlyOwnActiveMembershipNotifications() {
        val me = newUser()
        val other = newUser()
        val workplace = newWorkplace()
        val leftWorkplace = newWorkplace()
        val myMember = activeMember(me, workplace)
        val myLeftMember = leftMember(me, leftWorkplace)
        val otherMember = activeMember(other, workplace)
        val oldest = notification(myMember, BASE_INSTANT)
        val laterInstant = Instant.parse("2026-09-15T02:00:00Z")
        val sameTimeFirst = notification(myMember, laterInstant)
        val sameTimeSecond = notification(myMember, laterInstant)
        oldest.markAsRead(LocalDateTime.of(2026, 9, 15, 12, 30))
        notification(myLeftMember, laterInstant)
        notification(otherMember, laterInstant)
        flushAndClear()

        val result = mvc.perform(api(get("$CONTEXT_PATH/notifications"))
            .header(HttpHeaders.AUTHORIZATION, bearer(me))).andReturn()
        val json = body(result)
        val data = objectMapper.readTree(json).get("data")
        assertThat(result.response.status).isEqualTo(200)
        assertSuccessBody(json, "알림 목록을 조회했습니다.")
        assertThat(data.nodeType).isEqualTo(JsonNodeType.ARRAY)
        val ids = JsonPath.read<List<Number>>(json, "$.data[*].notificationId").map(Number::toLong)
        assertThat(ids).containsExactly(sameTimeSecond.id, sameTimeFirst.id, oldest.id)
        for (item in data) {
            assertThat(fieldNames(item)).containsExactlyInAnyOrder(
                "notificationId", "workplaceId", "workplaceName",
                "type", "message", "createdAt", "readAt",
            )
            assertThat(item.get("notificationId").nodeType).isEqualTo(JsonNodeType.NUMBER)
            assertThat(item.get("workplaceId").nodeType).isEqualTo(JsonNodeType.NUMBER)
            assertThat(item.get("workplaceName").nodeType).isEqualTo(JsonNodeType.STRING)
            assertThat(item.get("type").nodeType).isEqualTo(JsonNodeType.STRING)
            assertThat(item.get("message").nodeType).isEqualTo(JsonNodeType.STRING)
            assertThat(item.get("createdAt").nodeType).isEqualTo(JsonNodeType.STRING)
        }
        assertThat(JsonPath.read<Number>(json, "$.data[0].workplaceId").toLong()).isEqualTo(workplace.id)
        assertThat(JsonPath.read<String>(json, "$.data[0].workplaceName")).isEqualTo(workplace.name)
        assertThat(JsonPath.read<String>(json, "$.data[0].type")).isEqualTo("SCHEDULE_PUBLISHED")
        assertThat(JsonPath.read<String>(json, "$.data[0].message")).isEqualTo("알림 HTTP 계약 테스트")
        assertThat(JsonPath.read<String>(json, "$.data[0].createdAt")).isEqualTo("2026-09-15T11:00:00")
        assertThat(JsonPath.read<String>(json, "$.data[2].createdAt")).isEqualTo("2026-09-15T10:00:00")
        assertThat(JsonPath.read<String>(json, "$.data[2].readAt")).isEqualTo("2026-09-15T12:30:00")
        assertThat(data.get(0).get("readAt").nodeType).isEqualTo(JsonNodeType.NULL)
        assertThat(data.get(1).get("readAt").nodeType).isEqualTo(JsonNodeType.NULL)
    }

    @Test
    @DisplayName("알림이 없으면 data는 빈 배열이다")
    fun getNotificationsReturnsEmptyArrayWhenNoNotification() {
        val me = newUser()
        activeMember(me, newWorkplace())
        flushAndClear()
        val result = mvc.perform(api(get("$CONTEXT_PATH/notifications"))
            .header(HttpHeaders.AUTHORIZATION, bearer(me))).andReturn()
        val json = body(result)
        val data = objectMapper.readTree(json).get("data")
        assertThat(result.response.status).isEqualTo(200)
        assertSuccessBody(json, "알림 목록을 조회했습니다.")
        assertThat(data.nodeType).isEqualTo(JsonNodeType.ARRAY)
        assertThat(data.size()).isZero()
    }

    @Test
    @DisplayName("내 알림을 읽음 처리하면 응답과 DB의 readAt이 같고 다른 알림은 바뀌지 않는다")
    fun markAsReadUpdatesOnlyTargetNotification() {
        val me = newUser()
        val member = activeMember(me, newWorkplace())
        val target = notification(member, BASE_INSTANT)
        val untouched = notification(member, BASE_INSTANT)
        flushAndClear()
        testClock.setInstant(Instant.parse("2026-09-15T03:00:00Z"))
        val result = mvc.perform(api(patch(readUri(checkNotNull(target.id))))
            .header(HttpHeaders.AUTHORIZATION, bearer(me))).andReturn()
        val json = body(result)
        val data = objectMapper.readTree(json).get("data")
        assertThat(result.response.status).isEqualTo(200)
        assertSuccessBody(json, "알림을 읽음 처리했습니다.")
        assertThat(fieldNames(data)).containsExactlyInAnyOrder("notificationId", "readAt")
        assertThat(JsonPath.read<Number>(json, "$.data.notificationId").toLong()).isEqualTo(target.id)
        assertThat(JsonPath.read<String>(json, "$.data.readAt")).isEqualTo("2026-09-15T12:00:00")
        flushAndClear()
        assertThat(reloadReadAt(checkNotNull(target.id))).isEqualTo(LocalDateTime.of(2026, 9, 15, 12, 0))
        assertThat(reloadReadAt(checkNotNull(untouched.id))).isNull()
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 읽음 처리해도 200이고 최초 readAt을 유지한다")
    fun markAsReadTwiceKeepsFirstReadAt() {
        val me = newUser()
        val member = activeMember(me, newWorkplace())
        val target = notification(member, BASE_INSTANT)
        flushAndClear()
        testClock.setInstant(Instant.parse("2026-09-15T03:00:00Z"))
        mvc.perform(api(patch(readUri(checkNotNull(target.id))))
            .header(HttpHeaders.AUTHORIZATION, bearer(me)))
        flushAndClear()
        testClock.setInstant(Instant.parse("2026-09-15T05:00:00Z"))
        val result = mvc.perform(api(patch(readUri(checkNotNull(target.id))))
            .header(HttpHeaders.AUTHORIZATION, bearer(me))).andReturn()
        val json = body(result)
        assertThat(result.response.status).isEqualTo(200)
        assertSuccessBody(json, "알림을 읽음 처리했습니다.")
        assertThat(JsonPath.read<String>(json, "$.data.readAt")).isEqualTo("2026-09-15T12:00:00")
        flushAndClear()
        assertThat(reloadReadAt(checkNotNull(target.id))).isEqualTo(LocalDateTime.of(2026, 9, 15, 12, 0))
    }

    @Test
    @DisplayName("소수 초가 있는 시각도 읽음 응답과 DB 재조회 목록에서 같은 문자열로 나간다")
    fun dateTimeWithFractionalSecondsContract() {
        val me = newUser()
        val member = activeMember(me, newWorkplace())
        val target = notification(member, Instant.parse("2026-09-15T01:00:00.123456Z"))
        flushAndClear()
        testClock.setInstant(Instant.parse("2026-09-15T03:00:00.500Z"))
        val readResult = mvc.perform(api(patch(readUri(checkNotNull(target.id))))
            .header(HttpHeaders.AUTHORIZATION, bearer(me))).andReturn()
        flushAndClear()
        val listResult = mvc.perform(api(get("$CONTEXT_PATH/notifications"))
            .header(HttpHeaders.AUTHORIZATION, bearer(me))).andReturn()
        val readJson = body(readResult)
        val listJson = body(listResult)
        assertThat(readResult.response.status).isEqualTo(200)
        assertThat(listResult.response.status).isEqualTo(200)
        assertThat(JsonPath.read<String>(readJson, "$.data.readAt")).isEqualTo("2026-09-15T12:00:00.5")
        assertThat(JsonPath.read<String>(listJson, "$.data[0].createdAt"))
            .isEqualTo("2026-09-15T10:00:00.123456")
        assertThat(JsonPath.read<String>(listJson, "$.data[0].readAt")).isEqualTo("2026-09-15T12:00:00.5")
        assertThat(reloadReadAt(checkNotNull(target.id)))
            .isEqualTo(LocalDateTime.of(2026, 9, 15, 12, 0, 0, 500_000_000))
    }

    @Test
    @DisplayName("존재하지 않는 알림을 읽음 처리하면 404 NOT-001을 돌려준다")
    fun markAsReadFailsWhenNotificationNotFound() {
        val me = newUser()
        activeMember(me, newWorkplace())
        flushAndClear()
        val result = mvc.perform(api(patch(readUri(Long.MAX_VALUE)))
            .header(HttpHeaders.AUTHORIZATION, bearer(me))).andReturn()
        assertThat(result.response.status).isEqualTo(404)
        assertErrorBody(body(result), "NOT-001", "알림을 찾을 수 없습니다.")
    }

    @Test
    @DisplayName("다른 사용자의 알림을 읽음 처리하면 403 NOT-002이고 readAt은 바뀌지 않는다")
    fun markAsReadFailsWhenNotificationBelongsToAnotherUser() {
        val me = newUser()
        val owner = newUser()
        val workplace = newWorkplace()
        activeMember(me, workplace)
        val othersNotification = notification(activeMember(owner, workplace), BASE_INSTANT)
        flushAndClear()
        val result = mvc.perform(api(patch(readUri(checkNotNull(othersNotification.id))))
            .header(HttpHeaders.AUTHORIZATION, bearer(me))).andReturn()
        assertThat(result.response.status).isEqualTo(403)
        assertErrorBody(body(result), "NOT-002", "해당 알림에 접근할 권한이 없습니다.")
        flushAndClear()
        assertThat(reloadReadAt(checkNotNull(othersNotification.id))).isNull()
    }

    @Test
    @DisplayName("퇴사한 소속의 내 알림을 읽음 처리하면 403 NOT-002이고 readAt은 바뀌지 않는다")
    fun markAsReadFailsWhenRecipientMembershipLeft() {
        val me = newUser()
        activeMember(me, newWorkplace())
        val leftNotification = notification(leftMember(me, newWorkplace()), BASE_INSTANT)
        flushAndClear()
        val result = mvc.perform(api(patch(readUri(checkNotNull(leftNotification.id))))
            .header(HttpHeaders.AUTHORIZATION, bearer(me))).andReturn()
        assertThat(result.response.status).isEqualTo(403)
        assertErrorBody(body(result), "NOT-002", "해당 알림에 접근할 권한이 없습니다.")
        flushAndClear()
        assertThat(reloadReadAt(checkNotNull(leftNotification.id))).isNull()
    }

    private fun api(builder: MockHttpServletRequestBuilder): MockHttpServletRequestBuilder =
        builder.contextPath(CONTEXT_PATH)

    private fun readUri(notificationId: Long): String = "$CONTEXT_PATH/notifications/$notificationId/read"

    private fun bearer(user: User): String = "Bearer " + userService.genAccessToken(user)

    private fun newUser(): User {
        val key = UUID.randomUUID().toString()
        return userRepository.save(User("notification-http-${key}@test.com", "password-hash", "알림사용자"))
    }

    private fun newWorkplace(): Workplace {
        val key = UUID.randomUUID().toString()
        return workplaceRepository.save(Workplace("알림 계약 매장 " + key.substring(0, 8), "NOT-HTTP-" + key))
    }

    private fun activeMember(user: User, workplace: Workplace): WorkplaceMember =
        workplaceMemberRepository.save(WorkplaceMember(
            workplace, user, WorkplaceRole.EMPLOYEE, LocalDateTime.of(2026, 9, 1, 9, 0),
        ))

    private fun leftMember(user: User, workplace: Workplace): WorkplaceMember =
        workplaceMemberRepository.save(WorkplaceMember(
            workplace, user, WorkplaceRole.EMPLOYEE, LocalDateTime.of(2026, 9, 1, 9, 0),
            LocalDateTime.of(2026, 9, 10, 18, 0),
        ))

    private fun notification(member: WorkplaceMember, createdInstant: Instant): Notification {
        testClock.setInstant(createdInstant)
        return notificationRepository.save(Notification(
            member, NotificationType.SCHEDULE_PUBLISHED, "알림 HTTP 계약 테스트",
        ))
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun reloadReadAt(notificationId: Long): LocalDateTime? =
        notificationRepository.findById(notificationId).orElseThrow().readAt

    private fun assertSuccessBody(json: String, message: String) {
        val root = objectMapper.readTree(json)
        assertThat(fieldNames(root)).containsExactlyInAnyOrder("success", "code", "message", "data")
        assertThat(JsonPath.read<Boolean>(json, "$.success")).isTrue()
        assertThat(JsonPath.read<String>(json, "$.code")).isEqualTo("200")
        assertThat(JsonPath.read<String>(json, "$.message")).isEqualTo(message)
    }

    private fun assertErrorBody(json: String, code: String, message: String) {
        val root = objectMapper.readTree(json)
        assertThat(fieldNames(root)).containsExactlyInAnyOrder("success", "code", "message", "data")
        assertThat(JsonPath.read<Boolean>(json, "$.success")).isFalse()
        assertThat(JsonPath.read<String>(json, "$.code")).isEqualTo(code)
        assertThat(JsonPath.read<String>(json, "$.message")).isEqualTo(message)
        assertThat(root.get("data").nodeType).isEqualTo(JsonNodeType.NULL)
    }

    private fun body(result: MvcResult): String = result.response.getContentAsString(StandardCharsets.UTF_8)

    private fun fieldNames(node: JsonNode): Set<String> = node.properties().map { it.key }.toSet()

    private companion object {
        private const val CONTEXT_PATH = "/api/v1"
        private val BASE_INSTANT = Instant.parse("2026-09-15T01:00:00Z")
    }
}
