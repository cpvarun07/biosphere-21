package com.biosphere.core;

/**
 * Immutable coordinate on the simulation grid.
 * Used as a lightweight value type for positions, deltas, and neighbor
 * calculations. Being a record, it gets free equals()/hashCode()/toString(),
 * which is essential since Points are used as conceptual keys in lock
 * ordering and neighbor-scanning logic (Record Patterns in switch blocks).
 */
public record Point(int x, int y) {

    /**
     * Compact constructor — validates that coordinates are non-negative.
     * Actual upper-bound validation (grid width/height) happens in
     * GridManager, since Point itself has no knowledge of grid dimensions.
     */
    public Point {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException(
                "Point coordinates must be non-negative: (" + x + ", " + y + ")"
            );
        }
    }

    /**
     * Returns a new Point offset by (dx, dy). Used heavily by Movable
     * implementations to compute candidate destinations before validating
     * them against the grid.
     */
    public Point translate(int dx, int dy) {
        return new Point(x + dx, y + dy);
    }

    /**
     * Chebyshev (8-directional) distance — appropriate for grid-based
     * movement where diagonal steps count the same as orthogonal steps.
     */
    public int chebyshevDistance(Point other) {
        return Math.max(Math.abs(this.x - other.x), Math.abs(this.y - other.y));
    }

    /**
     * Deterministic ordering used to prevent deadlocks when a thread must
     * acquire locks on two different cells simultaneously. By always
     * locking the "lower" point first (row-major order), we guarantee a
     * consistent global lock acquisition order across all threads,
     * eliminating circular-wait conditions.
     */
    public int compareTo(Point other) {
        if (this.y != other.y) {
            return Integer.compare(this.y, other.y);
        }
        return Integer.compare(this.x, other.x);
    }
}
