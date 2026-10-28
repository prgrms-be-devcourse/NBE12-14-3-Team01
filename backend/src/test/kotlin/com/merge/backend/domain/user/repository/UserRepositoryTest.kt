package com.merge.backend.domain.user.repository

import com.merge.backend.domain.user.entity.User
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    @DisplayName("같은 이메일로 두 번 저장하면 DB가 거부한다")
    fun saveDuplicateEmail_rejectedByDatabase() {
        // 다른 Test 데이터와 겹치지 않는 이메일
        val email = "dup-" + UUID.randomUUID() + "@test.com"
        userRepository.saveAndFlush(User(email, "hash", "첫번째"))

        // 서비스의 중복 확인을 거치지 않고 DB 제약만 확인
        assertThatThrownBy { userRepository.saveAndFlush(User(email, "hash", "두번째")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
