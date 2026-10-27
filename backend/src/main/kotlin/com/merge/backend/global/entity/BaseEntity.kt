package com.merge.backend.global.entity

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.time.LocalDateTime
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {

    // 영속 전에는 null (IDENTITY 전략이라 저장 시점에 채워짐)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    // DB NOT NULL이고 Auditing이 저장 시점에 채움
    @CreatedDate
    @Column(nullable = false, updatable = false)
    lateinit var createDate: LocalDateTime
        protected set

    @LastModifiedDate
    @Column(nullable = false)
    lateinit var modifyDate: LocalDateTime
        protected set
}
