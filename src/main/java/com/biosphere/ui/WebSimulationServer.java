package com.biosphere.ui;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;
import com.biosphere.entities.Organism;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class WebSimulationServer {

    private final HttpServer server;
    private final GridManager gridManager;

    public WebSimulationServer(int port, GridManager gridManager) throws IOException {
        this.gridManager = gridManager;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/", new DashboardHandler());
        server.createContext("/api/grid", new GridDataHandler());
        
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    }

    public void start() {
        server.start();
        System.out.println(" -> Web UI Dashboard hosted active at: http://localhost:8080/");
    }

    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>BioSphere-21 Dashboard</title>
                <style>
                    body { background: #121212; color: #e0e0e0; font-family: sans-serif; text-align: center; }
                    #grid { display: grid; gap: 1px; margin: 20px auto; background: #333; width: fit-content; }
                    .cell { width: 15px; height: 15px; display: flex; align-items: center; justify-content: center; font-size: 10px; font-weight: bold; }
                    .empty { background: #1a1a1a; }
                    .herbivore { background: #2e7d32; color: #fff; }
                    .carnivore { background: #c62828; color: #fff; }
                </style>
            </head>
            <body>
                <h1>BioSphere-21 Live Concurrency Monitor</h1>
                <h3 id="counter">Living Organisms Tracked: 0</h3>
                <div id="grid"></div>
                <script>
                    async function updateGrid() {
                        const res = await fetch('/api/grid');
                        const data = await res.json();
                        document.getElementById('counter').innerText = 'Living Organisms Tracked: ' + data.tracked;
                        
                        const gridDiv = document.getElementById('grid');
                        gridDiv.style.gridTemplateColumns = `repeat(${data.width}, 15px)`;
                        gridDiv.innerHTML = '';
                        
                        data.matrix.forEach(row => {
                            row.forEach(cell => {
                                const div = document.createElement('div');
                                div.className = 'cell ' + (cell === 'H' ? 'herbivore' : cell === 'C' ? 'carnivore' : 'empty');
                                div.innerText = cell === '*' ? '' : cell;
                                gridDiv.appendChild(div);
                            });
                        });
                    }
                    setInterval(updateGrid, 250);
                    updateGrid();
                </script>
            </body>
            </html>
            """;
            byte[] response = html.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    private class GridDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int width = gridManager.getWidth();
            int height = gridManager.getHeight();
            int trackedCount = 0;
            
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"width\":").append(width).append(",");
            json.append("\"height\":").append(height).append(",");
            json.append("\"matrix\":[");
            
            for (int y = 0; y < height; y++) {
                json.append("[");
                for (int x = 0; x < width; x++) {
                    Organism organism = gridManager.peek(new Point(x, y)); // Non-blocking read
                    if (organism == null) {
                        json.append("\"*\"");
                    } else {
                        char g = organism.glyph(); // Polymorphic glyph fetch
                        json.append("\"").append(g).append("\"");
                        if (organism.isAlive()) trackedCount++; // Clean encapsulation check
                    }
                    if (x < width - 1) json.append(",");
                }
                json.append("]");
                if (y < height - 1) json.append(",");
            }
            json.append("],");
            json.append("\"tracked\":").append(trackedCount);
            json.append("}");
            
            byte[] response = json.toString().getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}