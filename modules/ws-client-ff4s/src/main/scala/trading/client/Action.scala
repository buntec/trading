package trading.client

import trading.Newtype
import trading.domain.*
import trading.ws.WsOut

enum Action:
  case CloseAlerts
  case SymbolChanged(input: InputText)
  case Subscribe
  case Unsubscribe(symbol: Symbol)
  case Recv(in: WsOut)
  case ConnectWs
  case SetWsStatus(status: WsStatus)

enum WsStatus:
  case Connecting
  case Connected
  case Failed(msg: String)
