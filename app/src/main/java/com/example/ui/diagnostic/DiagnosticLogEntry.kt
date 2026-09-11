package com.example.ui.diagnostic

import java.util.UUID

data class DiagnosticLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String,
    val type: String,
    val coordinates: String,
    val isSuccess: Boolean,
    val latencyMs: Long,
    val message: String
)
