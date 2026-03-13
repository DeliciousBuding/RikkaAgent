package io.rikka.agent.ui.screen

import io.rikka.agent.vm.HostKeyEvent

data class HostKeyDialogState(
  val event: HostKeyEvent? = null,
  val confirmReplacement: HostKeyEvent.Mismatch? = null,
)

data class HostKeyDialogResult(
  val nextState: HostKeyDialogState,
  val decision: Boolean? = null,
)

object HostKeyDialogStateMachine {

  fun receive(event: HostKeyEvent): HostKeyDialogState =
    HostKeyDialogState(event = event)

  fun acceptPrimary(state: HostKeyDialogState): HostKeyDialogResult {
    val event = state.event ?: return HostKeyDialogResult(state)
    return if (event is HostKeyEvent.Mismatch) {
      HostKeyDialogResult(
        nextState = state.copy(confirmReplacement = event),
      )
    } else {
      HostKeyDialogResult(
        nextState = HostKeyDialogState(),
        decision = true,
      )
    }
  }

  fun rejectPrimary(): HostKeyDialogResult =
    HostKeyDialogResult(
      nextState = HostKeyDialogState(),
      decision = false,
    )

  fun confirmReplacement(accepted: Boolean): HostKeyDialogResult =
    HostKeyDialogResult(
      nextState = HostKeyDialogState(),
      decision = accepted,
    )
}
