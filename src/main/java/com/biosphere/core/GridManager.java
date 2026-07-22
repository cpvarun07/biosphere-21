package com.biosphere.core;

import com.biosphere.entities.Organism;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe wrapper around the simulation's Organism[][] grid.
 *
 * Concurrency strategy:
 * - Every cell (x, y) has its own ReentrantLock, stored in a parallel
 *   ReentrantLock[][] array. This allows thousands of virtual threads to
 *   act on spatially distant cells fully in parallel — locking is as
 *   granular as the grid itself.
 * - All mutating operations (occupy, vacate, move) are exposed as single
 *   atomic methods. No external caller is ever allowed to read a cell's
 *   occupancy and act on it in a separate step; every check-then-act
 *   sequence happens entirely inside one lock-guarded method here.
 * - Operations touching two cells (moves, attacks) acquire both locks in
 *   a deterministic global order (row-major, via Point.compareTo) so that
 *   no two threads can ever hold opposite locks and wait on each other —
 *   this structurally eliminates circular-wait deadlocks without needing
 *   tryLock/backoff schemes.
 */
public final class GridManager {

    private final int width;
    private final int height;
    private final Organism[][] grid;
    private final ReentrantLock[][] locks;

    public GridManager(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                "Grid dimensions must be positive: " + width + "x" + height
            );
        }
        this.width = width;
        this.height = height;
        this.grid = new Organism[width][height];
        this.locks = new ReentrantLock[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                locks[x][y] = new ReentrantLock();
            }
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * Returns true if the given point lies within grid bounds.
     */
    public boolean inBounds(Point p) {
        return p.x() >= 0 && p.x() < width && p.y() >= 0 && p.y() < height;
    }

    /**
     * Locks the two given cells in a deterministic global order to prevent
     * deadlocks, then runs the supplied action, then unlocks both in
     * reverse order. If a and b are the same point, only one lock is
     * acquired (re-entrant locks make double-locking safe, but we avoid
     * it anyway for clarity and to prevent unnecessary contention).
     */
    private <T> T withOrderedLocks(Point a, Point b, java.util.function.Supplier<T> action) {
        boolean same = a.equals(b);
        Point first = same ? a : (a.compareTo(b) <= 0 ? a : b);
        Point second = same ? null : (first.equals(a) ? b : a);

        ReentrantLock firstLock = locks[first.x()][first.y()];
        firstLock.lock();
        try {
            if (same) {
                return action.get();
            }
            ReentrantLock secondLock = locks[second.x()][second.y()];
            secondLock.lock();
            try {
                return action.get();
            } finally {
                secondLock.unlock();
            }
        } finally {
            firstLock.unlock();
        }
    }

    /**
     * Atomically attempts to place an organism at the given point, but
     * only if the cell is currently empty. Returns true if placement
     * succeeded, false if the cell was already occupied.
     *
     * This is the entry point for spawning new organisms (initial
     * population seeding, reproduction offspring placement).
     */
    public boolean tryOccupy(Point at, Organism organism) {
        validate(at);
        ReentrantLock lock = locks[at.x()][at.y()];
        lock.lock();
        try {
            if (grid[at.x()][at.y()] != null) {
                return false;
            }
            grid[at.x()][at.y()] = organism;
            organism.updatePosition(at);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically clears a cell, but only if it currently holds the
     * expected organism. This "compare and clear" semantic prevents a
     * stale thread from vacating a cell that another organism has since
     * moved into. Returns true if the cell was cleared.
     */
    public boolean vacate(Point at, Organism expectedOccupant) {
        validate(at);
        ReentrantLock lock = locks[at.x()][at.y()];
        lock.lock();
        try {
            if (grid[at.x()][at.y()] == expectedOccupant) {
                grid[at.x()][at.y()] = null;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically moves an organism from one cell to another. Both cells
     * are locked in deterministic order for the duration of the entire
     * check-and-move sequence, so no other thread can interleave a
     * competing move, occupy, or vacate on either cell mid-operation.
     *
     * Fails (returns false) if:
     *  - 'from' does not currently hold 'organism' (stale caller), or
     *  - 'to' is already occupied by something else.
     *
     * On success, the organism's internal position is updated as part of
     * this same atomic operation.
     */
    public boolean tryMove(Point from, Point to, Organism organism) {
        validate(from);
        validate(to);
        return withOrderedLocks(from, to, () -> {
            if (grid[from.x()][from.y()] != organism) {
                return false;
            }
            if (grid[to.x()][to.y()] != null) {
                return false;
            }
            grid[from.x()][from.y()] = null;
            grid[to.x()][to.y()] = organism;
            organism.updatePosition(to);
            return true;
        });
    }

    /**
     * Atomically inspects the occupant of a single cell. Returns null if
     * empty. This is a snapshot read — the caller must not assume the
     * result is still accurate the instant after this method returns,
     * since another thread may act on that cell immediately afterward.
     * Safe for read-only purposes like neighbor scanning and rendering.
     */
    public Organism peek(Point at) {
        validate(at);
        ReentrantLock lock = locks[at.x()][at.y()];
        lock.lock();
        try {
            return grid[at.x()][at.y()];
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically attempts a predation/interaction between two adjacent
     * organisms: locks both cells in deterministic order, verifies both
     * occupants still match expectations, then hands control to the
     * supplied resolver to decide the outcome (e.g., carnivore kills
     * herbivore and occupies its cell). This avoids a whole class of
     * race conditions where two carnivores simultaneously target the same
     * prey, or prey moves away between the attacker's check and act.
     *
     * The resolver receives the live occupants of both cells (post-lock,
     * guaranteed consistent) and returns the outcome via InteractionResult.
     */
    public InteractionResult resolveInteraction(
            Point attackerAt,
            Point targetAt,
            Organism expectedAttacker,
            Organism expectedTarget,
            java.util.function.BiFunction<Organism, Organism, InteractionResult> resolver) {
        validate(attackerAt);
        validate(targetAt);
        return withOrderedLocks(attackerAt, targetAt, () -> {
            Organism actualAttacker = grid[attackerAt.x()][attackerAt.y()];
            Organism actualTarget = grid[targetAt.x()][targetAt.y()];

            if (actualAttacker != expectedAttacker || actualTarget != expectedTarget) {
                return InteractionResult.staleResult();
            }

            InteractionResult result = resolver.apply(actualAttacker, actualTarget);

            if (result.targetConsumed()) {
                grid[targetAt.x()][targetAt.y()] = null;
            }
            if (result.attackerMovesToTarget()) {
                grid[attackerAt.x()][attackerAt.y()] = null;
                grid[targetAt.x()][targetAt.y()] = actualAttacker;
                actualAttacker.updatePosition(targetAt);
            }

            return result;
        });
    }

    /**
     * Returns a defensive snapshot list of all 8-directional neighbor
     * points around the given point that lie within grid bounds. Does not
     * inspect occupancy — pure geometry, safe to call without locking.
     * Callers should use peek() on individual results if they need
     * occupancy state, understanding that state may change between the
     * scan and any subsequent action (hence why actions like tryMove and
     * resolveInteraction re-validate atomically rather than trusting a
     * prior scan).
     */
    public List<Point> neighborsOf(Point center) {
        validate(center);
        List<Point> neighbors = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                Point candidate = new Point(center.x() + dx, center.y() + dy);
                if (inBounds(candidate)) {
                    neighbors.add(candidate);
                }
            }
        }
        return neighbors;
    }

    private void validate(Point p) {
        if (!inBounds(p)) {
            throw new IllegalArgumentException(
                "Point out of grid bounds: " + p + " (grid is " + width + "x" + height + ")"
            );
        }
    }

    /**
     * Outcome of a resolved interaction between two organisms, returned by
     * the resolver passed to resolveInteraction(). Immutable value type.
     */
    public record InteractionResult(
            boolean targetConsumed,
            boolean attackerMovesToTarget,
            boolean stale) {

        /**
         * Factory for a "stale" result — used when resolveInteraction()
         * finds the grid no longer matches the expected occupants (e.g.
         * the prey was already killed by another thread). Named
         * staleResult() rather than stale() because a record automatically
         * generates an instance accessor method named after each component
         * — here, stale() — and a manually-declared method with that exact
         * name would collide with it (a static method can never satisfy an
         * accessor's required instance-method signature, which is exactly
         * what javac flagged).
         */
        public static InteractionResult staleResult() {
            return new InteractionResult(false, false, true);
        }

        public static InteractionResult consumeAndAdvance() {
            return new InteractionResult(true, true, false);
        }

        public static InteractionResult consumeInPlace() {
            return new InteractionResult(true, false, false);
        }

        public static InteractionResult noEffect() {
            return new InteractionResult(false, false, false);
        }
    }
}
