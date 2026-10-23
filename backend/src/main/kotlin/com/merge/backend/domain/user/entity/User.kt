package com.merge.backend.domain.user.entity

import com.merge.backend.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User() : BaseEntity() {

    @Column(nullable = false, unique = true)
    var email: String? = null
        protected set

    @Column(nullable = false)
    var passwordHash: String? = null
        protected set

    @Column(nullable = false)
    var name: String? = null
        protected set

    @Column
    var refreshToken: String? = null
        protected set

    constructor(email: String?, passwordHash: String?, name: String?) : this() {
        this.email = email
        this.passwordHash = passwordHash
        this.name = name
    }

    constructor(id: Long?, email: String?, name: String?) : this() {
        setId(id)
        this.email = email
        this.name = name
    }

    fun updateRefreshToken(refreshToken: String?) {
        this.refreshToken = refreshToken
    }
}
