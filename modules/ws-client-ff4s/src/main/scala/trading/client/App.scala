package trading.client

import cats.effect.*
import trading.domain.*
import org.scalajs.dom
import cats.syntax.all.*

class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action]:

  override val rootElementId = "trading-ws"

  override val store = Store[F]

  import dsl.*
  import dsl.html.*

  override val root = useState { state =>
    div(
      `class` := "container",
      genericErrorAlert,
      subscriptionSuccess,
      unsubscriptionSuccess,
      h2(styleAttr := "align-content: center", "Trading WS"),
      div(
        `class` := "input-group mb-3",
        input(
          `type`      := "text",
          idAttr      := "symbol-input",
          autoFocus   := true,
          placeholder := "Symbol (e.g. EURUSD)",
          onInput := ((ev: dom.Event) =>
            ev.target match
              case el: dom.HTMLInputElement => Some(Action.SymbolChanged(InputText(el.value)))
              case _                        => None
          ),
          onKeyDown := ((ev: dom.KeyboardEvent) =>
            if ev.key == "Enter" then Action.Subscribe.some
            else none
          ),
          value := state.input.value
        ),
        div(
          `class` := "input-group-append",
          button(
            `class` := "btn btn-outline-primary btn-rounded",
            onClick := (_ => Action.Subscribe.some),
            "Subscribe"
          )
        )
      ),
      div(
        idAttr  := "sid-card",
        `class` := "card",
        div(
          `class` := "sid-body",
          tradeStatus,
          span(" "),
          connectionDetails
        )
      ),
      p(),
      table(
        `class` := "table table-inverse",
        hidden  := state.alerts.isEmpty,
        thead(
          tr(
            th("Symbol"),
            th("Bid"),
            th("Ask"),
            th("High"),
            th("Low"),
            th("Status"),
            th()
          )
        ),
        tbody(
          state.alerts.toList.flatMap((sl, alt) => alertRow(sl, alt))
        )
      )
    )

  }

  def mkAlert(property: Option[String], divId: String, status: String, message: String) =
    div(
      idAttr  := divId,
      `class` := s"alert alert-$status fade show",
      hidden  := property.isEmpty,
      button(
        `class`       := "close",
        aria("label") := "Close",
        onClick       := (_ => Action.CloseAlerts.some),
        "x"
      ),
      message ++ property.getOrElse("X")
    )

  val genericErrorAlert = useState { state =>
    mkAlert(state.error, "generic-error", "danger", "Error: ")
  }

  val subscriptionSuccess = useState { state =>
    mkAlert(state.sub.map(_.show), "subscription-success", "success", "Subscribed to ")
  }

  val unsubscriptionSuccess = useState { state =>
    mkAlert(state.unsub.map(_.show), "unsubscription-success", "warning", "Unsubscribed from ")
  }

  val tradeStatus = useState {
    _.tradingStatus match
      case TradingStatus.On =>
        span(idAttr := "trade-status", `class` := "badge badge-pill badge-success", "Trading On")

      case TradingStatus.Off =>
        span(idAttr := "trade-status", `class` := "badge badge-pill badge-danger", "Trading Off")
  }

  val connectionDetails = useState { state =>
    (state.socket.id, state.onlineUsers) match
      case (Some(sid), online) =>
        span(
          span(idAttr := "socket-id", `class` := "badge badge-pill badge-success", s"Socket ID: ${sid.show}"),
          span(" "),
          span(idAttr := "online-users", `class` := "badge badge-pill badge-success", s"Online: ${online.show}")
        )

      case (None, users) =>
        span(
          span(idAttr := "socket-id", `class` := "badge badge-pill badge-secondary", "<Disconnected>"),
          span(" "),
          button(
            `class` := "badge badge-pill badge-primary",
            onClick := (_ => Action.ConnectWs.some),
            "Connect"
          )
        )
  }

  def alertRow(symbol: Symbol, alert: Alert) =
    alert match
      case t: Alert.TradeAlert =>
        List(
          tr(
            th(symbol.show),
            th(t.bidPrice.show),
            th(t.askPrice.show),
            th(t.high.show),
            th(t.low.show),
            alertType(t.alertType),
            th(
              button(
                `class` := "badge badge-pill badge-danger",
                onClick := (_ => Action.Unsubscribe(symbol).some),
                title   := "Unsubscribe",
                img(
                  src        := "assets/icons/delete.png",
                  widthAttr  := 16,
                  heightAttr := 16
                )
              )
            )
          )
        )

      case _: Alert.TradeUpdate =>
        List.empty

  def alertType(t: AlertType) =
    t match
      case AlertType.StrongBuy =>
        alertTypeColumn("strong-buy", "Strong Buy")

      case AlertType.StrongSell =>
        alertTypeColumn("strong-sell", "Strong Sell")

      case AlertType.Neutral =>
        alertTypeColumn("neutral", "Neutral")

      case AlertType.Sell =>
        alertTypeColumn("sell", "Sell")

      case AlertType.Buy =>
        alertTypeColumn("buy", "Buy")

  def alertTypeColumn(imgName: String, value: String) =
    th(
      img(
        src        := s"assets/icons/$imgName.png",
        widthAttr  := 28,
        heightAttr := 28
      ),
      value
    )
