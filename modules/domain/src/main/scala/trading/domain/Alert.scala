package trading.domain

import derevo.cats.show
import derevo.circe.magnolia.{ decoder, encoder }
import derevo.derive

@derive(decoder, encoder, show)
sealed trait Alert

object Alert {
  final case class StrongBuy(symbol: Symbol, price: Price)  extends Alert
  final case class StrongSell(symbol: Symbol, price: Price) extends Alert
  final case class Neutral(symbol: Symbol, price: Price)    extends Alert
  final case class Buy(symbol: Symbol, price: Price)        extends Alert
  final case class Sell(symbol: Symbol, price: Price)       extends Alert
}
