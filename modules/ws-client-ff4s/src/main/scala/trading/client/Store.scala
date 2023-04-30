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
import cats.effect.kernel.Resource.ExitCase
import cats.effect.kernel.Outcome

object Store:

  def apply[F[_]](implicit F: Async[F]) =
    for
      // queue for outbound WS messages
      wsSendQ <- Queue.unbounded[F, WsIn].toResource

      // offering to this queue (re)opens the WS connection
      wsConnQ <- Queue.unbounded[F, Unit].toResource

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
                  val ns = state.copy(sub = sl.some, symbol = mempty, input = mempty)
                  ns -> (wsSendQ.offer(WsIn.Subscribe(sl)) >> refocusInput)
                case (None, _) =>
                  state.copy(error = "Disconnected from server, please click on Connect.".some) -> F.unit
            }.flatten

          case Action.Unsubscribe(symbol) => stateRef.modify { state =>
              state.socket.id.fold((
                state.copy(error = "Disconnected from server, please click on Connect.".some) -> F.unit
              )) { _ =>
                val ns = state.copy(unsub = symbol.some, alerts = state.alerts - symbol)
                ns -> (wsSendQ.offer(WsIn.Unsubscribe(symbol)) >> refocusInput)
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

          case Action.ConnectWs => wsConnQ.offer(())

          case Action.SetWsStatus(status) =>
            stateRef.update(_.focus(_.socket.status).replace(status))
      }

      // establish websocket connection
      _ <- Stream.fromQueueUnterminated(wsConnQ).switchMap { _ =>
        Stream.exec(
          store.state.get.flatMap { state =>
            ff4s.WebSocketClient[F].bidirectionalJson[WsOut, WsIn](
              state.socket.wsUrl.value,
              _.evalMap(msg => store.dispatch(Action.Recv(msg))),
              Stream.fromQueueUnterminated(wsSendQ)
            )
          }.guaranteeCase {
            case Outcome.Errored(t)   => store.dispatch(Action.SetWsStatus(WsStatus.Failed(t.toString)))
            case Outcome.Succeeded(_) => store.dispatch(Action.SetWsStatus(WsStatus.Closed))
            case Outcome.Canceled()   => F.unit
          }.handleError(_ => ())
        )
      }.compile.drain.background
    yield store
