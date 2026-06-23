package io.rikka.agent.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit 4 Rule that replaces the Main dispatcher with a [TestDispatcher].
 *
 * This is essential for testing ViewModels and other code that uses
 * `Dispatchers.Main`. Without this rule, tests would fail with
 * "Module with the Main dispatcher had failed to initialize".
 *
 * ## Usage
 *
 * ```kotlin
 * class MyViewModelTest {
 *     @get:Rule
 *     val mainDispatcherRule = TestDispatcherRule()
 *
 *     @Test
 *     fun myTest() = runTest {
 *         // Dispatchers.Main is now a TestDispatcher
 *         val viewModel = MyViewModel()
 *         // ...
 *     }
 * }
 * ```
 *
 * ## Custom dispatcher
 *
 * By default, uses [UnconfinedTestDispatcher] for immediate execution.
 * Pass a [StandardTestDispatcher] if you need explicit control over
 * when coroutines run:
 *
 * ```kotlin
 * @get:Rule
 * val mainDispatcherRule = TestDispatcherRule(StandardTestDispatcher())
 * ```
 *
 * @param testDispatcher The [TestDispatcher] to use as Main dispatcher.
 *   Defaults to [UnconfinedTestDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
