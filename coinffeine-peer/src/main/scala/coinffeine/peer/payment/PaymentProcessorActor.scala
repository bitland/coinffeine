package coinffeine.peer.payment

import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.util.Timeout

import coinffeine.model.currency.{CurrencyAmount, FiatAmount, FiatCurrency}
import coinffeine.model.payment.Payment
import coinffeine.model.payment.PaymentProcessor._

object PaymentProcessorActor {

  /** Identifier of some funds blocked for a specific use. */
  case class FundsId(underlying: Int) extends AnyVal

  /** Initialize the payment processor actor */
  case class Initialize(eventChannel: ActorRef)

  /** A message sent to the payment processor in order to identify itself. */
  case object Identify

  /** A message sent by the payment processor identifying itself. */
  case class Identified(id: Id)

  /** A message sent to the payment processor to reserve some funds
    *
    * @constructor
    * @param amount    Amount to block
    * @param listener  Actor to notify if the funds are not available despite being blocked
    */
  case class BlockFunds(amount: FiatAmount, listener: ActorRef)

  /** Responses to [[BlockFunds]] */
  sealed trait BlockFundsResult
  case object NotEnoughFunds extends BlockFundsResult
  case class FundsBlocked(funds: FundsId) extends BlockFundsResult

  /** A message sent to the payment processor to release some previously reserved funds. */
  case class UnblockFunds(funds: FundsId)

  /** A message sent by the payment processor to notify that some blocked funds are not available
    * for external reasons.
    */
  case class UnavailableFunds(funds: FundsId)

  /** A message sent by the payment processor to notify when some blocked but not available funds
    * are available back */
  case class AvailableFunds(funds: FundsId)

  /** A message sent to the payment processor ordering a new pay.
    *
    * Funds to transfer should have been previously blocked using [[BlockFunds]] and can be
    * spent partially.
    *
    * @param fundsId   Id of the blocked funds to spent
    * @param to        The ID of the receiver account
    * @param amount    The amount of fiat currency to pay
    * @param comment   The comment to be attached to the payment
    * @tparam C        The fiat currency of the payment amount
    */
  case class Pay[C <: FiatCurrency](fundsId: FundsId,
                                    to: AccountId,
                                    amount: CurrencyAmount[C],
                                    comment: String)

  /** A message sent by the payment processor in order to notify of a successful payment. */
  case class Paid[C <: FiatCurrency](payment: Payment[C])

  /** A message sent by the payment processor to notify a payment failure.
    *
    * @param request The original pay message that cannot be processed.
    * @param error The error that prevented the request to be processed
    * @tparam C The fiat currency of the payment amount
    */
  case class PaymentFailed[C <: FiatCurrency](request: Pay[C], error: Throwable)

  /** A message sent to the payment processor in order to find a payment. */
  case class FindPayment(payment: PaymentId)

  /** A message sent by the payment processor to notify a found payment. */
  case class PaymentFound(payment: Payment[FiatCurrency])

  /** A message sent by the payment processor to notify a not found payment. */
  case class PaymentNotFound(payment: PaymentId)

  /** A message sent by the payment processor to notify an error while finding a payment. */
  case class FindPaymentFailed(payment: PaymentId, error: Throwable)

  /** A message sent to the payment processor to retrieve the current balance
    * in the given currency.
    * */
  case class RetrieveBalance[C <: FiatCurrency](currency: C)

  sealed trait RetrieveBalanceResponse

  /** A message sent by the payment processor reporting the current balance in the
    * given currency.
    * */
  case class BalanceRetrieved[C <: FiatCurrency](balance: CurrencyAmount[C])
    extends RetrieveBalanceResponse

  /** A message sent by the payment processor reporting that the current balance in the
    * given currency cannot be retrieved.
    */
  case class BalanceRetrievalFailed[C <: FiatCurrency](currency: C, error: Throwable)
    extends RetrieveBalanceResponse

  /** Payment processor requests should be considered to have failed after this period */
  val RequestTimeout = Timeout(5.seconds)
}
