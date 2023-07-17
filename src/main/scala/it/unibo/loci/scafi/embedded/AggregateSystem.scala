package it.unibo.loci.scafi.embedded

import it.unibo.loci.scafi.LociIncarnation._
import loci.language._
import loci.language.transmitter.rescala._
import loci.communicator.tcp._
import rescala.default._
import loci.serializer.circe._

import java.util.UUID
import io.circe.syntax.EncoderOps

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Random
import scala.util.Success
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions

@multitier class AggregateP2PSystem extends LogicalSystem {
  @peer type Node
  @peer type AggregateNode <: Node { type Tie <: Multiple[AggregateNode] with Single[BaseStation] }
  @peer type BaseStation <: Node { type Tie <: Multiple[AggregateNode] }

  private val namespace: Local[StandardSpatialSensorNames] on AggregateNode = new StandardSpatialSensorNames {}
  private var _state: EXPORT on AggregateNode = factory.emptyExport()
  private val mid: ID on AggregateNode = UUID.randomUUID().hashCode()
  private val localExports: Var[(ID, Map[ID, EXPORT])] on AggregateNode = Var((mid, Map.empty[ID, EXPORT]))
  private val remoteNodesIds: Local[Var[Map[Remote[AggregateNode], ID]]] on AggregateNode = Var(
    Map.empty[Remote[AggregateNode], ID]
  )
  private val currentNodeState: Evt[EXPORT] on AggregateNode = Evt[EXPORT]()

  def process(id: ID, export: EXPORT): Unit on AggregateNode =
    localExports.transform { case (myId, exports) => (myId, exports + (id -> export)) }

  override def state(id: ID): State on AggregateNode = _state

  override def update(id: ID, state: State): Unit on AggregateNode = this._state = state.asJson.as[EXPORT] match {
    case Right(value) => value
    case Left(value) => throw new Exception(value)
  }
  override def exports(id: Int): Set[(ID, EXPORT)] on AggregateNode = localExports.now._2.toSet
  def myState: State on AggregateNode = this.state(mid)
  def mySensors(sensor: (CNAME, SensorData)): Set[(CNAME, SensorData)] on AggregateNode = this.sense(mid, sensor)

  def computeLocal(
      id: ID,
      state: State,
      exports: Set[(ID, EXPORT)],
      sensors: Set[(CNAME, SensorData)],
      nbrSensors: Map[CNAME, Map[ID, Double]]
  ): (
      EXPORT,
      State
  ) on AggregateNode =
    super.compute(id, state, exports, sensors, nbrSensors)

  def addRemoteNode(node: Remote[AggregateNode]): Local[Unit] on AggregateNode = {
    val id: Future[ID] = (mid from node).asLocal
    id.onComplete {
      case Success(value) => remoteNodesIds.transform(_ + (node -> value))
      case Failure(_) => println(s"Failed to get id from $node")
    }
  }

  def removeExport(node: Remote[AggregateNode]): Local[Unit] on AggregateNode = {
    val id = remoteNodesIds.now(implicitly)(node)
    localExports.transform { case (myId, exports) =>
      (myId, exports.filterNot(_._1 == id))
    }
  }

  def updateConnections(nodes: Seq[Remote[AggregateNode]]): Local[Unit] on AggregateNode = {
    val remoteNodes = remoteNodesIds.now.keys.toSeq
    val nodesToAdd = nodes diff remoteNodes
    val nodesToRemove = remoteNodes diff nodes

    nodesToAdd foreach addRemoteNode
    nodesToRemove foreach removeExport

    remoteNodesIds.transform(_ -- nodesToRemove)
  }

  def aggregateResults(): Unit on BaseStation = {
    currentNodeState.asLocalFromAllSeq observe { case (remote, export) =>
      println(s"Received ${export.root[Any]()} from $remote")
    }
  }

  def main(): Unit on Node = on[AggregateNode] {
    remote[AggregateNode].connected observe updateConnections

    val imSource = Math.random() < 0.5

    while (true) {
      val state = myState
      val myExports = exports(mid)
      val nbrRange = myExports.map { case (id, _) => id -> 1.0 }.toMap + (mid -> 0.0)
      val nbrSensors: Map[CNAME, Map[ID, Double]] = Map(namespace.NBR_RANGE -> nbrRange)
      val sensors = mySensors(("source", imSource))
      val result = computeLocal(mid, state, myExports, sensors, nbrSensors)

//      // at every round perform a remote call and send my id and export to all my neighbours (the connected nodes)
      remote.call(process(mid, result._1))
      actuation(mid, result._1, imSource)
      currentNodeState.fire(result._1)
      update(mid, result._1)
      Thread.sleep(1000)
    }
  } and on[BaseStation] {
    aggregateResults()
  }
}

@multitier object SimpleExampleP2P extends AggregateP2PSystem

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
    val connections = connect[SimpleExampleP2P.AggregateNode](node) and connect[SimpleExampleP2P.AggregateNode](next)
    multitier.start(new Instance[SimpleExampleP2P.AggregateNode](connections))
  }
}

object BaseStationServer extends App {
  multitier start new Instance[SimpleExampleP2P.BaseStation](
    listen[SimpleExampleP2P.AggregateNode] {
      TCP(43052)
    }
  )
}

// A -> B
// B -> C
// C -> A
// C -> D
// D -> E
object A extends App {
  multitier start new Instance[SimpleExampleP2P.AggregateNode](
    listen[SimpleExampleP2P.AggregateNode] {
      TCP(43053)
    } and connect[SimpleExampleP2P.AggregateNode] {
      TCP("localhost", 43054)
    } and connect[SimpleExampleP2P.AggregateNode] {
      TCP("localhost", 43055)
    }
      and connect[SimpleExampleP2P.BaseStation] {
        TCP("localhost", 43052)
      }
  )
}

object B extends App {
  multitier start new Instance[SimpleExampleP2P.AggregateNode](
    listen[SimpleExampleP2P.AggregateNode] {
      TCP(43054)
    } and
      connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43053)
      } and connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43055)
      }
      and connect[SimpleExampleP2P.BaseStation] {
        TCP("localhost", 43052)
      }
  )
}

object C extends App {
  multitier start new Instance[SimpleExampleP2P.AggregateNode](
    connect[SimpleExampleP2P.AggregateNode] {
      TCP("localhost", 43053)
    } and
      listen[SimpleExampleP2P.AggregateNode] {
        TCP(43055)
      } and connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43054)
      } and connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43056)
      }
//      and connect[SimpleExampleP2P.BaseStation] {
//        TCP("localhost", 43052)
//      }
  )
}

object D extends App {
  multitier start new Instance[SimpleExampleP2P.AggregateNode](
    listen[SimpleExampleP2P.AggregateNode] {
      TCP(43056)
    } and
      connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43055)
      } and connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43057)
      }
//      and connect[SimpleExampleP2P.BaseStation] {
//        TCP("localhost", 43052)
//      }
  )
}

object E extends App {
  multitier start new Instance[SimpleExampleP2P.AggregateNode](
    listen[SimpleExampleP2P.AggregateNode] {
      TCP(43057)
    } and
      connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43056)
      }
//      and connect[SimpleExampleP2P.BaseStation] {
//        TCP("localhost", 43052)
//      }
  )
}
