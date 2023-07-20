package it.unibo.loci.scafi.experiments.basestation

import io.circe.syntax.EncoderOps
import it.unibo.loci.scafi.experiments.commons.LociIncarnation._
import it.unibo.loci.scafi.experiments.commons.LogicalSystem
import java.util.UUID
import loci.language._
import loci.language.transmitter.rescala._
import loci.serializer.circe._
import rescala.default._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

@multitier trait AggregateBaseStation extends LogicalSystem with Monitoring {
  @peer type BaseStation <: Monitor { type Tie <: Multiple[AggregateNode] with Multiple[Monitored] }
  @peer type AggregateNode <: Monitored {
    type Tie <: Multiple[AggregateNode] with Optional[BaseStation] with Optional[Monitor]
  }

  private val namespace: Local[StandardSpatialSensorNames] on AggregateNode = new StandardSpatialSensorNames {}
  private var _state: EXPORT on AggregateNode = factory.emptyExport()
  private val mid: ID on AggregateNode = UUID.randomUUID().hashCode()
  private val localExports: Var[(ID, Map[ID, EXPORT])] on AggregateNode = Var((mid, Map.empty[ID, EXPORT]))
  private val remoteNodesIds: Local[Var[Map[Remote[AggregateNode], ID]]] on AggregateNode = Var(
    Map.empty[Remote[AggregateNode], ID]
  )
  private val currentNodeState: Evt[(EXPORT, Set[(ID, EXPORT)])] on AggregateNode = Evt[(EXPORT, Set[(ID, EXPORT)])]()

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

  def gatherValues(): Unit on BaseStation = {
    currentNodeState.asLocalFromAllSeq observe { case (remote, (nodeOutput, nodeExports)) =>
      super.monitorNode(remote, nodeOutput, nodeExports)
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
      // at every round perform a remote call and send my id and export to all my neighbours (the connected nodes)
      remote.call(process(mid, result._1))
      actuation(mid, result._1, imSource)
      update(mid, result._1)
      currentNodeState.fire(result._1 -> myExports)
      Thread.sleep(1000)
    }
  } and on[BaseStation] {
    gatherValues()
  }
}
