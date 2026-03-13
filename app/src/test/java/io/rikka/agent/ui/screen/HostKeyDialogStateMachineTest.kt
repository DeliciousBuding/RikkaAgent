package io.rikka.agent.ui.screen

import io.rikka.agent.vm.HostKeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostKeyDialogStateMachineTest {

  @Test
  fun `unknown host accept resolves to trusted decision`() {
    val event = HostKeyEvent.UnknownHost("example.test", 22, "SHA256:abc", "ED25519")

    val result = HostKeyDialogStateMachine.acceptPrimary(
      HostKeyDialogStateMachine.receive(event)
    )

    assertEquals(HostKeyDialogState(), result.nextState)
    assertEquals(true, result.decision)
  }

  @Test
  fun `mismatch accept opens replacement confirmation before deciding`() {
    val event = HostKeyEvent.Mismatch("example.test", 22, "SHA256:old", "SHA256:new", "ED25519")

    val result = HostKeyDialogStateMachine.acceptPrimary(
      HostKeyDialogStateMachine.receive(event)
    )

    assertEquals(event, result.nextState.event)
    assertEquals(event, result.nextState.confirmReplacement)
    assertNull(result.decision)
  }

  @Test
  fun `reject primary immediately returns false decision`() {
    val result = HostKeyDialogStateMachine.rejectPrimary()

    assertEquals(HostKeyDialogState(), result.nextState)
    assertEquals(false, result.decision)
  }

  @Test
  fun `replacement confirmation clears state and returns explicit decision`() {
    val result = HostKeyDialogStateMachine.confirmReplacement(accepted = true)

    assertEquals(HostKeyDialogState(), result.nextState)
    assertEquals(true, result.decision)
  }
}
