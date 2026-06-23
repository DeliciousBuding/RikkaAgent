package io.rikka.agent.testutil

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.testIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test utilities for working with Kotlin Flows.
 *
 * Provides assertion extensions and helper functions for testing
 * [Flow], [StateFlow], and related coroutine-based reactive streams.
 *
 * ## Dependencies
 *
 * Requires the Turbine library: `app.cash.turbine:turbine`
 */

// ── Flow Assertion Extensions ─────────────────────────────────────────────────

/**
 * Assert that a [Flow] emits exactly the expected values in order, then completes.
 *
 * ```kotlin
 * flowOf(1, 2, 3).assertEmitsExactly(1, 2, 3)
 * ```
 *
 * @param expected The values the flow should emit, in order.
 */
suspend fun <T> Flow<T>.assertEmitsExactly(vararg expected: T) {
    test {
        expected.forEach { value ->
            assertEquals(value, awaitItem(), "Unexpected emission value")
        }
        awaitComplete()
    }
}

/**
 * Assert that a [Flow] emits exactly the expected values as a list, then completes.
 *
 * ```kotlin
 * flowOf(1, 2, 3).assertEmitsExactly(listOf(1, 2, 3))
 * ```
 *
 * @param expected The list of values the flow should emit, in order.
 */
suspend fun <T> Flow<T>.assertEmitsExactly(expected: List<T>) {
    test {
        expected.forEach { value ->
            assertEquals(value, awaitItem(), "Unexpected emission value")
        }
        awaitComplete()
    }
}

/**
 * Assert that a [Flow] emits at least the expected values in order.
 *
 * The flow may emit additional values after the expected ones.
 *
 * ```kotlin
 * flowOf(1, 2, 3, 4).assertStartsWith(1, 2, 3)
 * ```
 *
 * @param expected The values the flow should start with.
 */
suspend fun <T> Flow<T>.assertStartsWith(vararg expected: T) {
    test {
        expected.forEach { value ->
            assertEquals(value, awaitItem(), "Unexpected emission value")
        }
    }
}

/**
 * Assert that a [StateFlow] has the expected initial value.
 *
 * ```kotlin
 * val flow = MutableStateFlow(42)
 * flow.assertCurrentValue(42)
 * ```
 *
 * @param expected The expected current value.
 */
fun <T> StateFlow<T>.assertCurrentValue(expected: T) {
    assertEquals(expected, value, "StateFlow current value mismatch")
}

/**
 * Collect a [Flow] into a test [ReceiveTurbine] within the given [scope].
 *
 * ```kotlin
 * val turbine = flow.collectIn(scope)
 * assertEquals(1, turbine.awaitItem())
 * turbine.cancel()
 * ```
 *
 * @param scope The [CoroutineScope] to collect in.
 * @return A [ReceiveTurbine] for asserting emissions.
 */
fun <T> Flow<T>.collectIn(scope: CoroutineScope): ReceiveTurbine<T> = testIn(scope)

// ── Turbine Assertion Helpers ─────────────────────────────────────────────────

/**
 * Assert that the next emission matches the expected value.
 *
 * ```kotlin
 * flow.test {
 *     assertNext(1)
 *     assertNext(2)
 *     awaitComplete()
 * }
 * ```
 *
 * @param expected The expected value.
 */
suspend fun <T> ReceiveTurbine<T>.assertNext(expected: T): T {
    val item = awaitItem()
    assertEquals(expected, item, "Unexpected emission value")
    return item
}

/**
 * Assert that the next emission matches a predicate.
 *
 * ```kotlin
 * flow.test {
 *     assertNextThat { it > 0 }
 * }
 * ```
 *
 * @param predicate The predicate to match against.
 * @return The matched item.
 */
suspend fun <T> ReceiveTurbine<T>.assertNextThat(predicate: (T) -> Boolean): T {
    val item = awaitItem()
    assertTrue(predicate(item), "Item $item did not match predicate")
    return item
}

/**
 * Assert that the next emission is null.
 *
 * ```kotlin
 * flow.test {
 *     assertNull()
 * }
 * ```
 */
suspend fun ReceiveTurbine<Any?>.assertNull() {
    val item = awaitItem()
    assertNull(item, "Expected null but got $item")
}

/**
 * Assert that the next emission is not null.
 *
 * ```kotlin
 * flow.test {
 *     assertNotNull()
 * }
 * ```
 *
 * @return The non-null item.
 */
suspend fun <T> ReceiveTurbine<T?>.assertNotNull(): T {
    val item = awaitItem()
    assertNotNull(item, "Expected non-null but got null")
    @Suppress("UNCHECKED_CAST")
    return item as T
}

/**
 * Skip [count] emissions without asserting them.
 *
 * Useful for skipping setup emissions before the actual test assertions.
 *
 * ```kotlin
 * flow.test {
 *     skipItems(2) // Skip initial emissions
 *     assertNext(expectedValue)
 * }
 * ```
 *
 * @param count The number of items to skip.
 */
suspend fun <T> ReceiveTurbine<T>.skipItems(count: Int) {
    repeat(count) { awaitItem() }
}

// ── Flow Collection Helpers ───────────────────────────────────────────────────

/**
 * Collect all emissions from a [Flow] into a list.
 *
 * **Warning**: This will suspend indefinitely for infinite flows.
 * Use only for flows that complete.
 *
 * ```kotlin
 * val items = flowOf(1, 2, 3).toList()
 * assertEquals(listOf(1, 2, 3), items)
 * ```
 *
 * @return A list of all emitted values.
 */
suspend fun <T> Flow<T>.toList(): List<T> {
    val items = mutableListOf<T>()
    collect { items.add(it) }
    return items
}

/**
 * Collect the first [n] emissions from a [Flow].
 *
 * ```kotlin
 * val items = flow.takeFirst(3)
 * ```
 *
 * @param n The number of items to collect.
 * @return A list of the first [n] emitted values.
 */
suspend fun <T> Flow<T>.takeFirst(n: Int): List<T> {
    val items = mutableListOf<T>()
    var count = 0
    collect { item ->
        items.add(item)
        count++
        if (count >= n) return items
    }
    return items
}
