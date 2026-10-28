package com.merge.backend.domain.workplace.entity

import com.merge.backend.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity

@Entity
class Workplace(
    name: String,
    inviteCode: String
) : BaseEntity() {

    @field:Column(nullable = false)
    var name: String = name
        protected set

    @field:Column(nullable = false, unique = true)
    var inviteCode: String = inviteCode
        protected set

    /**
     * 현재 관리자 확인, Workplace Lock, 다른 근무지 코드와의 중복 검사를 마친
     * 재발급 Command에서 호출한다. 가입 경로도 같은 Lock 규약을 적용해야 한다.
     */
    fun regenerateInviteCode(newInviteCode: String) {
        require(newInviteCode.matches(Regex("[0-9A-F]{8}"))) {
            "초대 코드는 8자리 대문자 16진수여야 합니다."
        }
        require(newInviteCode != inviteCode) { "직전 초대 코드와 다른 코드가 필요합니다." }

        inviteCode = newInviteCode
    }
}
