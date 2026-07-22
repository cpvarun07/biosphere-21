# BioSphere-21

A real-time, concurrent digital ecosystem simulator. Every organism (Plant,
Herbivore, Carnivore) runs on its own **virtual thread** (Java 21 / Project
Loom) rather than being updated by a central game loop — the simulation is
a swarm of thousands of independently-acting entities coordinating through
a thread-safe shared grid.

## Requirements

- JDK 21+
- Maven 3.9+
- MySQL 8.x (for the persistence layer — see below)

## Build & Run

```bash
mvn clean package
java -jar target/biosphere-21.jar
```

## Architecture

### Concurrency model
Each `Animal` instance is submitted to
`Executors.newVirtualThreadPerTaskExecutor()` and runs its own `act()` loop
independently. There is no central tick/update loop driving entities —
timing is per-organism (e.g. `Thread.sleep` between actions), which is only
feasible because virtual threads are cheap enough to run thousands
concurrently without exhausting OS threads.

### Grid synchronization (`com.biosphere.core.GridManager`)
The shared `Organism[][]` grid is wrapped by `GridManager`, which gives each
cell its own `ReentrantLock`. All mutations happen through atomic methods
(`tryOccupy`, `tryMove`, `vacate`, `resolveInteraction`) — no caller ever
reads a cell's state and acts on it as a separate step. Operations touching
two cells (moves, predation) acquire both locks in a deterministic
row-major order (`Point.compareTo`), which structurally prevents deadlocks
from circular lock-waiting.

### Entities (`com.biosphere.entities`)
`Organism` is the abstract base: atomic alive/dead state via
`AtomicBoolean`, atomic position tracking via `AtomicReference<Point>`, and
a `final void die()` method that guarantees death is a single, race-free
transition (species-specific cleanup goes through the `onDeath()` hook, not
by overriding `die()` itself). Reproduction uses `Cloneable`.

### Analytics (`com.biosphere.core.EcosystemMonitor` — not yet built)
A background thread waking every 5 seconds, using the Streams API to
compute global biomass, population counts per species, and extinction
events from the live organism list.

### UI (`com.biosphere.ui` — not yet built)
Console rendering is intentionally decoupled from simulation state: the
renderer reads a *snapshot* of the grid rather than touching live locked
cells, so heavy console I/O never blocks or slows simulation threads.

### Persistence (`com.biosphere.persistence` — not yet built)
MySQL, via `mysql-connector-j` + HikariCP connection pooling. Simulation
threads never perform JDBC calls directly — they drop `PersistenceEvent`
objects onto a bounded `BlockingQueue`, drained by a small dedicated writer
thread pool. Two kinds of data are persisted:
- **Periodic snapshots** — population/biomass summary rows, written on the
  same 5-second cadence as `EcosystemMonitor`.
- **Event log** — discrete rows for births, deaths, and extinction events.

## Project status

| Component | Status |
|---|---|
| `Point` (core) | ✅ Done |
| `Organism` (entities, abstract base) | ✅ Done |
| `GridManager` (core) | ✅ Done |
| `Movable` interface | ✅ Done |
| `Plant` | ✅ Done |
| `Animal` (abstract base) | ✅ Done |
| `Herbivore` | ✅ Done |
| `Carnivore` | ✅ Done |
| `SimulationEngine` | ✅ Done (runnable) |
| Console UI (`ConsoleRenderer`, `SimulationLauncher`) | ✅ Done (runnable) |
| `EcosystemMonitor` | ⏳ Next |
| MySQL schema + DAO layer | ⏳ Planned |

## Known gaps

- Reproduction logic (`trySpread`/`tryReproduce`) is duplicated nearly
  identically across `Plant`, `Herbivore`, and `Carnivore` — a shared
  helper would remove ~20 lines of repetition per species.
- No JUnit tests yet.

## Package layout

```
com.biosphere.core         Point, GridManager, (later) EcosystemMonitor, SimulationEngine
com.biosphere.entities     Organism, Movable, Plant, Animal, Herbivore, Carnivore
com.biosphere.ui           Console rendering, SimulationLauncher (main entry point)
com.biosphere.persistence  MySQL DAO layer, PersistenceEvent, connection pool config (planned)
```
"# biosphere-21" 
