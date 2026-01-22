# redis-from-scratch-java

A small Redis-inspired server implemented in pure Java.

The goal of this project is to learn and experiment with:

- TCP networking
- Event-driven I/O using Java NIO (`Selector`, `ServerSocketChannel`, `SocketChannel`)
- A Redis-like protocol (RESP) and command set
- Clean separation into `server`, `protocol`, `command`, and `store` packages

---

## Current Features

- Non-blocking event loop server (single-threaded, multiple clients)
- RESP parsing (arrays of bulk strings) and encoding (simple string, bulk string, null bulk, arrays)
- Commands:
  - `PING`
  - `ECHO <msg>`
  - `SET key value`
  - `SET key value PX <ms>` (millisecond expiry, passive)
  - `GET key` (null when missing or expired)
  - List commands:
    - `RPUSH key value [value ...]`
    - `LPUSH key value [value ...]`
    - `LRANGE key start stop` (supports negative indexes)
    - `LLEN key`
    - `LPOP key` and `LPOP key count`
    - `BLPOP key timeout` (blocking pop with FIFO waiters and timeouts)
- In-memory key–value store with optional expiry
- In-memory list store with basic list semantics

---

## Planned Features (later)

- Sorted sets, streams
- Pub/Sub
- Transactions
- Replication
- RDB-style persistence
- Authentication

---

## How to Run

```bash
mvn package
java -jar target/redis-from-scratch-java.jar
```

# redis-from-scratch-java

A small Redis-inspired server implemented in pure Java.

The goal of this project is to learn and experiment with:

- TCP networking
- Event-driven I/O using Java NIO (`Selector`, `ServerSocketChannel`, `SocketChannel`)
- A Redis-like protocol (RESP) and command set
- Clean separation into `server`, `protocol`, `command`, and `store` packages

---

## Current Features

- Non-blocking event loop server (single-threaded, multiple clients)
- RESP parsing (arrays of bulk strings) and encoding (simple string, bulk string, null bulk)
- Commands:
  - `PING`
  - `ECHO <msg>`
  - `SET key value`
  - `SET key value PX <ms>` (millisecond expiry, passive)
  - `GET key` (null when missing or expired)
- In-memory key–value store with optional expiry

---

## Planned Features (later)

- Lists, sorted sets, streams
- Pub/Sub
- Transactions
- Replication
- RDB-style persistence
- Authentication

---

## How to Run

```bash
mvn package
java -jar target/redis-from-scratch-java.jar
```

Then in another terminal:

```
redis-cli PING
redis-cli ECHO "hello"
redis-cli SET foo bar
redis-cli GET foo
redis-cli SET temp value PX 100
sleep 0.2 && redis-cli GET temp
```

You can also try some list commands:

```
redis-cli RPUSH list_key a b c d e
redis-cli LRANGE list_key 0 -1
redis-cli LPOP list_key
redis-cli BLPOP blocking_list 5
```

## How to Run Tests

```
mvn test
```
