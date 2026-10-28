package com.merge.backend.domain.workplace.service

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import java.sql.Connection
import java.time.LocalDateTime
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.annotation.DirtiesContext
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionSynchronizationManager

/** 외부 Transaction 없이 Spring proxy를 호출해 미리보기 전용 격리 수준을 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LeavePreviewTransactionTest {
    @Autowired private lateinit var service: LeavePreviewService
    @Autowired private lateinit var dataSource: DataSource
    @MockitoBean private lateinit var memberService: WorkplaceMemberService

    @Test
    fun useReadOnlyRepeatableReadAndRestoreDefaultIsolation() {
        val target = mock<WorkplaceMember>()
        val user = mock<User>()
        whenever(target.id).thenReturn(Long.MAX_VALUE)
        whenever(target.user).thenReturn(user)
        whenever(user.name).thenReturn("직원")
        whenever(target.role).thenReturn(WorkplaceRole.EMPLOYEE)
        whenever(target.joinedAt).thenReturn(LocalDateTime.of(2026, 10, 1, 9, 0))
        doAnswer {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue()
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue()
            val connection: Connection = DataSourceUtils.getConnection(dataSource)
            try {
                assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ)
                assertThat(connection.isReadOnly).isTrue()
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource)
            }
            target
        }.whenever(memberService).requireLeaveTarget(1, Long.MAX_VALUE, Long.MAX_VALUE)

        val preview = service.getPreview(1, Long.MAX_VALUE, Long.MAX_VALUE)

        assertThat(preview.canLeave).isTrue()
        assertThat(preview.ongoingShift).isNull()
        assertThat(preview.previewContext).startsWith("lv1.")
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
        dataSource.connection.use { connection ->
            assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
            assertThat(connection.isReadOnly).isFalse()
        }
    }
}
