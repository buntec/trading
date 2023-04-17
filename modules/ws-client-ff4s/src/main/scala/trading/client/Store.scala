package trading.client

import cats.effect.kernel.Async
import cats.effect.implicits.*
import cats.syntax.all.*
import monocle.syntax.all.*
import trading.domain.*
import cats.effect.std.Queue
import trading.ws.WsIn
import trading.ws.WsOut
import org.scalajs.dom
import fs2.Stream

object Store:

  def apply[F[_]](implicit F: Async[F]) =
    for
      wsSendQ <- Queue.unbounded[F, WsIn].toResource
      connQ   <- Queue.unbounded[F, Unit].toResource

      // ad-hoc implementation; should use fs2.dom
      refocusInput = F.delay {
        val elm = dom.document.getElementById("symbol-input").asInstanceOf[dom.HTMLElement]
        if elm != null then
          elm.focus()
      }.attempt.void

      store <- ff4s.Store[F, State, Action](State.init) { stateRef =>
        _ match
          case Action.CloseAlerts =>
            stateRef.update(_.copy(error = None, sub = None, unsub = None))
          case Action.SymbolChanged(in) => stateRef.update {
              _.copy(input = in, symbol = Symbol(in.value))
            }
          case Action.Subscribe => stateRef.modify { state =>
              (state.socket.id, state.symbol) match
                case (_, Symbol.XEMPTY) =>
                  state.copy(error = "Invalid symbol".some) -> F.unit
                case (Some(_), sl) =>
                  val nm = state.copy(sub = sl.some, symbol = mempty, input = mempty)
                  nm -> (wsSendQ.offer(WsIn.Subscribe(sl)) >> refocusInput)
                case (None, _) =>
                  state.copy(error = "Disconnected from server, please click on Connect.".some) -> F.unit
            }.flatten
          case Action.Unsubscribe(symbol) => stateRef.modify { state =>
              state.socket.id.fold((
                state.copy(error = "Disconnected from server, please click on Connect.".some) -> F.unit
              )) { _ =>
                val nm = state.copy(unsub = symbol.some, alerts = state.alerts - symbol)
                nm -> (wsSendQ.offer(WsIn.Unsubscribe(symbol)) >> refocusInput)
              }
            }.flatten

          case Action.Recv(WsOut.OnlineUsers(online)) => stateRef.update(_.copy(onlineUsers = online))

          case Action.Recv(WsOut.Notification(t: Alert.TradeAlert)) =>
            stateRef.update { state =>
              state.copy(alerts = state.alerts.updated(t.symbol, t))
            }

          case Action.Recv(WsOut.Notification(t: Alert.TradeUpdate)) =>
            stateRef.update(_.copy(tradingStatus = t.status))

          case Action.Recv(WsOut.Attached(sid)) => stateRef.update { state =>
              state.socket.id match
                case None    => state.focus(_.socket.id).replace(sid.some)
                case Some(_) => state
            }

          case Action.ConnectWs => connQ.offer(())

          case Action.SetWsStatus(status) => F.unit
      }

      // establish websockets connection
      _ <- Stream.fromQueueUnterminated(connQ).switchMap { _ =>
        Stream.exec(
          ff4s.WebSocketsClient[F].bidirectionalJson[WsOut, WsIn](
            "ws://localhost:9000/v1/ws",
            _.evalMap(msg => store.dispatch(Action.Recv(msg))),
            Stream.fromQueueUnterminated(wsSendQ)
          )
        )
      }.compile.drain.background
    yield store
