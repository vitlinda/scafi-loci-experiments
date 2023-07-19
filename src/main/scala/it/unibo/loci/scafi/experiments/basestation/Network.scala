package it.unibo.loci.scafi.experiments.basestation

import loci.communicator.tcp.TCP
import loci.language._

@multitier object SimpleExampleP2P extends AggregateBaseStation

object BaseStationNode extends App {
  multitier start new Instance[SimpleExampleP2P.BaseStation](
    listen[SimpleExampleP2P.AggregateNode] {
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
  multitier start new Instance[SimpleExampleP2P.AggregateNode](
    listen[SimpleExampleP2P.AggregateNode] {
      TCP(43053)
    } and connect[SimpleExampleP2P.AggregateNode] {
      TCP("localhost", 43054)
    } and connect[SimpleExampleP2P.AggregateNode] {
      TCP("localhost", 43055)
    }
      and connect[SimpleExampleP2P.BaseStation] {
        TCP("localhost", 43052)
      }
  )
}

object B extends App {
  multitier start new Instance[SimpleExampleP2P.AggregateNode](
    listen[SimpleExampleP2P.AggregateNode] {
      TCP(43054)
    } and
      connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43053)
      } and connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43055)
      }
      and connect[SimpleExampleP2P.BaseStation] {
        TCP("localhost", 43052)
      }
  )
}

object C extends App {
  multitier start new Instance[SimpleExampleP2P.AggregateNode](
    connect[SimpleExampleP2P.AggregateNode] {
      TCP("localhost", 43053)
    } and
      listen[SimpleExampleP2P.AggregateNode] {
        TCP(43055)
      } and connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43054)
      } and connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43056)
      }
      and connect[SimpleExampleP2P.BaseStation] {
        TCP("localhost", 43052)
      }
  )
}

object D extends App {
  multitier start new Instance[SimpleExampleP2P.AggregateNode](
    listen[SimpleExampleP2P.AggregateNode] {
      TCP(43056)
    } and
      connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43055)
      } and connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43057)
      }
      and connect[SimpleExampleP2P.BaseStation] {
        TCP("localhost", 43052)
      }
  )
}

object E extends App {
  multitier start new Instance[SimpleExampleP2P.AggregateNode](
    listen[SimpleExampleP2P.AggregateNode] {
      TCP(43057)
    } and
      connect[SimpleExampleP2P.AggregateNode] {
        TCP("localhost", 43056)
      }
      and connect[SimpleExampleP2P.BaseStation] {
        TCP("localhost", 43052)
      }
  )
}
