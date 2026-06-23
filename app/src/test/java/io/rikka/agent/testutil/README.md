# Test Utilities

This package contains shared test utilities for RikkaAgent tests.

## Files

### TestDispatcherRule

A JUnit 4 Rule that replaces `Dispatchers.Main` with a `TestDispatcher`.

**Usage:**
```kotlin
class MyViewModelTest {
    @get:Rule
    val mainDispatcherRule = TestDispatcherRule()

    @Test
    fun myTest() = runTest {
        // Dispatchers.Main is now a TestDispatcher
        val viewModel = MyViewModel()
        // ...
    }
}
```

**Custom dispatcher:**
```kotlin
@get:Rule
val mainDispatcherRule = TestDispatcherRule(StandardTestDispatcher())
```

### FlowTestUtils

Extensions and utilities for testing Kotlin Flows.

**Key features:**
- `assertEmitsExactly()` - Assert flow emits exact values
- `assertStartsWith()` - Assert flow starts with values
- `assertCurrentValue()` - Assert StateFlow current value
- `assertNext()` / `assertNextThat()` - Turbine assertion helpers
- `skipItems()` - Skip emissions in tests

**Example:**
```kotlin
@Test
fun testFlow() = runTest {
    val flow = flowOf(1, 2, 3)

    flow.assertEmitsExactly(1, 2, 3)

    flow.test {
        assertNext(1)
        assertNext(2)
        assertNext(3)
        awaitComplete()
    }
}
```

### TestDataFactory

Factory methods for creating test data with sensible defaults.

**Key methods:**
- `createProfile()` - Create SSH profile
- `createPasswordProfile()` - Create password-authenticated profile
- `createCodexProfile()` - Create Codex-enabled profile
- `createTextMessage()` / `createUserMessage()` / `createAssistantMessage()`
- `createCommandMessage()` - Create command with output
- `createThread()` / `createConversation()`
- `createMessages()` / `createProfiles()` - Batch creation

**Example:**
```kotlin
@Test
fun testWithFactoryData() {
    val profile = TestDataFactory.createProfile(
        name = "Production Server",
        host = "10.0.1.50"
    )

    val thread = TestDataFactory.createConversation(
        title = "Disk check",
        userMessages = listOf("df -h", "du -sh *"),
        assistantMessages = listOf("Filesystem...", "45G ./data")
    )
}
```

**Deterministic IDs:**
Call `TestDataFactory.resetCounters()` in `@Before` for reproducible tests.

### ComposeTestExtensions

Extension functions for Jetpack Compose UI testing.

**Node assertions:**
- `assertTextIsDisplayed()` / `assertTextIsNotDisplayed()`
- `assertTagIsDisplayed()` / `assertTagIsNotDisplayed()`
- `assertContentDescriptionIsDisplayed()`
- `assertContainsText()` / `assertHasText()`
- `assertIsClickable()` / `assertIsNotClickable()`
- `assertIsEnabled()` / `assertIsDisabled()`
- `assertIsSelected()` / `assertIsNotSelected()`

**Node interactions:**
- `clickOnText()` / `clickOnTag()` / `clickOnContentDescription()`
- `typeText()` / `typeTextByLabel()`

**Wait helpers:**
- `waitForText()` / `waitForTag()`
- `waitForTextToDisappear()` / `waitForTagToDisappear()`

**Collection assertions:**
- `assertCount()` / `assertCountAtLeast()`
- `first()` / `last()`

**Example:**
```kotlin
class MyScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testScreen() {
        composeTestRule.setContent { MyScreen() }

        composeTestRule.assertTextIsDisplayed("Welcome")
        composeTestRule.clickOnText("Submit")
        composeTestRule.waitForText("Success!")
    }
}
```

## Dependencies

The utilities require these test dependencies (already in `build.gradle.kts`):

```kotlin
testImplementation(libs.junit4)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)  // For FlowTestUtils
testImplementation(platform(libs.compose.bom))
testImplementation("androidx.compose.ui:ui-test-junit4")
```

## Best Practices

1. **Use TestDispatcherRule** for all ViewModel and coroutine-based tests
2. **Reset TestDataFactory counters** in `@Before` for deterministic tests
3. **Prefer FlowTestUtils** over manual Turbine usage for cleaner assertions
4. **Use ComposeTestExtensions** to reduce boilerplate in UI tests
