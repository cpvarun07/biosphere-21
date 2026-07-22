package com.biosphere.entities;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract base class for every living entity in the BioSphere-21 simulation.
 *
 * Design notes:
 * - Every Organism runs its lifecycle on its own virtual thread, so all
 *   mutable state here must be safe under concurrent access from the
 *   organism's own thread AND from external threads (e.g., a predator
 *   thread killing this organism, or EcosystemMonitor reading its state).
 * - position is stored as an AtomicReference<Point> rather than a plain
 *   field so external readers (monitor, neighbor scans) get a consistent
 *   snapshot without needing to acquire this organism's lock.
 * - alive is an AtomicBoolean so death is a single atomic transition that
 *   can be safely observed and can only ever happen once (see die()).
 * - die() is final: subclasses must NOT be able to override or bypass the
 *   death protocol, since GridManager and EcosystemMonitor rely on this
 *   transition being uniform and race-free across every organism type.
 * - id, position, and alive are intentionally NOT declared final. This is
 *   required to support correct Cloneable-based reproduction: clone()
 *   must give an offspring a fresh identity and independent atomic state
 *   objects rather than sharing the parent's (see clone() below for why
 *   a shallow Object.clone() alone would be a serious bug here). These
 *   fields remain private and are only ever mutated inside this class's
 *   own methods, so the thread-safety story is unchanged — mutation
 *   still only happens during construction or clone(), both of which
 *   complete before the object is handed to GridManager (whose internal
 *   locking provides the happens-before guarantee for safe publication
 *   to other threads).
 */
public abstract class Organism implements Cloneable {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    private long id;
    private AtomicReference<Point> position;
    private AtomicBoolean alive;
    private volatile int energy;
    private final int maxEnergy;
    private final GridManager grid;
    private final OffspringListener offspringListener;

    protected Organism(Point startPosition, int initialEnergy, int maxEnergy, GridManager grid,
                        OffspringListener offspringListener) {
        if (startPosition == null) {
            throw new IllegalArgumentException("Organism must spawn with a valid Point");
        }
        if (initialEnergy <= 0 || maxEnergy <= 0 || initialEnergy > maxEnergy) {
            throw new IllegalArgumentException(
                "Invalid energy configuration: initial=" + initialEnergy + ", max=" + maxEnergy
            );
        }
        if (grid == null) {
            throw new IllegalArgumentException("Organism must be constructed with a valid GridManager");
        }
        if (offspringListener == null) {
            throw new IllegalArgumentException("Organism must be constructed with a valid OffspringListener");
        }
        this.id = ID_GENERATOR.incrementAndGet();
        this.position = new AtomicReference<>(startPosition);
        this.alive = new AtomicBoolean(true);
        this.energy = initialEnergy;
        this.maxEnergy = maxEnergy;
        this.grid = grid;
        this.offspringListener = offspringListener;
    }

    /**
     * Every concrete organism defines its own per-tick behavior — this is
     * what runs repeatedly on the organism's dedicated virtual thread.
     * Implementations must be responsive to interruption (Thread.sleep
     * inside the loop should propagate InterruptedException upward) so the
     * simulation can shut down virtual threads cleanly.
     */
    public abstract void act() throws InterruptedException;

    /**
     * Returns a short symbolic representation for console rendering
     * (e.g., "P" for plant, "H" for herbivore, "C" for carnivore).
     * Kept abstract so the UI layer never needs instanceof checks —
     * it just calls glyph() polymorphically.
     */
    public abstract char glyph();

    /**
     * Atomically terminates this organism. Guarded by AtomicBoolean.
     * compareAndSet so that even if two threads simultaneously try to kill
     * the same organism (e.g., two carnivores attacking the same prey in
     * the same instant), only one will succeed in performing the actual
     * death transition — the other gets false and treats it as a no-op.
     *
     * final: no subclass may override this. Any species-specific cleanup
     * should be done by overriding onDeath(), which this method invokes
     * exactly once, only on the thread that wins the CAS race.
     */
    public final void die() {
        if (alive.compareAndSet(true, false)) {
            onDeath();
        }
    }

