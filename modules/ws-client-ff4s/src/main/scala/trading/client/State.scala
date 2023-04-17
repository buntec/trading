package trading.client

import trading.Newtype
import trading.domain.*
import trading.ws.WsOut

import cats.Monoid
import cats.effect.IO
import cats.syntax.all.*
import monocle.{ Focus, Lens }

type WsUrl = WsUrl.Type
object WsUrl extends Newtype[String]

type InputText = InputText.Type
object InputText extends Newtype[String]:
  given Monoid[InputText] = derive

final case class TradingSocket(
    wsUrl: WsUrl,
    id: Option[SocketId],
    error: Option[String]
)

final case class State(
    symbol: Symbol,
    input: InputText,
    socket: TradingSocket,
    onlineUsers: Int,
    alerts: Map[Symbol, Alert],
    tradingStatus: TradingStatus,
    sub: Option[Symbol],
    unsub: Option[Symbol],
    error: Option[String]
)
