package trading.client

import trading.Newtype
import trading.domain.*
import trading.ws.WsOut

type ElemId = ElemId.Type
object ElemId extends Newtype[String]

enum WsMsg:
  case Error(msg: String)
  case Connecting
  case Heartbeat
  case Disconnected(code: Int, reason: String)

  def asAction: Action = Action.ConnStatus(this)

enum Action:
  case CloseAlerts
  case SymbolChanged(input: InputText)
  case Subscribe
  case Unsubscribe(symbol: Symbol)
  case Recv(in: WsOut)
  case ConnStatus(msg: WsMsg)
  case FocusError(id: ElemId)
  case NoOp
