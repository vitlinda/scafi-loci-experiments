package it.unibo.loci.scafi


import loci._
import loci.communicator.tcp._
import loci.language.Placement
import rescala.default._
import loci.transmitter.rescala._
import loci.serializer.circe._
import rescala.parrp.ParRPStruct
import rescala.reactives

import java.util.UUID
import LociIncarnation._

import scala.concurrent.Future
import scala.util.Success
/**
 * Reference article : Pulverization in Cyber-Physical Systems: Engineering the Self-Organizing Logic Separated from Deployment
 * DOI : https://doi.org/10.3390/fi12110203
 */
@multitier trait LogicalSystem { //defines only the component and the behaviour. The interaction is deployment-specific
  type SensorData = String // should be any or a wrap data class
  type State = String // should be coherent with the aggregate computing context
  /**
   * Logical components used to define a device in a cyber-physical system.
   */
  // γ
  @peer type BehaviourComponent
  def compute(id : ID, state : State, exports : Set[(ID, EXPORT)], sensors : Set[(LSNS, SensorData)]): (EXPORT, State) on BehaviourComponent = {
    val sensorsMap = sensors.map { case (id, value) => (id, (value : Any)) }.toMap
    val context = new ContextImpl(id, exports, sensorsMap, Map.empty) //todo
    val program = new AggregateProgram { //make it trasmittable
      override def main(): Any = foldhood(0)(_ + _)(1)
    }
    (program.round(context), state)
  }
  // α
  @peer type ActuatorComponent
  def actuation(id : ID, export : EXPORT) : Unit on ActuatorComponent = { println(s"id : $id --- export : ${export}")}
  // σ
  @peer type SensorComponent
  def sense(id : ID) : Set[(LSNS, SensorData)] on SensorComponent = Set.empty[(LSNS, SensorData)]
  // k
  @peer type StateComponent
  def state(id : ID) : State on StateComponent = on[StateComponent] { "empty" }
  def update(id : ID, state : State) : Unit on StateComponent = on[StateComponent] { }
  // x
  @peer type CommunicationComponent
  def exports(id : ID) : Set[(ID, EXPORT)] on CommunicationComponent
}

@multitier class P2PSystem extends LogicalSystem {
  @peer type Node <: { type Tie <: Multiple[Node] }
  @peer type BehaviourComponent <: Node
  @peer type ActuatorComponent <: Node
  @peer type SensorComponent <: Node
  @peer type StateComponent <: Node
  @peer type CommunicationComponent <: Node

  private val id : ID on Node = { UUID.randomUUID().hashCode() }
  private val localExports : Var[Set[(ID, EXPORT)]] on Node = on[Node] { Var(Set.empty[(ID, EXPORT)]) }
  def process(id : ID, export : EXPORT) : Unit on Node = placed {
    localExports.transform(exports => exports + (id -> export))
  }

  override def exports(id: Int): Set[(ID, EXPORT)] on Node = localExports.now

  def myState : State on Node = this.state(id)
  def mySensors : Set[(LSNS, SensorData)] on Node = this.sense(id)

  def computeLocal(id : ID,
                       state : State,
                       exports : Set[(ID, EXPORT)],
                       sensors : Set[(LSNS, SensorData)]): (EXPORT, State) on Node = {

    super.compute(id, state, exports, sensors)
  }

  def main() : Unit on Node = {
    while(true) {
      val state = myState
      val myExports = exports(id)
      val sensors = mySensors
      val result = computeLocal(id, state, myExports, sensors)
      remote.call(process(id, result._1))
      actuation(id, result._1)
      update(id, result._2)
      Thread.sleep(1000)
    }
  }
}

@multitier class Broker extends LogicalSystem {
  @peer type Root
  @peer type Node <: Root { type Tie <: Single[Broker] }
  @peer type Broker <: Root { type Tie <: Multiple[Node] }
  @peer type BehaviourComponent <: Node
  @peer type ActuatorComponent <: Node
  @peer type SensorComponent <: Node
  @peer type StateComponent <: Node
  @peer type CommunicationComponent <: Broker

  private val id : ID on Node = { UUID.randomUUID().hashCode() }
  private val brokerExport : Var[Set[(ID, EXPORT)]] on Broker = on[Broker] { Var(Set.empty[(ID, EXPORT)]) }
  private def localExport(id : ID) : Future[Set[(ID, EXPORT)]] on Node = placed {
    remote[Broker].call(exportFixed(id)).asLocal
  }
  def exportFixed(id: Int): Set[(ID, EXPORT)] on Broker = {
    brokerExport.now.filter(_._1 != id)
  }
  //Has problems.. override doesn't work very well I suppose..
  override def exports(id: Int): Set[(ID, EXPORT)] on Broker = {
    brokerExport.now.filter(_._1 != id)
  }
  def process(id : ID, export : EXPORT) : Unit on Broker = brokerExport.transform(exports => exports + (id -> export))
  def myState : State on Node = this.state(id)
  def mySensors : Set[(LSNS, SensorData)] on Node = this.sense(id)

  def computeLocal(id : ID,
                   state : State,
                   exports : Set[(ID, EXPORT)],
                   sensors : Set[(LSNS, SensorData)]): (EXPORT, State) on Node = {
    super.compute(id, state, exports, sensors)
  }
  import scala.concurrent.ExecutionContext.Implicits.global
  def main() : Unit on Root = (on[Node] {
    println("Device starts ...")
    logic()
  }) and (on[Broker] {
    println("Broker starts ...")
  })

  def logic() : Future[Unit] on Node = {
    val futureExport = localExport(id)
    futureExport.flatMap {
      export => {
        val sensors = mySensors
        val state = myState
        val result = computeLocal(id, state, export, sensors)
        update(id, result._2)
        actuation(id, result._1)
        remote.call(process(id, result._1)).asLocal
      }
    }.flatMap { a => Future { Thread.sleep(1000) } }.flatMap(_ => logic())
  }
}

@multitier object SimpleExampleP2P extends P2PSystem()
@multitier object SimpleExampleBroker extends Broker()

object SystemPeerTest extends App {
  val port = 3245
  multitier.start(new Instance[SimpleExampleP2P.Node](
    connect[SimpleExampleP2P.Node](TCP(port).firstConnection)
  ))
  multitier.start(new Instance[SimpleExampleP2P.Node](
    connect[SimpleExampleP2P.Node](TCP("localhost", port))
  ))
}

object SystemBrokerTest extends App {
  val port = 3245
  multitier.start(new Instance[SimpleExampleBroker.Broker](
    listen[SimpleExampleBroker.Node](TCP(port))
  ))
  multitier.start(new Instance[SimpleExampleBroker.Node](
    connect[SimpleExampleBroker.Broker](TCP("localhost", port))
  ))
  multitier.start(new Instance[SimpleExampleBroker.Node](
    connect[SimpleExampleBroker.Broker](TCP("localhost", port))
  ))
}