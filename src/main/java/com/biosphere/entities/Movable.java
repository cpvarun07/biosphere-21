package com.biosphere.entities;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;

/**
 * Contract for any Organism that can request a change of position on the
 * grid. Only Animal implements this — Plant is deliberately excluded so
 * the type system itself prevents a Plant from ever being handed to
 * movement logic. This is stronger than a runtime check: it's a compile
 * time guarantee.
 *
 * Implementations do NOT mutate the grid directly. They call into
 * GridManager, which owns all locking and atomicity guarantees for cell
 * occupancy. Movable is purely the "can this thing move, and how does it
 * decide where" contract — the "is the move safe under concurrency"
 * question is entirely GridManager's responsibility.
 */
public interface Movable {

    /**
     * Attempts to move this entity from its current position to the given
     * target, using the supplied GridManager as the single source of
     * truth for grid occupancy. Returns true if the move succeeded, false
     * if it was rejected (e.g., target became occupied by another thread
     * between this entity's decision and the attempt).
     *
     * Implementations should treat a false return as a normal, expected
     * outcome of concurrent execution — not an error — and fall back to
     * re-evaluating their next action rather than retrying the same move
     * in a tight loop.
     */
    boolean moveTo(Point target, GridManager gridManager);

    /**
     * Returns the maximum Chebyshev distance this entity can move in a
     * single action. Most animals will return 1 (adjacent cells only,
     * including diagonals), but this is left open for future species
     * with different mobility (e.g., a fast-moving predator).
     */
    int movementRange();
}
