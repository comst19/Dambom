package com.comst19.dambom.core.common.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ViewModelJobLauncher<KEY : Any>(
    private val scope: CoroutineScope,
) {
    private val jobs = mutableMapOf<KEY, Job>()

    fun launchIfIdle(
        key: KEY,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        if (jobs[key]?.isActive == true) return

        val job = scope.launch(start = CoroutineStart.LAZY, block = block)
        jobs[key] = job
        job.invokeOnCompletion { jobs.remove(key, job) }
        job.start()
    }
}
