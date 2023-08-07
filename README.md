# ScaFi and ScalaLoci integration

#TODO: update the README

[//]: # ()
[//]: # (This repository contains experiments about the integration between [ScaFi]&#40;https://scafi.github.io/&#41; and [ScalaLoci]&#40;https://scala-loci.github.io/#getting-started&#41;.)

[//]: # ()
[//]: # (The experiments are based on the following paper: [Towards Pulverised Architectures for Collective Adaptive Systems through Multi-Tier Programming]&#40;https://ieeexplore.ieee.org/document/9599177&#41;)

[//]: # ()
[//]: # (## How to run the experiments)

[//]: # ()
[//]: # (The experiments can  be found in the package `it.unibo.loci.scafi.experiments`)
[//]: # ()
[//]: # (### [1 - Dynamic connections]&#40;https://github.com/vitlinda/scafi-loci-experiments/tree/main/src/main/scala/it/unibo/loci/scafi/experiments/dynamiconnections&#41;)

[//]: # (The goal of the experiment is to show a system that runs several examples of aggregate programs using ScaFi's constructs)

[//]: # (on a set of nodes connected via ScalaLoci.)

[//]: # (The system can adapt to changes &#40;e.g. a node leaves the system and the computed values change accordingly&#41;.)

[//]: # ()
[//]: # (When running the gradient example, you should see some nodes that are randomly selected as sources that will have a value of 0.0 &#40;distance 0.0&#41; and the other nodes will start with an initial value of "infinity" that will be updated to the minimum distance from the closest source.)

[//]: # (If a node leaves or join the network, the other nodes will update their values accordingly.)

[//]: # ()
[//]: # (#### 1.1 - Run the nodes)

[//]: # (Run the nodes in different terminals.)

[//]: # ()
[//]: # (The nodes are connected according to this topology:)

[//]: # (A -> B, B -> C, C -> A, C -> D, D -> E)

[//]: # ()
[//]: # (```bash)

[//]: # (sbt "run it.unibo.loci.scafi.experiments.dynamiconnections.A")

[//]: # (sbt "run it.unibo.loci.scafi.experiments.dynamiconnections.B")

[//]: # (sbt "run it.unibo.loci.scafi.experiments.dynamiconnections.C")

[//]: # (sbt "run it.unibo.loci.scafi.experiments.dynamiconnections.D")

[//]: # (sbt "run it.unibo.loci.scafi.experiments.dynamiconnections.E")

[//]: # (``` )

[//]: # ()
[//]: # (### [2 - Base Station]&#40;https://github.com/vitlinda/scafi-loci-experiments/tree/main/src/main/scala/it/unibo/loci/scafi/experiments/basestation&#41;)

[//]: # (This experiment introduces to the network of [1 - Dynamic connections]&#40;#1---dynamic-connections&#41; a node that doesn't run the aggregate program &#40;e.g. a base station&#41;.)

[//]: # (This node act as a "monitor" and collects the values of every node connected to it.)

[//]: # (The values collected by the base station are: the output of each node's rounds and their export to obtain the values of neighbours that may not be directly connected to the base station.)

[//]: # ()
[//]: # (#### 2.1 - Run the Base Station)

[//]: # (```bash)

[//]: # (sbt "run it.unibo.loci.scafi.experiments.basestation.BaseStationNode")

[//]: # (```)

[//]: # (#### 2.2 - Run the nodes)

[//]: # (Run the nodes in different terminals.)

[//]: # ()
[//]: # (```bash)

[//]: # (sbt "run it.unibo.loci.scafi.experiments.basestation.A")

[//]: # (sbt "run it.unibo.loci.scafi.experiments.basestation.B")

[//]: # (sbt "run it.unibo.loci.scafi.experiments.basestation.C")

[//]: # (sbt "run it.unibo.loci.scafi.experiments.basestation.D")

[//]: # (sbt "run it.unibo.loci.scafi.experiments.basestation.E")

[//]: # (``` )