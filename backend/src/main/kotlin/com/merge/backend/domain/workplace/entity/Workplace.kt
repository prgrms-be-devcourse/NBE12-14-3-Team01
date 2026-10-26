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
}
