package it.unibo.loci.scafi.experiments.dynamiconnections

import io.circe.syntax.EncoderOps
import it.unibo.loci.scafi.commons.LociIncarnation._
import loci.language.transmitter.rescala._
import loci.serializer.circe._

import loci.communicator.tcp._
import loci.language._
import rescala.default._

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

@multitier class P2PSystem extends LogicalSystem {
  @peer type Node <: { type Tie <: Multiple[Node] }
  @peer type BehaviourComponent <: Node
  @peer type ActuatorComponent <: Node
  @peer type SensorComponent <: Node
  @peer type StateComponent <: Node
  @peer type CommunicationComponent <: Node

  private val namespace = new StandardSpatialSensorNames {}

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
  def mySensors(sensor: (CNAME, SensorData)): Set[(CNAME, SensorData)] on Node = this.sense(mid, sensor)

  def computeLocal(
      id: ID,
      state: State,
      exports: Set[(ID, EXPORT)],
      sensors: Set[(CNAME, SensorData)],
      nbrSensors: Map[CNAME, Map[ID, Double]]
  ): (
      EXPORT,
      State
  ) on Node =
    super.compute(id, state, exports, sensors, nbrSensors)

  def addRemoteNode(node: Remote[Node]): Local[Unit] on Node = {
    val id: Future[ID] = (mid from node).asLocal
    id.onComplete {
      case Success(value) => remoteNodesIds.transform(_ + (node -> value))
      case Failure(_) => println(s"Failed to get id from $node")
    }
  }

  def removeExport(node: Remote[Node]): Local[Unit] on Node = {
    val id = remoteNodesIds.now(implicitly)(node)
    localExports.transform { case (myId, exports) =>
      (myId, exports.filterNot(_._1 == id))
    }
  }

  def updateConnections(nodes: Seq[Remote[Node]]): Local[Unit] on Node = {
    val remoteNodes = remoteNodesIds.now.keys.toSeq
    val nodesToAdd = nodes diff remoteNodes
    val nodesToRemove = remoteNodes diff nodes

    nodesToAdd foreach addRemoteNode
    nodesToRemove foreach removeExport

    remoteNodesIds.transform(_ -- nodesToRemove)
  }

  def main(): Unit on Node = {
    remote[Node].connected observe updateConnections
    val imSource = Math.random() < 0.5

    while (true) {
      val state = myState
      val myExports = exports(mid)
      val nbrRange = myExports.map { case (id, _) => id -> 1.0 }.toMap + (mid -> 0.0)
      val nbrSensors = Map(namespace.NBR_RANGE -> nbrRange)
      val sensors = mySensors(("source", imSource))
      val result = computeLocal(mid, state, myExports, sensors, nbrSensors)
      // at every round perform a remote call and send my id and export to all my neighbours (the connected nodes)
      remote.call(process(mid, result._1))
      actuation(mid, result._1, imSource)
      update(mid, result._1)
      Thread.sleep(1000)
    }
  }
}

@multitier object SimpleExampleP2P extends P2PSystem

object Network extends App {
  val initialPort: Int = 43053
  val numNodes = 4
  val endPort = initialPort + numNodes
  val ports = initialPort to endPort
  val Seq((firstPort, secondNode), middle @ _*) = ports.zip(ports.tail)

  val (lastPort, secondLastPort) = (ports.last, ports.head)
  val firstNode = TCP(firstPort).firstConnection -> TCP(secondNode).firstConnection
  val middleNodes = middle.map { case (current, next) => TCP("localhost", current) -> TCP(next).firstConnection }
  val lastNode = TCP("localhost", lastPort) -> TCP("localhost", secondLastPort)
  val nodes = firstNode +: middleNodes :+ lastNode
  nodes.foreach { case (node, next) =>
    val connections = connect[SimpleExampleP2P.Node](node) and connect[SimpleExampleP2P.Node](next)
    multitier.start(new Instance[SimpleExampleP2P.Node](connections))
  }
}

// A -> B
// B -> C
// C -> A
// C -> D
// D -> E
object A extends App {
  multitier start new Instance[SimpleExampleP2P.Node](listen[SimpleExampleP2P.Node] {
    TCP(43053)
  } and connect[SimpleExampleP2P.Node] {
    TCP("localhost", 43054)
  } and connect[SimpleExampleP2P.Node] {
    TCP("localhost", 43055)
  })
}

object B extends App {
  multitier start new Instance[SimpleExampleP2P.Node](
    listen[SimpleExampleP2P.Node] {
      TCP(43054)
    } and
      connect[SimpleExampleP2P.Node] {
        TCP("localhost", 43053)
      } and connect[SimpleExampleP2P.Node] {
        TCP("localhost", 43055)
      }
  )
}

object C extends App {
  multitier start new Instance[SimpleExampleP2P.Node](
    connect[SimpleExampleP2P.Node] {
      TCP("localhost", 43053)
    } and
      listen[SimpleExampleP2P.Node] {
        TCP(43055)
      } and connect[SimpleExampleP2P.Node] {
        TCP("localhost", 43054)
      } and connect[SimpleExampleP2P.Node] {
        TCP("localhost", 43056)
      }
  )
}

object D extends App {
  multitier start new Instance[SimpleExampleP2P.Node](
    listen[SimpleExampleP2P.Node] {
      TCP(43056)
    } and
      connect[SimpleExampleP2P.Node] {
        TCP("localhost", 43055)
      } and connect[SimpleExampleP2P.Node] {
        TCP("localhost", 43057)
      }
  )
}

object E extends App {
  multitier start new Instance[SimpleExampleP2P.Node](
    listen[SimpleExampleP2P.Node] {
      TCP(43057)
    } and
      connect[SimpleExampleP2P.Node] {
        TCP("localhost", 43056)
      }
  )
}
