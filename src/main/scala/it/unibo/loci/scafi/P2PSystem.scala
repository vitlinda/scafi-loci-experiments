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

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.concurrent.ExecutionContext.Implicits.global

@multitier class P2PSystem extends LogicalSystem {
  @peer type Node <: { type Tie <: Multiple[Node] }
  @peer type BehaviourComponent <: Node
  @peer type ActuatorComponent <: Node
  @peer type SensorComponent <: Node
  @peer type StateComponent <: Node
  @peer type CommunicationComponent <: Node

  private var _state: EXPORT on Node = factory.emptyExport()
  private val mid: ID on Node = UUID.randomUUID().hashCode()
  private val localExports: Local[Var[(ID, Map[ID, EXPORT])]] on Node = Var((mid, Map.empty[ID, EXPORT]))
  private val remoteNodesIds: Local[Var[Map[Remote[Node], ID]]] on Node = Var(Map.empty[Remote[Node], ID])

  // add the id and export of the nbrs to my localExports
  def process(id: ID, export: EXPORT): Unit on Node =
    localExports.transform { case (myId, exports) => (myId, exports + (id -> export)) }

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

  def addRemoteNode(node: Remote[Node]): Local[Unit] on Node = {
    val id: Future[ID] = (mid from node).asLocal
    id.onComplete {
      case Success(value) => remoteNodesIds.transform(_ + (node -> value))
      case Failure(_) => println("Failed to get the id")
    }
  }

  def removeExport(node: Remote[Node]): Local[Unit] on Node = {
    val id = remoteNodesIds.now(node)
    remoteNodesIds.transform(_ - node)
    localExports.transform { case (myId, exports) =>
      (myId, exports - exports.find(_._1 == id).get._1)
    }
  }

  // another approach: an export expires after a while and is removed from the exports of a node
  // when an export arrives, save the id and the time. After a delta time checks all the dates and discard the expired exports
  // a reasonable delta could be 3 times the evaluation time
  // if the application is really dynamic the delta should be shorter
  // should be possible to pass the delta as a parameter

  def main(): Unit on Node = {
    remote[Node].joined observe addRemoteNode
    remote[Node].left observe removeExport

    remoteNodesIds.observe(a => println(s"the nodes: $a"))

    while (true) {
      val state = myState
      val myExports = exports(mid)
      val sensors = mySensors
      val result = computeLocal(mid, state, myExports, sensors)
      // at every round perform a remote call and send my id and export to all my neighbours (the connected nodes)
      remote.call(process(mid, result._1))
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
