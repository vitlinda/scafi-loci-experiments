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
// Incarnation used to define aggregate programs
import LociIncarnation._
// A template for a multitier aggregate system. It defines i) node type, ii) placement types and iii) data stream. Program isn't known a priori
@multitier class P2PAggregateSystemTemplate(program : => AggregateProgram) {
  @peer type Node <: { type Tie <: Multiple[Node] }
  // Placement type that gives a random UUID for each node in the system
  val id: ID on Node = UUID.randomUUID.hashCode()
  // Exports produced by each node. It is a "reactive stream". It is event because is discrete (fire when new export is produced)
  val localExports : Evt[(ID, EXPORT)] on Node = Evt[(ID, EXPORT)]
  // Timer used to compute new round
  val timer : Var[Long] on Node = Var(0L)
  // Export produced by neighbourhood.
  val neighboursExports: reactives.Event[(ID, EXPORT), ParRPStruct] per Node on Node = on[Node].sbj {
    implicit ctx: Placement.Context[Node] => //helps type check
      node: Remote[Node] => localExports
        .asLocalFromAllSeq //access to remote data contained in local exports and create a new stream of export produced by neighbourhood
        .collect { case (remote, message) if remote != node => message }
  }
  // Define the behaviour of the node
  def main() : Unit on Node = {
    on[Node] { implicit ctx : Placement.Context[Node] => //helps type check
      val period = 1000L
      // Creates a context map from neighborhood exports produced
      val exportsSignal = neighboursExports
        .asLocalFromAllSeq
        .fold(Map.empty[ID, EXPORT]) { case (exports, (_, (neighId, export))) => exports + (neighId -> export) }
      // Proactive round evaluation
      timer.observe {
        _ =>
          val exports = exportsSignal.readValueOnce // Access to current exports representation
          val context = new ContextImpl(id, exports, Map.empty, Map.empty) // Create context for aggregate execution
          val e = program.round(context) // Evaluate the aggregate program
          localExports.fire(id -> e) // Fire new export produced
      }
      // Some actuation, print the export produced in the stdout
      localExports.observe { case (id, export) => println(s"my id : ${id}, export produced : ${export.root[Any]()}")}
      // Proactive loop, change timer each period time
      while(true) {
        timer.set(timer.now + period)
        Thread.sleep(period)
      }
    }
  }
}

class AggregateNeighboursId extends AggregateProgram {
  override def main(): Any = foldhood(Set.empty[ID])(_++_)(nbr{Set(mid)})
}
// A concrete aggregate system. It shares the same aggregate program within all nodes.
@multitier object P2PAggregateSystem extends P2PAggregateSystemTemplate(new AggregateNeighboursId)
// Token-Ring like ties
object TokenRingAggregateSystem extends App {
  val initialPort: Int = if(args.length == 1) args(0).toInt else 43053
  val numNodes = 10
  val endPort = initialPort + numNodes
  val ports = initialPort to endPort
  val Seq((firstPort, secondNode), middle @ _*) = ports zip ports.tail
  val (lastPort, secondLastPort) = (ports.last, ports.head)
  val firstNode = TCP(firstPort).firstConnection -> TCP(secondNode).firstConnection
  val middleNodes = middle.map { case (current, next) =>  TCP("localhost", current) -> TCP(next).firstConnection }
  val lastNode = TCP("localhost", lastPort) -> TCP("localhost", secondLastPort)
  val nodes = firstNode +: middleNodes :+ lastNode
  nodes.foreach { case (node, next) =>
    val connections = connect[P2PAggregateSystem.Node](node) and connect[P2PAggregateSystem.Node](next)
    multitier.start(new Instance[P2PAggregateSystem.Node](connections))
  }
}
