package it.unibo.loci.examples

import it.unibo.scafi.incarnations.AbstractTestIncarnation
import loci._
import loci.communicator.tcp._
import loci.serializer.upickle._
import loci.transmitter.Serializable
import upickle.default._
import rescala.default._

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala
import scala.util.Try

object LociIncarnation extends AbstractTestIncarnation
import LociIncarnation._

object ExportLocalUtils {
  implicit def exportSerialization: Serializable[EXPORT] = new Serializable[EXPORT] {
    private val cache = new ConcurrentHashMap[Int, EXPORT]().asScala
    def serialize(value: EXPORT) : MessageBuffer = {
      val key = value.hashCode()
      cache.put(key, value)
      MessageBuffer encodeString write(value.hashCode().toString)
    }
    def deserialize(value: MessageBuffer) : Try[EXPORT] = Try { cache(value.hashCode()) }
  }
}
@multitier object ScafiExecution {
  import ExportLocalUtils._
  type Id = ID
  type Actuation = String
  type Sensing = Int
  @peer type Node <: { type Tie <: Multiple[Node] }
  val id: Id on Node = on[Node] { UUID.randomUUID.hashCode() }

  //val message : Evt[String] on Node = on[Node] { Evt[String] }

  def main() = {
    on[Node] {
      val program = new AggregateProgram { override def main(): Any = foldhood(0)(_+_)(1) }
      val context = new ContextImpl(id, Iterable.empty, Map.empty, Map.empty)
      val export = program.round(context)
      println(`export`)
     // println(local)
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
  val numNodes = 10
  //first
  multitier start new Instance[ScafiExecution.Node](
    listen[ScafiExecution.Node] {
      TCP(port)
    }
  )
  //others
  1 to numNodes foreach { i =>
    multitier start new Instance[ScafiExecution.Node](
      listen[ScafiExecution.Node] {
        TCP(port + i)
      } and connect[ScafiExecution.Node] {
        TCP("localhost", port + i - 1)
      }
    )
  }
}