package com.merge.backend.global.exception

class BusinessException @JvmOverloads constructor(
    val errorCode: ErrorCode,
    val data: Any? = null,
) : RuntimeException(errorCode.getMessage())
