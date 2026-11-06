package com.susil.sonora

import com.susil.sonora.data.settings.resolveEffectivePerformancePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptivePerformancePolicyTest {

    @Test
    fun `battery saver temporarily reduces visual work and suspends high refresh`() {
        val policy = resolveEffectivePerformancePolicy(
            userReduceAnimation = false,
            userReduceDynamicBlur = false,
            userAnimatedCanvas = true,
            userHighPerformanceMode = true,
            systemPowerSaver = true,
        )

        assertEquals(true, policy.reduceAnimation)
        assertEquals(true, policy.reduceDynamicBlur)
        assertEquals(false, policy.animatedCanvas)
        assertEquals(false, policy.highPerformanceMode)
    }

    @Test
    fun `leaving battery saver restores user selected preferences`() {
        val policy = resolveEffectivePerformancePolicy(
            userReduceAnimation = false,
            userReduceDynamicBlur = true,
            userAnimatedCanvas = true,
            userHighPerformanceMode = true,
            systemPowerSaver = false,
        )

        assertEquals(false, policy.reduceAnimation)
        assertEquals(true, policy.reduceDynamicBlur)
        assertEquals(true, policy.animatedCanvas)
        assertEquals(true, policy.highPerformanceMode)
    }

    @Test
    fun `manual reduced motion remains active when system battery saver is off`() {
        val policy = resolveEffectivePerformancePolicy(
            userReduceAnimation = true,
            userReduceDynamicBlur = false,
            userAnimatedCanvas = false,
            userHighPerformanceMode = false,
            systemPowerSaver = false,
        )

        assertEquals(true, policy.reduceAnimation)
        assertEquals(false, policy.reduceDynamicBlur)
        assertEquals(false, policy.animatedCanvas)
        assertEquals(false, policy.highPerformanceMode)
    }
}
