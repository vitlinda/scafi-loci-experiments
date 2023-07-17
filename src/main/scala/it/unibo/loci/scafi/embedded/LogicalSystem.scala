package it.unibo.loci.scafi.embedded

import loci.language._
import it.unibo.loci.scafi.LociIncarnation._


@multitier trait LogicalSystem { // defines only the component and the behaviour. The interaction is deployment-specific
  type SensorData = Boolean // should be any or a wrap data class
  type State = EXPORT // should be coherent with the aggregate computing context

  @peer type AggregateNode
  def compute(
      id: ID,
      state: State,
      exports: Set[(ID, EXPORT)],
      sensors: Set[(CNAME, SensorData)],
      nbrSensors: Map[CNAME, Map[ID, Double]]
  ): (
      EXPORT,
      State
  ) on AggregateNode = {
    val sensorsMap = sensors.map { case (id, value) => (id, (value: Any)) }.toMap
    val context = new ContextImpl(id, exports + (id -> state), sensorsMap, nbrSensors)
    val program = Programs.gradient()

    val result = program.round(context)
    (result, result)
  }

  def actuation(id: ID, export: EXPORT, imSource: Boolean): Unit on AggregateNode =
    println(s"id: $id ${if (imSource) " (source)" else "         "} -- ${export.root[Any]()} \n")

  def sense(id: ID, sensor: (CNAME, SensorData)): Set[(CNAME, SensorData)] on AggregateNode = Set(
    //    ("temperature", 20.0),
    sensor
  )

  def state(id: ID): State on AggregateNode = on[AggregateNode](factory.emptyExport())
  def update(id: ID, state: State): Unit on AggregateNode = on[AggregateNode] {}
  def exports(id: ID): Set[(ID, EXPORT)] on AggregateNode
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
    override def main(): Any = rep(Double.PositiveInfinity) { distance =>
      mux(sense[Boolean]("source"))(0.0) {
        minHoodPlus {
          nbr(distance) + nbrRange
        }
      }
    } + " -- " + foldhood(Set.empty[ID])(_ ++ _)(nbr(Set(mid)))
  }

}
