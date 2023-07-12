package it.unibo.loci.scafi

import loci.language._
import LociIncarnation._

/** Reference article : Pulverization in Cyber-Physical Systems: Engineering the Self-Organizing Logic Separated from
  * Deployment DOI : https://doi.org/10.3390/fi12110203
  */
@multitier trait LogicalSystem { // defines only the component and the behaviour. The interaction is deployment-specific
  type SensorData = Boolean // should be any or a wrap data class
  type State = EXPORT // should be coherent with the aggregate computing context

  /** Logical components used to define a device in a cyber-physical system.
    */
  // γ
  @peer type BehaviourComponent
  def compute(
      id: ID,
      state: State,
      exports: Set[(ID, EXPORT)],
      sensors: Set[(CNAME, SensorData)],
      nbrSensors: Map[CNAME, Map[ID, Double]]
  ): (
      EXPORT,
      State
  ) on BehaviourComponent = {
    val sensorsMap = sensors.map { case (id, value) => (id, (value: Any)) }.toMap
    val context = new ContextImpl(id, exports + (id -> state), sensorsMap, nbrSensors)
    val program = Programs.gradient()

    val result = program.round(context)
    (result, result)
  }

  // α
  @peer type ActuatorComponent
  def actuation(id: ID, export: EXPORT, imSource: Boolean): Unit on ActuatorComponent =
    println(s"id: $id ${if (imSource) " (source)" else "         "} -- ${export.root[Any]()} \n")

  // σ
  @peer type SensorComponent
  def sense(id: ID, sensor: (CNAME, SensorData)): Set[(CNAME, SensorData)] on SensorComponent = Set(
    //    ("temperature", 20.0),
    sensor
  )
  // k
  @peer type StateComponent
  def state(id: ID): State on StateComponent = on[StateComponent](factory.emptyExport())
  def update(id: ID, state: State): Unit on StateComponent = on[StateComponent] {}
  // x
  @peer type CommunicationComponent
  def exports(id: ID): Set[(ID, EXPORT)] on CommunicationComponent
}

object Programs {
  def idOfNeighbours() = new AggregateProgram {
    override def main(): Any = foldhood(Set.empty[ID])(_ ++ _)(nbr(Set(mid)))
  }

  def averageTemperature() = new AggregateProgram {
    override def main(): Any = foldhood(0.0)(_ + _)(nbr(sense[Double]("temperature"))) / foldhood(0)(_ + _) {
      1
    }
  }

  // minimum distances from any node to its closest “source node”.
  def gradient() = new AggregateProgram with StandardSensors {
    override def main(): Any = rep(Double.PositiveInfinity) {
      distance =>
        mux(sense[Boolean]("source"))(0.0) {
          minHoodPlus {
            nbr(distance) + nbrRange
          }
        }
    }  + " -- " + foldhood(Set.empty[ID])(_ ++ _)(nbr(Set(mid)))
  }

}
