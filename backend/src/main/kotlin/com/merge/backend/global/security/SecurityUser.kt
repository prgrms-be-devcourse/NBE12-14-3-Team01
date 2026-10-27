package com.merge.backend.global.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User

class SecurityUser(
    username: String,
    password: String,
    authorities: Collection<GrantedAuthority>,
    val id: Long,
    val name: String,
) : User(username, password, authorities)
