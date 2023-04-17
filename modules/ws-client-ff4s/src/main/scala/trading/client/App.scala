package trading.client

import cats.effect.*
import trading.domain.*
import cats.syntax.show.*

class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action]:

  override def store: Resource[F, ff4s.Store[F, State, Action]] = ???

  import dsl.*
  import dsl.html.*

  override def root: dsl.V = useState { model =>
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
          // onInput (s => Msg.SymbolChanged(InputText(s))),
          // onKeyDown(subscribeOnEnter),
          value := model.input.value
        ),
        div(
          `class` := "input-group-append",
          button(
            `class` := "btn btn-outline-primary btn-rounded",
            // onClick(Msg.Subscribe)
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
        hidden  := model.alerts.isEmpty,
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
          model.alerts.toList.flatMap((sl, alt) => alertRow(sl, alt))
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
        `class` := "close",
        // attribute("aria-label", "Close"),
        // onClick(Msg.CloseAlerts)
        "x"
      ),
      message ++ property.getOrElse("X")
    )

  val genericErrorAlert = useState { model =>
    mkAlert(model.error, "generic-error", "danger", "Error: ")
  }

  val subscriptionSuccess = useState { model =>
    mkAlert(model.sub.map(_.show), "subscription-success", "success", "Subscribed to ")
  }

  val unsubscriptionSuccess = useState { model =>
    mkAlert(model.unsub.map(_.show), "unsubscription-success", "warning", "Unsubscribed from ")
  }

  val tradeStatus = useState { model =>
    model.tradingStatus match
      case TradingStatus.On =>
        span(idAttr := "trade-status", `class` := "badge badge-pill badge-success", "Trading On")

      case TradingStatus.Off =>
        span(idAttr := "trade-status", `class` := "badge badge-pill badge-danger", "Trading Off")
  }

  val connectionDetails = useState { model =>
    (model.socket.id, model.onlineUsers) match
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
            // onClick(WsMsg.Connecting.asMsg),
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
                // onClick(Msg.Unsubscribe(symbol)),
                title := "Unsubscribe",
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
