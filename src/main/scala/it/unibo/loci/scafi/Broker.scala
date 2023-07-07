package it.unibo.loci.scafi

import loci.communicator.tcp.TCP
import loci.language._
import rescala.default._
import loci.transmitter.rescala._
import loci.serializer.circe._

import java.util.UUID
import LociIncarnation._

import scala.concurrent.Future

@multitier class Broker extends LogicalSystem {
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

@multitier object SimpleExampleBroker extends Broker()

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
