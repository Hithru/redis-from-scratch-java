# redis-from-scratch-java

A small Redis-inspired server implemented in pure Java.

The goal of this project is to learn and experiment with:

- TCP networking
- Event-driven I/O using Java NIO (`Selector`, `ServerSocketChannel`, `SocketChannel`)
- Designing and implementing a simple Redis-like protocol and feature set

At the moment, the server handles basic health-check style commands and is structured to be extended with more Redis-like commands over time.
