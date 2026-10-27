package com.merge.backend.domain.user.entity

import com.merge.backend.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User() : BaseEntity() {

    // DB NOT NULL이라 null 불가. Java Test가 빈 생성자(new User())를 쓰므로 lateinit
    @Column(nullable = false, unique = true)
    lateinit var email: String
        protected set

    @Column(nullable = false)
    lateinit var passwordHash: String
        protected set

    @Column(nullable = false)
    lateinit var name: String
        protected set

    @Column
    var refreshToken: String? = null
        protected set

    constructor(email: String, passwordHash: String, name: String) : this() {
        this.email = email
        this.passwordHash = passwordHash
        this.name = name
    }

    // passwordHash는 받지 않음 (Java 원본과 같음)
    constructor(id: Long?, email: String, name: String) : this() {
        setId(id)
        this.email = email
        this.name = name
    }

    fun updateRefreshToken(refreshToken: String?) {
        this.refreshToken = refreshToken
    }
}
