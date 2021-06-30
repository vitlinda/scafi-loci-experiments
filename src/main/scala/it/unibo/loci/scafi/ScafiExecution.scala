package it.unibo.loci.scafi

import loci._
import loci.communicator.tcp._
import rescala.default._
import loci.transmitter.rescala._
import loci.serializer.circe._

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala

@multitier object ScafiExecution {
  import LociIncarnation._
  type Id = ID
  type Actuation = String
  type Sensing = Int
  @peer type Node <: { type Tie <: Multiple[Node] }

  val id: Id on Node = on[Node] { UUID.randomUUID.hashCode() }
  val localExports : Evt[(Id, EXPORT)] on Node = on[Node] { Evt[(Id, EXPORT)] }
  val neighboursExports = on[Node] sbj {
    node: Remote[Node] => localExports.asLocalFromAllSeq.collect { case (remote, message) => message }
  }

  def main() = {
    on[Node] {
      val exports = new ConcurrentHashMap[ID, EXPORT]().asScala
      neighboursExports.asLocalFromAllSeq.observe {
        case (_, (neighId, export)) => exports.put(neighId, export).foreach(data => data)
      }
      while(true) {
        val program = new AggregateProgram { override def main(): Any = foldhood(Set.empty[ID])(_++_)(nbr{Set(mid)}) }
        val context = new ContextImpl(id, exports, Map.empty, Map.empty)
        val e = program.round(context)
        localExports.fire(id -> e)
        println(s"My id : $id -- export : " + e.root[Any]())
        Thread.sleep(1000)
      }
    }
  }
}

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
    val connections = connect[ScafiExecution.Node](node) and connect[ScafiExecution.Node](next)
    multitier.start(new Instance[ScafiExecution.Node](connections))
  }
}
