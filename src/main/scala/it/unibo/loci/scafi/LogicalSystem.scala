package it.unibo.loci.scafi

import loci._

/**
 * Reference article : Pulverization in Cyber-Physical Systems: Engineering the Self-Organizing Logic Separated from Deployment
 * DOI : https://doi.org/10.3390/fi12110203
 */
@multitier trait LogicalSystem {
  /**
   * Logical components used to define a device in a cyber-physical system.
   */
  // γ
  @peer type Behaviour <: { type Tie <: Single[State] }
  // α
  @peer type Actuators
  // σ
  @peer type Sensors <: { type Tie <: Single[State] }
  // k
  @peer type State <: { type Tie <: Single[Actuators] with Single[Communication] with Single[Behaviour] }
  // x
  @peer type Communication <: { type Tie <: Multiple[Communication] with Single[State] }
}
