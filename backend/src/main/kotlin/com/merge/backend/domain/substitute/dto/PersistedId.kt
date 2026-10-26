package com.merge.backend.domain.substitute.dto

import com.merge.backend.global.entity.BaseEntity

// 응답 DTO는 저장된 Entity로만 만들므로 id가 반드시 있다
internal val BaseEntity.persistedId: Long
    get() = requireNotNull(id) { "저장되지 않은 ${javaClass.simpleName}의 id에 접근했습니다." }