    /**
     * Hook for subclasses to release species-specific resources or trigger
     * side effects on death (e.g., a Plant dropping seeds, a Carnivore
     * logging a kill). Default is a no-op. Called at most once, guaranteed
     * by die()'s CAS guard.
     */
    protected void onDeath() {
        // Default: no additional cleanup required.
    }

    public final boolean isAlive() {
        return alive.get();
    }

    public final Point getPosition() {
        return position.get();
    }

    /**
     * Atomically updates this organism's recorded position. Note: this
     * does NOT move the organism on the grid — GridManager is the
     * authority for spatial occupancy. This method only updates the
     * organism's own internal bookkeeping after GridManager has already
     * confirmed the move succeeded.
     */
    public final void updatePosition(Point newPosition) {
        if (newPosition == null) {
            throw new IllegalArgumentException("New position cannot be null");
        }
        position.set(newPosition);
    }

    public final int getEnergy() {
        return energy;
    }

    /**
     * Adjusts energy within [0, maxEnergy]. Marked synchronized because
     * energy changes (feeding, movement cost, reproduction cost) involve
     * a read-modify-write sequence that must be atomic — volatile alone
     * is insufficient here since we're doing arithmetic, not a simple
     * assignment.
     */
    protected final synchronized void adjustEnergy(int delta) {
        int updated = this.energy + delta;
        this.energy = Math.max(0, Math.min(maxEnergy, updated));
        if (this.energy == 0) {
            die();
        }
    }

    /**
     * Directly sets energy to an absolute value (clamped to [0, maxEnergy]),
     * rather than applying a relative delta like adjustEnergy(). Intended
     * for reproduction logic to set an offspring's starting energy right
     * after clone(), before the offspring is published to GridManager —
     * at that point it is only visible to the reproducing thread, so a
     * plain synchronized write (matching adjustEnergy's own locking) is
     * sufficient.
     */
    protected final synchronized void setEnergyDirect(int newEnergy) {
        this.energy = Math.max(0, Math.min(maxEnergy, newEnergy));
    }

    public final long getId() {
        return id;
    }

    /**
     * Returns the GridManager this organism belongs to, so act()
     * implementations in subclasses can query/mutate the grid (movement,
     * reproduction, predation) without needing it passed in separately.
     */
    protected final GridManager getGrid() {
        return grid;
    }

    /**
     * Returns the listener that must be notified whenever this organism
     * successfully produces an offspring, so reproduction logic in
     * subclasses can hand the new organism off to SimulationEngine for
     * thread launch and tracking rather than leaving it inert on the
     * grid. Automatically carried over to clones via the shallow copy in
     * clone() below — offspring report to the same engine as their
     * parent, which is correct since there is only ever one engine per
     * running simulation.
     */
    protected final OffspringListener getOffspringListener() {
        return offspringListener;
    }

    /**
     * Reproduction leverages native Cloneable per the spec. super.clone()
     * performs a SHALLOW copy — without the steps below, the offspring
     * would share this organism's exact AtomicReference<Point> and
     * AtomicBoolean instances, meaning moving or killing one would
     * silently affect the other. This override prevents that by giving
     * the copy a fresh id and brand-new atomic state objects immediately
     * after the shallow copy, before returning it to the caller.
     *
     * The offspring's position is initialized to the parent's current
     * position as a placeholder; reproduction logic in subclasses (e.g.
     * Plant.tryReproduce) is responsible for placing it at its real
     * starting cell via GridManager.tryOccupy(), which will call
     * updatePosition() on success.
     *
     * The offspring's energy is shallow-copied from the parent's current
     * value as a starting default; subclasses should generally call
     * setEnergyDirect() right after cloning to set an intentional
     * starting energy rather than relying on this default.
     */
    @Override
    public Organism clone() {
        try {
            Organism copy = (Organism) super.clone();
            copy.id = ID_GENERATOR.incrementAndGet();
            copy.position = new AtomicReference<>(this.getPosition());
            copy.alive = new AtomicBoolean(true);
            return copy;
        } catch (CloneNotSupportedException e) {
            // Cannot happen: this class implements Cloneable.
            throw new AssertionError("Organism clone failed unexpectedly", e);
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Organism other)) return false;
        return this.id == other.id;
    }

    @Override
    public final int hashCode() {
        return Long.hashCode(id);
    }
}
