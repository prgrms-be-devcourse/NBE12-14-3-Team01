package com.merge.backend.global.config

import com.merge.backend.domain.user.entity.User
import com.merge.backend.domain.user.repository.UserRepository
import com.merge.backend.domain.workplace.entity.Workplace
import com.merge.backend.domain.workplace.entity.WorkplaceMember
import com.merge.backend.domain.workplace.entity.WorkplaceRole
import com.merge.backend.domain.workplace.repository.WorkplaceMemberRepository
import com.merge.backend.domain.workplace.repository.WorkplaceRepository
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Configuration
@Profile("init-data")
class InitData(
    private val initDataService: InitDataService,
) {

    @Bean
    fun initDummyData(): CommandLineRunner = CommandLineRunner { initDataService.init() }

    // 트랜잭션 적용을 위해 내부 서비스 클래스로 분리
    @Component
    @Profile("init-data")
    class InitDataService(
        private val userRepository: UserRepository,
        private val workplaceRepository: WorkplaceRepository,
        private val workplaceMemberRepository: WorkplaceMemberRepository,
        private val passwordEncoder: PasswordEncoder,
    ) {

        @Value("\${app.init.default-password}")
        private lateinit var rawDefaultPassword: String

        @Transactional
        fun init() {
            // 이미 데이터가 존재하는지 확인하여 중복 생성 방지
            if (userRepository.count() > 0) {
                println("이미 데이터가 존재하므로 초기화 작업을 스킵합니다.")
                return
            }

            val now = LocalDateTime.now()

            // 환경변수에서 읽어온 비밀번호를 암호화
            val defaultPassword = requireNotNull(passwordEncoder.encode(rawDefaultPassword))

            // User 생성 (매니저 1명, 직원 2명)
            val managerUser = userRepository.save(User("manager@test.com", defaultPassword, "김점장"))
            val worker1User = userRepository.save(User("worker1@test.com", defaultPassword, "이알바"))
            val worker2User = userRepository.save(User("worker2@test.com", defaultPassword, "박알바"))

            // Workplace 생성
            val workplace = workplaceRepository.save(Workplace("강남 1호점", "1234"))

            // WorkplaceMember 생성
            workplaceMemberRepository.save(
                WorkplaceMember(workplace, managerUser, WorkplaceRole.MANAGER, now),
            )
            workplaceMemberRepository.save(
                WorkplaceMember(workplace, worker1User, WorkplaceRole.EMPLOYEE, now),
            )
            workplaceMemberRepository.save(
                WorkplaceMember(workplace, worker2User, WorkplaceRole.EMPLOYEE, now),
            )

            println("=================================================")
            println(">>> [InitData] 최소 초기 더미 데이터 생성이 완료되었습니다.")
            println("=================================================")
        }
    }
}
