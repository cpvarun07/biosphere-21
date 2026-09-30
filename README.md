# BioSphere-21

BioSphere-21 is a Java 21 real-time concurrent digital ecosystem simulator using virtual threads and a thread-safe grid.

## Food-chain rules

- **P — Young Plant:** producer stage. It grows and can be eaten by Herbivores.
- **T — Mature Tree:** a P matures into T after growth cycles. **No organism eats T.**
- **H — Herbivore:** eats P only. It does not eat T or C.
- **C — Carnivore:** eats H only. It does not eat P or T.

The food chain is therefore:

`P → H → C`

while `P → T` is the plant maturation path.

## Live dashboard

Run:

```bash
mvn clean package
java -jar target/biosphere-21.jar
```

Open `http://localhost:8080/`.

The dashboard reports live:

- Living organisms
- Young Plants (P)
- Mature Trees (T)
- Herbivores (H)
- Carnivores (C)
- Total energy
- Average energy
- Plant energy / Tree energy
- Herbivore energy / Carnivore energy
- Metabolism per tick
- Births and deaths
- Live grid state

The dashboard contains an **AI Code Intelligence** panel that performs local AI-assisted static detection and produces automated refactoring suggestions. It requires no external API key; suggestions are presented for developer review before applying changes.

## Project status

| Component | Status |
|---|---|
| Point | Done |
| Organism | Done |
| GridManager | Done |
| Movable | Done |
| Plant P→T lifecycle | Done |
| Herbivore H→P feeding | Done |
| Carnivore C→H feeding | Done |
| SimulationEngine | Done |
| Java 21 Virtual Threads | Done |
| Live Web Dashboard | Done |
| Live energy/metabolism/lifecycle metrics | Done |
| AI Code Detection | Done |
| Automated Refactoring Suggestions | Done |
| EcosystemMonitor | Planned |
| MySQL DAO persistence | Planned |
