package com.merge.backend.global.entity

import com.merge.backend.global.config.TestClock
import com.merge.backend.global.config.TestTimeConfig
import com.merge.backend.global.config.TimeConfig
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.Instant
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
@Import(
    TimeConfig::class,
    TestTimeConfig::class,
)
class BaseEntityAuditingTest {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var testClock: TestClock

    @BeforeEach
    fun setUp() {
        testClock.setInstant(
            Instant.parse("2026-09-15T01:00:00Z")
        )
    }

    @Test
    fun 저장_시_Clock_기준으로_생성일과_수정일이_기록된다() {
        // given
        val entity = AuditingTestEntity("before")

        // when
        entityManager.persist(entity)
        entityManager.flush()

        // then
        val expected = LocalDateTime.of(
            2026, 9, 15, 10, 0
        )

        assertThat(entity.createDate).isEqualTo(expected)
        assertThat(entity.modifyDate).isEqualTo(expected)
    }

    @Test
    fun 수정_시_생성일은_유지되고_수정일만_Clock_기준으로_갱신된다() {
        // given
        val entity = AuditingTestEntity("before")

        entityManager.persist(entity)
        entityManager.flush()

        val entityId = entity.id
        val createdAt = entity.createDate

        testClock.setInstant(
            Instant.parse("2026-09-15T02:00:00Z")
        )

        // when
        entity.changeName("after")
        entityManager.flush()
        entityManager.clear()

        // DB에서 다시 조회
        val found = entityManager.find(AuditingTestEntity::class.java, entityId)

        // then
        val expectedModifiedAt = LocalDateTime.of(2026, 9, 15, 11, 0)

        assertThat(found.createDate).isEqualTo(createdAt)
        assertThat(found.modifyDate).isEqualTo(expectedModifiedAt)
    }
}
