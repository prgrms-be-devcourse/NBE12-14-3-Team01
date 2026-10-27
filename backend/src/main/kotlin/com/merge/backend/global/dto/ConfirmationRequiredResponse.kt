package com.merge.backend.global.dto

@JvmRecord
data class ConfirmationRequiredResponse(
    val requiresConfirmation: Boolean,
)
