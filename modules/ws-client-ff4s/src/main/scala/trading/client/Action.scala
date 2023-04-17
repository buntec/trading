package trading.client

sealed trait Action

object Action:

  case class NoOp() extends Action
