package it.unibo.loci.scafi.pulverization

import io.circe.syntax.EncoderOps
import it.unibo.loci.scafi.experiments.commons.LociIncarnation._
import java.util.UUID
import loci.communicator.tcp.TCP
import loci.language._
import loci.language.transmitter.rescala._
import loci.serializer.circe._
import rescala.default._
import scala.concurrent.Future

/** Reference article : Pulverization in Cyber-Physical Systems: Engineering the Self-Organizing Logic Separated from
  * Deployment DOI : https://doi.org/10.3390/fi12110203
  */

@multitier trait LogicalPulvSystem { // defines only the component and the behaviour. The interaction is deployment-specific
  type SensorData = String // should be any or a wrap data class
  type State = EXPORT // should be coherent with the aggregate computing context
  /** Logical components used to define a device in a cyber-physical system.
    */
  // γ
  @peer type BehaviourComponent
  def compute(id: ID, state: State, exports: Set[(ID, EXPORT)], sensors: Set[(CNAME, SensorData)]): (
      EXPORT,
      State
  ) on BehaviourComponent = {
    val sensorsMap = sensors.map { case (id, value) => (id, (value: Any)) }.toMap
    val context = new ContextImpl(id, exports + (id -> state), sensorsMap, Map.empty) // todo
    val program = new AggregateProgram { // make it trasmittable
      override def main(): Any = foldhood(Set.empty[ID])(_ ++ _)(nbr(Set(mid)))
    }
    println(s"exportssss: ${context.exports()}")
    val result = program.round(context)
    (result, result)
  }
  // α
  @peer type ActuatorComponent
  def actuation(id: ID, export: EXPORT): Unit on ActuatorComponent =
    println(s"id : $id --- result : ${export.root[Any]()} |---| export : $export \n")
  // σ
  @peer type SensorComponent
  def sense(id: ID): Set[(CNAME, SensorData)] on SensorComponent = Set.empty[(CNAME, SensorData)]
  // k
  @peer type StateComponent
  def state(id: ID): State on StateComponent = on[StateComponent](factory.emptyExport())
  def update(id: ID, state: State): Unit on StateComponent = on[StateComponent] {}
  // x
  @peer type CommunicationComponent
  def exports(id: ID): Set[(ID, EXPORT)] on CommunicationComponent
}

@multitier class P2PSystem extends LogicalPulvSystem {
  @peer type Node <: { type Tie <: Multiple[Node] }
  @peer type BehaviourComponent <: Node
  @peer type ActuatorComponent <: Node
  @peer type SensorComponent <: Node
  @peer type StateComponent <: Node
  @peer type CommunicationComponent <: Node

  private var _state: EXPORT on Node = factory.emptyExport()
  private val mid: ID on Node = UUID.randomUUID().hashCode()
  private val localExports: Var[(ID, Map[ID, EXPORT])] on Node = on[Node](Var((mid, Map.empty[ID, EXPORT])))
  def process(id: ID, export: EXPORT): Unit on Node = placed {
    println(
      "remote  " + id + " local: " + mid + " export: " + export.root[Any]() + " local exports " + localExports.now
    )
    println("_____")
    localExports.transform { case (myId, exports) => (myId, exports + (id -> export)) }
  }

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
    while (true) {
      val state = myState
      val myExports = exports(mid)
      val sensors = mySensors
      val result = computeLocal(mid, state, myExports, sensors)
      remote.call(process(mid, result._1))
      actuation(mid, result._1)
      update(mid, result._1)
      Thread.sleep(1000)
    }
  }
}

@multitier class Broker extends LogicalPulvSystem {
  @peer type Root
  @peer type Node <: Root { type Tie <: Single[Broker] }
  @peer type Broker <: Root { type Tie <: Multiple[Node] }
  @peer type BehaviourComponent <: Node
  @peer type ActuatorComponent <: Node
  @peer type SensorComponent <: Node
  @peer type StateComponent <: Node
  @peer type CommunicationComponent <: Broker

  private var _state: EXPORT on Node = factory.emptyExport()
  private val id: ID on Node = UUID.randomUUID().hashCode()
  private val brokerExport: Var[Map[ID, EXPORT]] on Broker = on[Broker](Var(Map.empty[ID, EXPORT]))
  private def localExport(id: ID): Future[Set[(ID, EXPORT)]] on Node = placed {
    remote[Broker].call(exportFixed(id)).asLocal
  }

  override def state(id: ID): State on StateComponent = _state

  override def update(id: ID, state: State): Unit on StateComponent = this._state = state
  def exportFixed(id: Int): Set[(ID, EXPORT)] on Broker =
    brokerExport.now.filter(_._1 != id).toSet
  // Has problems.. override doesn't work very well I suppose..
  override def exports(id: Int): Set[(ID, EXPORT)] on Broker =
    brokerExport.now.filter(_._1 != id).toSet
  def process(id: ID, export: EXPORT): Unit on Broker = brokerExport.transform(exports => exports + (id -> export))
  def myState: State on Node = this.state(id)
  def mySensors: Set[(CNAME, SensorData)] on Node = this.sense(id)

  def computeLocal(id: ID, state: State, exports: Set[(ID, EXPORT)], sensors: Set[(CNAME, SensorData)]): (
      EXPORT,
      State
  ) on Node =
    super.compute(id, state, exports, sensors)
  import scala.concurrent.ExecutionContext.Implicits.global
  def main(): Unit on Root = on[Node] {
    println("Device starts ...")
    logic()
  } and on[Broker] {
    println("Broker starts ...")
  }

  def logic(): Future[Unit] on Node = {
    val futureExport = localExport(id)
    futureExport.flatMap { export =>
      val sensors = mySensors
      val state = myState
      val result = computeLocal(id, state, export, sensors)
      update(id, result._2)
      actuation(id, result._1)
      remote.call(process(id, result._1)).asLocal
    }.flatMap(a => Future(Thread.sleep(1000))).flatMap(_ => logic())
  }
}

@multitier object SimpleExampleP2P extends P2PSystem()
@multitier object SimpleExampleBroker extends Broker()

object SystemPeerTest extends App {
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

object SystemBrokerTest extends App {
  val port = 3245
  multitier.start(
    new Instance[SimpleExampleBroker.Broker](
      listen[SimpleExampleBroker.Node](TCP(port))
    )
  )
  multitier.start(
    new Instance[SimpleExampleBroker.Node](
      connect[SimpleExampleBroker.Broker](TCP("localhost", port))
    )
  )
  multitier.start(
    new Instance[SimpleExampleBroker.Node](
      connect[SimpleExampleBroker.Broker](TCP("localhost", port))
    )
  )
}
