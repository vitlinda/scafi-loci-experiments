package it.unibo.loci.scafi.betterimpl.encapsulation

import io.circe.syntax.EncoderOps
import it.unibo.loci.scafi.experiments.commons.LociIncarnation._
import java.util.UUID
import loci.communicator.tcp.TCP
import loci.language._
import loci.language.on
import loci.language.transmitter.rescala._
import loci.serializer.circe._
import rescala.default._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

@multitier trait AggregateUtils {
  type SensorData[A] = A
  type State = EXPORT

  @peer type AggregateNode

  def compute[A](
      id: ID,
      state: State,
      exports: Set[(ID, EXPORT)],
      sensors: Set[(CNAME, SensorData[A])],
      nbrSensors: Map[CNAME, Map[ID, Double]],
      program: AggregateProgram
  ): Local[(EXPORT, State)] on AggregateNode = {
    val sensorsMap = sensors.map { case (id, value) => (id, value) }.toMap
    val context = new ContextImpl(id, exports + (id -> state), sensorsMap, nbrSensors)

    val result = program.round(context)
    (result, result)
  }

  def actuation[A](id: ID, export: EXPORT, sensors: Set[(CNAME, SensorData[A])]): Local[Unit] on AggregateNode =
    println(s"id: $id ($sensors) -- ${export.root[Any]()} \n")

  def sense[A](id: ID, sensor: Set[(CNAME, SensorData[A])]): Local[Set[(CNAME, SensorData[A])]] on AggregateNode =
    sensor

  def state(id: ID): EXPORT on AggregateNode = factory.emptyExport()
  def update(id: ID, state: State): Unit on AggregateNode = on[AggregateNode] {
    (factory.emptyExport(), factory.emptyExport())
  }
  def exports(id: ID): Set[(ID, EXPORT)] on AggregateNode
}

@multitier class AggregateSystem(program: => AggregateProgram) extends AggregateUtils {
  @peer type AggregateNode <: { type Tie <: Multiple[AggregateNode] }

  private val namespace = new StandardSpatialSensorNames {}
  private var _state: EXPORT on AggregateNode = factory.emptyExport()
  private val mid: ID on AggregateNode = UUID.randomUUID().hashCode()
  private val localExports: Local[Var[(ID, Map[ID, EXPORT])]] on AggregateNode = Var((mid, Map.empty[ID, EXPORT]))
  private val remoteNodesIds: Local[Var[Map[Remote[AggregateNode], ID]]] on AggregateNode = Var(
    Map.empty[Remote[AggregateNode], ID]
  )

  override def state(id: ID): State on AggregateNode = _state

  override def update(id: ID, state: State): Unit on AggregateNode = {
    this._state = state.asJson.as[EXPORT] match {
      case Right(value) => value
      case Left(value) => throw new Exception(value)
    }
  }

  override def exports(id: ID): Set[(ID, EXPORT)] on AggregateNode = localExports.now._2.toSet

  def observeConnections(): Unit on AggregateNode = remote[AggregateNode].connected observe updateConnections

  def process(id: ID, export: EXPORT): Unit on AggregateNode =
    localExports.transform { case (myId, exports) => (myId, exports + (id -> export)) }

  def myState: State on AggregateNode = this.state(mid)

  def mySensors[A](sensor: Set[(CNAME, SensorData[A])]): Local[Set[(CNAME, SensorData[A])]] on AggregateNode =
    this.sense(mid, sensor)

  def computeLocal[A](
      id: ID,
      state: State,
      exports: Set[(ID, EXPORT)],
      sensors: Set[(CNAME, SensorData[A])],
      nbrSensors: Map[CNAME, Map[ID, Double]]
  ): Local[(EXPORT, State)] on AggregateNode =
    super.compute(id, state, exports, sensors, nbrSensors, program)

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

  def performRound[A](sens: Set[(CNAME, SensorData[A])]): Local[(EXPORT, State)] on AggregateNode = {
    val state = myState
    val myExports = exports(mid)
    val nbrRange = myExports.map { case (id, _) => id -> 1.0 }.toMap + (mid -> 0.0)
    val nbrSensors = Map(namespace.NBR_RANGE -> nbrRange)
    val sensors = mySensors(sens)
    val result = computeLocal(mid, state, myExports, sensors, nbrSensors)
    remote.call(process(mid, result._1))
    actuation(mid, result._1, sens)
    update(mid, result._1)
    result
  }
}

@multitier object AL {
  @multitier object as extends AggregateSystem(new Gradient)
  @peer type Node
  @peer type BaseStation <: Node { type Tie <: Multiple[AGNode] }
  @peer type AGNode <: as.AggregateNode with Node { type Tie <: Multiple[as.AggregateNode] with Single[BaseStation] }

  private val currentNodeState: Evt[EXPORT] on AGNode = Evt[EXPORT]()

  def gatherValues(): Unit on BaseStation = {
    currentNodeState.asLocalFromAllSeq observe { case (remote, nodeOutput) =>
      println(s"Output: $nodeOutput from remote: $remote")
    }
  }

  def main(): Unit on Node = on[AGNode] {
    as.observeConnections()
    // define program sensors
    val source: as.SensorData[Boolean] = Math.random() < 0.5
    val sensors = Set(("source", source))

    while (true) {
      val res = as.performRound(sensors)
      currentNodeState.fire(res._1)
      Thread.sleep(1000)
    }
  } and on[BaseStation] {
    gatherValues()
  }
}

class Gradient extends AggregateProgram with StandardSensors {
  override def main(): Any =
    rep(Double.PositiveInfinity) { distance =>
      mux(sense[Boolean]("source"))(0.0) {
        minHoodPlus {
          nbr(distance) + nbrRange
        }
      }
    }
}

object BaseStationNode extends App {
  multitier start new Instance[AL.BaseStation](
    listen[AL.AGNode] {
      TCP(43052)
    }
  )
}

object A extends App {
  multitier start new Instance[AL.AGNode](
    listen[AL.AGNode] {
      TCP(43053)
    }
//      and connect[AL.AGNode] {
//      TCP("localhost", 43054)
//    } and connect[AL.AGNode] {
//      TCP("localhost", 43055)
//    }
      and connect[AL.BaseStation] {
        TCP("localhost", 43052)
      }
  )
}

object B extends App {
  multitier start new Instance[AL.AGNode](
    listen[AL.AGNode] {
      TCP(43054)
    } and
      connect[AL.AGNode] {
        TCP("localhost", 43053)
      } and connect[AL.AGNode] {
        TCP("localhost", 43055)
      }
      and connect[AL.BaseStation] {
        TCP("localhost", 43052)
      }
  )
}
