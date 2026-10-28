package com.merge.backend.global.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

// JPA용 인자 없는 생성자는 kotlin("plugin.jpa")가 만든다 (Java의 Lombok @NoArgsConstructor 대체)
@Entity
@Table(name = "auditing_test_entity")
class AuditingTestEntity(name: String) : BaseEntity() {

    @field:Column(nullable = false)
    var name: String = name
        protected set

    fun changeName(name: String) {
        this.name = name
    }
}
