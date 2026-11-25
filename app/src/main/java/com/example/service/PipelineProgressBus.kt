package com.example.service

import com.example.model.GenerationProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object PipelineProgressBus {
    private val _progress = MutableStateFlow(GenerationProgress())
    val progress: StateFlow<GenerationProgress> = _progress.asStateFlow()

    fun update(transform: (GenerationProgress) -> GenerationProgress) {
        _progress.update(transform)
    }

    fun reset() {
        _progress.value = GenerationProgress()
    }
}
