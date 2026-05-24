package com.example.eyes.voiceguide

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

class ApplicationScope(
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main.immediate
) : CoroutineScope {
}
