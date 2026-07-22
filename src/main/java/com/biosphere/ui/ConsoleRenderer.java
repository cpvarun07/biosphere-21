package com.biosphere.ui;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;
import com.biosphere.entities.Organism;

/**
 * Renders the grid to the console. Deliberately decoupled from
 * simulation logic: every render pass only calls GridManager.peek() per
 * cell (a fast, individually-locked read) rather than holding any lock
 * across the whole render, so a slow terminal write can never block
 * simulation threads from acting.
 *
 * This is intentionally the simplest possible renderer — a full-grid
 * text dump with an ANSI clear between frames. A more advanced UI (e.g.
 * double-buffered diff rendering to reduce flicker) can replace this
 * later without touching any simulation code, which is exactly the
 * point of keeping rendering decoupled.
 */
public final class ConsoleRenderer {

    private static final String ANSI_CLEAR_SCREEN = "\033[H\033[2J";

    public void render(GridManager grid) {
        StringBuilder frame = new StringBuilder();
        frame.append(ANSI_CLEAR_SCREEN);

        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                Organism occupant = grid.peek(new Point(x, y));
                frame.append(occupant == null ? '.' : occupant.glyph());
                frame.append(' ');
            }
            frame.append('\n');
        }

        System.out.print(frame);
        System.out.flush();
    }
}
