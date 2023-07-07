package it.unibo.loci.scafi

import it.unibo.loci.scafi.LociIncarnation.CNAME
import it.unibo.loci.scafi.LociIncarnation.EXPORT
import it.unibo.loci.scafi.LociIncarnation.ID
import it.unibo.loci.scafi.LociIncarnation.factory
import loci.language._
import loci.language.transmitter.rescala._
import loci.communicator.tcp._
import rescala.default._
import loci.serializer.circe._

import java.util.UUID
import io.circe.syntax.EncoderOps

@multitier class P2PSystem extends LogicalSystem {
  @peer type Node <: { type Tie <: Multiple[Node] }
  @peer type BehaviourComponent <: Node
  @peer type ActuatorComponent <: Node
  @peer type SensorComponent <: Node
  @peer type StateComponent <: Node
  @peer type CommunicationComponent <: Node

  private var _state: EXPORT on Node = factory.emptyExport()
  private val mid: ID on Node = UUID.randomUUID().hashCode()
  private val localExports: Var[(ID, Map[ID, EXPORT])] on Node = on[Node](Var((mid, Map.empty[ID, EXPORT])))
  def process(id: ID, export: EXPORT): Unit on Node = placed { // add the id and export of the nbrs to my localExports
    localExports.transform { case (myId, exports) => (myId, exports + (id -> export)) }
  }

  // quando uno si toglie dire a tutti che ti sei tolto
  // fare che i messaggi scadono dopo un po'.
  // quando arriva export salvo id e momento in cui mandato, ogni tot controllo il tempo corrente
  // delta ragionevole (in base ad applicazione se molto dinamico meglio avere tempo più breve) 3 volte al tempo di valutazione

  override def state(id: ID): State on StateComponent = _state

  override def update(id: ID, state: State): Unit on StateComponent = this._state = state.asJson.as[EXPORT] match {
    case Right(value) => value
    case Left(value) => throw new Exception(value)
  }
  override def exports(id: Int): Set[(ID, EXPORT)] on Node = localExports.now._2.toSet

  def myState: State on Node = this.state(mid)
  def mySensors: Set[(CNAME, SensorData)] on Node = this.sense(mid)

  def computeLocal(id: ID, state: State, exports: Set[(ID, EXPORT)], sensors: Set[(CNAME, SensorData)]): (
      EXPORT,
      State
  ) on Node =
    super.compute(id, state, exports, sensors)

  def main(): Unit on Node = {
//    remote[Node].left observe println
//    remote[Node].joined.observe { x: Remote[Node] => println(x) }
    while (true) {
      val state = myState
      val myExports = exports(mid)
      val sensors = mySensors
      val result = computeLocal(mid, state, myExports, sensors)
      remote.call(
        process(mid, result._1)
      ) // on every round does a remote call to every connected node, passing my id and export
      actuation(mid, result._1)
      update(mid, result._1)
      Thread.sleep(1000)
    }
  }
}

@multitier object SimpleExampleP2P extends P2PSystem()

object SystemP2PTest extends App {
  val port = 3245
  multitier.start(
    new Instance[SimpleExampleP2P.Node](
      connect[SimpleExampleP2P.Node](TCP(port).firstConnection)
    )
  )
  multitier.start(
    new Instance[SimpleExampleP2P.Node](
      connect[SimpleExampleP2P.Node](TCP("localhost", port))
    )
  )
}

object A extends App {
  multitier start new Instance[SimpleExampleP2P.Node](listen[SimpleExampleP2P.Node] {
    TCP(43053)
  })
}

object B extends App {
  multitier start new Instance[SimpleExampleP2P.Node](
    listen[SimpleExampleP2P.Node] {
      TCP(43054)
    } and
      connect[SimpleExampleP2P.Node] {
        TCP("localhost", 43053)
      }
  )
}

object C extends App {
  multitier start new Instance[SimpleExampleP2P.Node](connect[SimpleExampleP2P.Node] {
    TCP("localhost", 43053)
  } and connect[SimpleExampleP2P.Node] {
    TCP("localhost", 43054)
  })
}
