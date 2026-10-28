package com.merge.backend.domain.substitute.repository

// Lock 전에 Entity를 읽지 않고 확인하는 요청의 근무·근무지 ID (ML-5 §4-3)
data class SubstituteRequestScope(
    val shiftId: Long,
    val workplaceId: Long,
)
