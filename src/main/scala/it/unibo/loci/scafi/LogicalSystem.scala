package it.unibo.loci.scafi

import loci.language._
import LociIncarnation._

/** Reference article : Pulverization in Cyber-Physical Systems: Engineering the Self-Organizing Logic Separated from
  * Deployment DOI : https://doi.org/10.3390/fi12110203
  */
@multitier trait LogicalSystem { // defines only the component and the behaviour. The interaction is deployment-specific
  type SensorData = Double // should be any or a wrap data class
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
    val program = new AggregateProgram { // make it transmittable
      override def main(): Any = foldhood(Set.empty[ID])(_ ++ _)(nbr(Set(mid)))
      // foldhood(0.0){_+_}{nbr{sense[Double]("temperature")}} / foldhood(0)(_+_){1}
    }
//    println(s"exportssss: ${context.exports()}")
    val result = program.round(context)
    (result, result)
  }
  // α
  @peer type ActuatorComponent
  def actuation(id: ID, export: EXPORT): Unit on ActuatorComponent =
    println(s"id : $id --- result : ${export.root[Any]()} |---| export : ${export.root[Any]()} \n")
  // σ
  @peer type SensorComponent
  def sense(id: ID): Set[(CNAME, SensorData)] on SensorComponent = Set(
    ("temperature", 40.0)
  ) // Set.empty[(CNAME, SensorData)]
  // k
  @peer type StateComponent
  def state(id: ID): State on StateComponent = on[StateComponent](factory.emptyExport())
  def update(id: ID, state: State): Unit on StateComponent = on[StateComponent] {}
  // x
  @peer type CommunicationComponent
  def exports(id: ID): Set[(ID, EXPORT)] on CommunicationComponent
}
