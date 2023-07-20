package it.unibo.loci.scafi.experiments.basestation

import it.unibo.loci.scafi.experiments.commons.LociIncarnation._
import loci.language._
import rescala.default._

@multitier trait Monitoring {
  @peer type Node
  @peer type Monitor <: Node { type Tie <: Multiple[Monitored] }
  @peer type Monitored <: Node { type Tie <: Optional[Monitor] }

  def monitorNode(remote: Remote[Monitored], export: EXPORT, nbrSensors: Map[CNAME, Map[ID, Double]]): Local[Unit] on Monitor = println(
    s"Received value: ${export.root[Any]()} nbrValues: $nbrSensors from: $remote"
  )
}
