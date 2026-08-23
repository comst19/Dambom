package com.comst19.dambom.core.testfixture

import com.comst19.dambom.core.domain.model.Sample
import com.comst19.dambom.core.domain.repository.SampleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

fun sampleFixture(
    id: Long = 1,
    title: String = "Sample $id",
    description: String = "Description $id",
): Sample = Sample(id, title, description)

class FakeSampleRepository(
    initial: List<Sample> = emptyList(),
) : SampleRepository {
    private val samples = MutableStateFlow(initial)
    var refreshFailure: Throwable? = null
    var refreshCallCount: Int = 0
        private set
    var onRefresh: suspend () -> Unit = {}

    override fun observeSamples(): Flow<List<Sample>> = samples

    override fun observeSample(id: Long): Flow<Sample?> =
        MutableStateFlow(
            samples.value.firstOrNull { it.id == id },
        )

    override suspend fun refreshSamples() {
        refreshCallCount++
        onRefresh()
        refreshFailure?.let { throw it }
    }

    fun setSamples(value: List<Sample>) {
        samples.value = value
    }
}
