 # ScaFi and ScalaLoci integration

This repository contains experiments about the integration between [ScaFi](https://scafi.github.io/) and [ScalaLoci](https://scala-loci.github.io/#getting-started).

The experiments are based on the following paper: [Towards Pulverised Architectures for Collective Adaptive Systems through Multi-Tier Programming](https://ieeexplore.ieee.org/document/9599177)

## How to run the experiments

### [1 - Dynamic connections](https://github.com/vitlinda/scafi-loci-experiments/tree/main/src/main/scala/it/unibo/loci/scafi/experiments/dynamiconnections)
The goal of the experiment is to show a system that runs several examples of aggregate programs using ScaFi's constructs
on a set of nodes connected via ScalaLoci.
The system can adapt to changes (e.g. a node leaves the system and the computed values change accordingly).

When running the gradient example, you should see some nodes that are randomly selected as sources that will have a value of 0.0 (distance 0.0) and the other nodes will start with an initial value of "infinity" that will be updated to the minimum distance from the closest source.
If a node leaves or join the network, the other nodes will update their values accordingly.

#### 1.1 - Run the nodes
Run the nodes in different terminals.

The nodes are connected according to this topology:
A -> B, B -> C, C -> A, C -> D, D -> E

```bash
sbt "run it.unibo.loci.scafi.experiments.basestation.A"
sbt "run it.unibo.loci.scafi.experiments.basestation.B"
sbt "run it.unibo.loci.scafi.experiments.basestation.C"
sbt "run it.unibo.loci.scafi.experiments.basestation.D"
sbt "run it.unibo.loci.scafi.experiments.basestation.E"
``` 

### [2 - Base Station](https://github.com/vitlinda/scafi-loci-experiments/tree/main/src/main/scala/it/unibo/loci/scafi/experiments/basestation)
This experiment introduces to the network of the experiment 1 a node that doesn't run an aggregate program (a base station). This node act as a "monitor" and collects the values of every node connected to it.
Since the connected nodes are running an aggregate program, the values collected by the base station are the results of every node's round.
This example assumes that every node is connected to the base station.

#### 2.1 - Run the Base Station
```bash
sbt "run it.unibo.loci.scafi.experiments.basestation.BaseStationNode"
```
#### 2.2 - Run the nodes
Run the nodes as in 1.1
