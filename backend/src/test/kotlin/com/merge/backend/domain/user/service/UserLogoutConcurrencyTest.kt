package com.merge.backend.domain.user.service

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

// 실제 Commit과 MySQL 행 Lock으로 순서를 강제하므로 Test Transaction(@Transactional)을 쓰지 않음
@SpringBootTest
@ActiveProfiles("test")
class UserLogoutConcurrencyTest {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    // 같은 DB를 다른 테스트와 같이 쓰므로 테스트마다 다른 이메일
    private lateinit var email: String

    @BeforeEach
    fun setUp() {
        email = "logout-race-${UUID.randomUUID()}@test.com"
        userRepository.save(User(email, "password-hash", "동시성유저"))
    }

    // 실제 DB에 남으니까 테스트마다 지움
    @AfterEach
    fun tearDown() {
        userRepository.findByEmail(email).ifPresent(userRepository::delete)
    }

    private fun user(): User = userRepository.findByEmail(email).orElseThrow()

    @Test
    @DisplayName("다른 기기 로그인이 Commit 전일 때 예전 토큰으로 로그아웃하면 기다렸다가 새 로그인을 지우지 않는다")
    fun logoutWaitsForNewerLoginAndKeepsIt() {
        // given: 기기 A 로그인 (Commit됨)
        val oldDeviceToken = userService.issueRefreshToken(user())
        var newDeviceToken: String? = null

        // when: 기기 B 로그인이 행을 잡고 Commit 전 대기 → 그동안 A의 쿠키로 로그아웃
        val logoutError = runSecondWhileFirstHoldsLock(
            first = {
                newDeviceToken = userService.issueRefreshToken(user())
                // UPDATE를 바로 보내서 Commit 전까지 행 Lock을 잡고 있게 함
                userRepository.flush()
            },
            second = { userService.logout(oldDeviceToken) },
        )

        // then: 로그아웃은 실패하지 않고, B의 토큰이 남음
        assertThat(logoutError).isNull()
        assertThat(newDeviceToken).isNotNull().isNotEqualTo(oldDeviceToken)
        assertThat(user().refreshToken).isEqualTo(newDeviceToken)
    }

    @Test
    @DisplayName("예전 토큰 로그아웃이 Commit 전일 때 다른 기기가 로그인하면 기다렸다가 새 로그인이 남는다")
    fun loginWaitsForLogoutAndNewerLoginRemains() {
        // given: 기기 A 로그인 (Commit됨), 기기 B 로그인 요청이 사용자를 먼저 읽어 둠 (UserService.login과 같음)
        val oldDeviceToken = userService.issueRefreshToken(user())
        val loginUser = user()
        var newDeviceToken: String? = null

        // when: A 로그아웃의 조건부 UPDATE가 행을 잡고 Commit 전 대기 → 그동안 B 로그인
        val loginError = runSecondWhileFirstHoldsLock(
            first = { userService.logout(oldDeviceToken) },
            second = { newDeviceToken = userService.issueRefreshToken(loginUser) },
        )

        // then: 로그인은 실패하지 않고, B의 토큰이 남음
        assertThat(loginError).isNull()
        assertThat(newDeviceToken).isNotNull().isNotEqualTo(oldDeviceToken)
        assertThat(user().refreshToken).isEqualTo(newDeviceToken)
    }

    // first를 Commit하지 않은 채로 멈추고 second를 실행. second가 Lock을 기다리는지 확인한 뒤 first를 Commit
    // (SubstituteRequestConcurrencyTest와 같은 방식)
    private fun runSecondWhileFirstHoldsLock(first: () -> Unit, second: () -> Unit): Throwable? {
        val executor = Executors.newFixedThreadPool(2)
        val firstExecuted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)

        try {
            val firstFuture: Future<*> = executor.submit(
                Runnable {
                    TransactionTemplate(transactionManager).executeWithoutResult {
                        first()
                        firstExecuted.countDown()
                        await(releaseFirst) // Commit하지 않고 대기
                    }
                }
            )

            assertThat(firstExecuted.await(10, SECONDS))
                .describedAs("first 작업이 Commit 전 상태로 대기해야 한다")
                .isTrue()

            val secondFuture: Future<Throwable?> = executor.submit(Callable { capture(second) })

            assertThatThrownBy { secondFuture.get(LOCK_WAIT_CHECK_MILLIS, MILLISECONDS) }
                .describedAs("first가 Commit하기 전에는 second가 Lock을 기다려야 한다")
                .isInstanceOf(TimeoutException::class.java)

            releaseFirst.countDown()
            firstFuture.get(10, SECONDS)

            return secondFuture.get(10, SECONDS)
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    companion object {
        private const val LOCK_WAIT_CHECK_MILLIS = 1_000L

        private fun capture(task: () -> Unit): Throwable? =
            try {
                task()
                null
            } catch (throwable: Throwable) {
                throwable
            }

        private fun await(latch: CountDownLatch) {
            try {
                check(latch.await(30, SECONDS)) { "대기 시간 초과" }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException(e)
            }
        }
    }
}
