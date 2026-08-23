package com.comst19.dambom.core.domain.usecase

import app.cash.turbine.test
import com.comst19.dambom.core.testfixture.FakeSampleRepository
import com.comst19.dambom.core.testfixture.sampleFixture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveSamplesUseCaseTest {
    @Test
    fun `blank titles are removed and samples are sorted`() =
        runTest {
            val repository =
                FakeSampleRepository(
                    listOf(
                        sampleFixture(id = 2),
                        sampleFixture(id = 3, title = ""),
                        sampleFixture(id = 1),
                    ),
                )

            ObserveSamplesUseCase(repository)().test {
                assertEquals(listOf(1L, 2L), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }
}
