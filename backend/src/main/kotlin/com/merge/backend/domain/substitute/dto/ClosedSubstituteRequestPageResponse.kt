package com.merge.backend.domain.substitute.dto

// API-SB-02 페이지 응답. 전체 개수 없이 다음 페이지 여부만 준다
@JvmRecord
data class ClosedSubstituteRequestPageResponse(
    val items: List<ClosedSubstituteRequestResponse>,
    val page: Int,
    val size: Int,
    val hasNext: Boolean,
)
