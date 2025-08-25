package com.crabtrack.app.domain.usecase

import com.crabtrack.app.data.model.Threshold
import com.crabtrack.app.data.repository.ThresholdRepository
import javax.inject.Inject

class UpdateThresholdsUseCase @Inject constructor(
    private val thresholdRepository: ThresholdRepository
) {
    
    suspend operator fun invoke(threshold: Threshold) {
        thresholdRepository.updateThreshold(threshold)
    }
    
    suspend operator fun invoke(thresholds: List<Threshold>) {
        thresholdRepository.updateThresholds(thresholds)
    }
}