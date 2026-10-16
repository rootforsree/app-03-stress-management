package com.sreecharan.stressrelief.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * Runs a ViewModel test with Dispatchers.Main pointed at the test scheduler, so
 * viewModelScope and an injected @IoDispatcher both advance with advanceUntilIdle().
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runViewModelTest(block: suspend TestScope.(dispatcher: CoroutineDispatcher) -> Unit) =
    runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            block(dispatcher)
        } finally {
            Dispatchers.resetMain()
        }
    }
