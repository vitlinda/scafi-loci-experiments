package it.unibo.loci.scafi.embedded

import loci.language._


@multitier trait Monitoring {
  @peer type Monitor <: { type Tie <: Multiple[Monitored] }
  @peer type Monitored <: { type Tie <: Single[Monitor] }

  def monitoredTimedOut(monitored: Remote[Monitored]): Local[Unit] on Monitor = ???
}


@multitier class CombinedSystem extends Monitoring {
  @peer type BaseStation <: Monitor { type Tie <: Multiple[AggregateSystem] with Multiple[Monitored] }
  @peer type AggregateSystem <: Monitored { type Tie <: Single[BaseStation] with Single [Monitor] }


}