package coinffeine.peer.bitcoin

import akka.actor.Props
import org.scalatest.mock.MockitoSugar
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.bitcoin.{ImmutableTransaction, KeyPair}
import coinffeine.model.currency.Implicits._

class BlockchainActorTest extends AkkaSpec("BlockChainActorTest")
    with BitcoinjTest with MockitoSugar {

  "The blockchain actor" must "report transaction confirmation" in new Fixture {
    instance ! BlockchainActor.WatchTransactionConfirmation(tx.getHash, 1)
    sendToBlockChain(tx)
    expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 1))
  }

  it must "report transaction confirmation only once" in new Fixture {
    instance ! BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2)
    sendToBlockChain(tx)
    mineBlock()
    expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
    mineBlock()
    expectNoMsg()
  }

  it must "not report transaction confirmation when still unconfirmed" in new Fixture {
    instance ! BlockchainActor.WatchTransactionConfirmation(tx.getHash, 3)
    sendToBlockChain(tx)
    expectNoMsg()
  }

  it must "report transaction rejection when it's lost from the blockchain" in new Fixture {
    instance ! BlockchainActor.WatchTransactionConfirmation(tx.getHash, 3)
    val forkBlock = chainHead()
    sendToBlockChain(tx)
    val forkPlusOneBlock = sendToBlockChain(forkBlock, otherKeyPair)
    sendToBlockChain(forkPlusOneBlock, otherKeyPair)
    expectMsg(BlockchainActor.TransactionRejected(tx.getHash))
  }

  it must "report transaction confirmation after blockchain fork including the tx" in new Fixture {
    instance ! BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2)
    val forkBlock = chainHead()
    sendToBlockChain(tx)
    val forkPlusOneBlock = sendToBlockChain(forkBlock, otherKeyPair, tx)
    sendToBlockChain(forkPlusOneBlock, otherKeyPair)
    expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
  }

  it must "report transaction confirmation after an attempt of blockchain fork" in new Fixture {
    instance ! BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2)
    val forkBlock = chainHead()
    val origPlusOneBlock = sendToBlockChain(tx)
    val forkPlusOneBlock = sendToBlockChain(forkBlock, otherKeyPair, tx)
    sendToBlockChain(origPlusOneBlock, otherKeyPair)
    expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
  }

  it must "report concurrent transaction confirmations" in new Fixture {
    instance ! BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2)
    instance ! BlockchainActor.WatchTransactionConfirmation(otherTx.getHash, 3)
    sendToBlockChain(tx, otherTx)
    mineBlock()
    expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
    mineBlock()
    expectMsg(BlockchainActor.TransactionConfirmed(otherTx.getHash, 3))
  }

  it must "retrieve existing transaction in blockchain" in new Fixture {
    instance ! BlockchainActor.WatchPublicKey(keyPair)
    sendToBlockChain(tx)
    instance ! BlockchainActor.RetrieveTransaction(tx.getHash)
    expectMsg(BlockchainActor.TransactionFound(tx.getHash, ImmutableTransaction(tx)))
  }

  it must "fail to retrieve nonexistent transaction in blockchain" in new Fixture {
    instance ! BlockchainActor.WatchPublicKey(keyPair)
    instance ! BlockchainActor.RetrieveTransaction(tx.getHash)
    expectMsg(BlockchainActor.TransactionNotFound(tx.getHash))
  }

  it must "report blockchain height after the blockchain reaches the notification threshold" in
    new Fixture {
      instance ! BlockchainActor.WatchBlockchainHeight(50)
      for (currentHeight <- chain.getBestChainHeight to 48) {
        mineBlock()
      }
      expectNoMsg()
      mineBlock()
      expectMsg(BlockchainActor.BlockchainHeightReached(50))
    }

  it must "retrieve the blockchain height" in new Fixture {
    instance ! BlockchainActor.RetrieveBlockchainHeight
    expectMsg(BlockchainActor.BlockchainHeightReached(chain.getBestChainHeight))
  }

  trait Fixture {
    val keyPair = new KeyPair()
    val otherKeyPair = new KeyPair()
    val wallet = createWallet(keyPair, 1.BTC)
    val transactionBroadcaster = new MockTransactionBroadcaster()
    val otherWallet = createWallet(keyPair, 1.BTC)
    val tx = wallet.createSend(keyPair.toAddress(network), 0.1.BTC.asSatoshi)
    val immutableTx = ImmutableTransaction(tx)
    val otherTx = otherWallet.createSend(keyPair.toAddress(network), 0.1.BTC.asSatoshi)

    val instance = system.actorOf(Props(new BlockchainActor(network)))
    instance ! BlockchainActor.Initialize(chain)
  }
}
