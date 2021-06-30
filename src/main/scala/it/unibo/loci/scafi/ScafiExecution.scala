package it.unibo.loci.scafi

import loci._
import loci.transmitter.rescala._
import loci.serializer.circe._
import loci.communicator.tcp._
import rescala.default._

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
  val messageStream : Evt[(Id, EXPORT)] on Node = on[Node] { Evt[(Id, EXPORT)] }
  val receivedMessages = on[Node] sbj {
    node: Remote[Node] =>
      messageStream.asLocalFromAllSeq collect { case (remote, message) if remote != node => message }
  }

  def main() = {
    on[Node] {
      val map = new ConcurrentHashMap[ID, EXPORT]().asScala
      receivedMessages.asLocalFromAllSeq.observe {
        case (_, (neighId, export)) => map.put(neighId, export).foreach(data => data)
      }
      while(true) {
        val program = new AggregateProgram { override def main(): Any = foldhood(Set.empty[ID])(_++_)(nbr{Set(mid)}) }
        val context = new ContextImpl(id, map, Map.empty, Map.empty)
        val e = program.round(context)
        messageStream.fire(id -> e)
        println(s"My id : $id -- export : " + e.root[Any]())
        Thread.sleep(1000)
      }
    }
  }
}

object AggregateApplication extends App {
  multitier start new Instance[ScafiExecution.Node](
    listen[ScafiExecution.Node] { TCP(43053) }
  )
}

object AggregateSystem extends App {
  val port: Int = if(args.length==1) args(0).toInt else 43053
  val numNodes = 3
  //first
  multitier start new Instance[ScafiExecution.Node](
    listen[ScafiExecution.Node] {
      TCP(port)
    }
  )
  //others
  1 until numNodes foreach { i =>
    multitier start new Instance[ScafiExecution.Node](
      listen[ScafiExecution.Node] {
        TCP(port + i)
      } and connect[ScafiExecution.Node] {
        TCP("localhost", port + i - 1)
      } /*
      and connect[ScafiExecution.Node] {
        TCP("localhost", port + i + 1)
      }
      */
    )
  }
}