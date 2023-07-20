package it.unibo.loci.scafi.experiments.basestation

import loci.communicator.tcp.TCP
import loci.language._

@multitier object ABSExample extends AggregateBaseStation

object BaseStationNode extends App {
  multitier start new Instance[ABSExample.BaseStation](
    listen[ABSExample.AggregateNode] {
      TCP(43052)
    }
  )
}

// A -> B
// B -> C
// C -> A
// C -> D
// D -> E
object A extends App {
  multitier start new Instance[ABSExample.AggregateNode](
    listen[ABSExample.AggregateNode] {
      TCP(43053)
    } and connect[ABSExample.AggregateNode] {
      TCP("localhost", 43054)
    } and connect[ABSExample.AggregateNode] {
      TCP("localhost", 43055)
    }
      and connect[ABSExample.BaseStation] {
        TCP("localhost", 43052)
      }
  )
}

object B extends App {
  multitier start new Instance[ABSExample.AggregateNode](
    listen[ABSExample.AggregateNode] {
      TCP(43054)
    } and
      connect[ABSExample.AggregateNode] {
        TCP("localhost", 43053)
      } and connect[ABSExample.AggregateNode] {
        TCP("localhost", 43055)
      }
      and connect[ABSExample.BaseStation] {
        TCP("localhost", 43052)
      }
  )
}

object C extends App {
  multitier start new Instance[ABSExample.AggregateNode](
    connect[ABSExample.AggregateNode] {
      TCP("localhost", 43053)
    } and
      listen[ABSExample.AggregateNode] {
        TCP(43055)
      } and connect[ABSExample.AggregateNode] {
        TCP("localhost", 43054)
      } and connect[ABSExample.AggregateNode] {
        TCP("localhost", 43056)
      }
//      and connect[ABSExample.BaseStation] {
//        TCP("localhost", 43052)
//      }
  )
}

object D extends App {
  multitier start new Instance[ABSExample.AggregateNode](
    listen[ABSExample.AggregateNode] {
      TCP(43056)
    } and
      connect[ABSExample.AggregateNode] {
        TCP("localhost", 43055)
      } and connect[ABSExample.AggregateNode] {
        TCP("localhost", 43057)
      }
      and connect[ABSExample.BaseStation] {
        TCP("localhost", 43052)
      }
  )
}

object E extends App {
  multitier start new Instance[ABSExample.AggregateNode](
    listen[ABSExample.AggregateNode] {
      TCP(43057)
    } and
      connect[ABSExample.AggregateNode] {
        TCP("localhost", 43056)
      }
      and connect[ABSExample.BaseStation] {
        TCP("localhost", 43052)
      }
  )
}
