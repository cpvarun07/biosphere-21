package com.biosphere.ui;

import com.biosphere.ai.AIAnalysisService;
import com.biosphere.core.GridManager;
import com.biosphere.core.Point;
import com.biosphere.core.SimulationEngine;
import com.biosphere.entities.Organism;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class WebSimulationServer {
    private final HttpServer server;
    private final SimulationEngine engine;
    private final GridManager grid;
    private final AIAnalysisService ai = new AIAnalysisService();

    public WebSimulationServer(int port, SimulationEngine engine) throws IOException {
        this.engine=engine; this.grid=engine.getGrid();
        server=HttpServer.create(new InetSocketAddress(port),0);
        server.createContext("/", this::dashboard);
        server.createContext("/api/grid", this::gridApi);
        server.createContext("/api/ai-analysis", this::aiApi);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }
    public void start(){ server.start(); }

    private void dashboard(HttpExchange x) throws IOException {
        byte[] b=html().getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type","text/html; charset=UTF-8");
        x.sendResponseHeaders(200,b.length); try(OutputStream o=x.getResponseBody()){o.write(b);}
    }
    private void gridApi(HttpExchange x) throws IOException {
        var s=engine.getPopulationStats();
        StringBuilder j=new StringBuilder("{");
        j.append("\"width\":").append(grid.getWidth()).append(",\"height\":").append(grid.getHeight())
         .append(",\"total\":").append(s.total()).append(",\"plants\":").append(s.plants())
         .append(",\"trees\":").append(s.trees()).append(",\"herbivores\":").append(s.herbivores())
         .append(",\"carnivores\":").append(s.carnivores()).append(",\"energy\":").append(s.totalEnergy())
         .append(",\"plantEnergy\":").append(s.plantEnergy())
         .append(",\"treeEnergy\":").append(s.treeEnergy())
         .append(",\"herbivoreEnergy\":").append(s.herbivoreEnergy())
         .append(",\"carnivoreEnergy\":").append(s.carnivoreEnergy())
         .append(",\"avgEnergy\":").append(String.format(java.util.Locale.ROOT,"%.1f",s.averageEnergy()))
         .append(",\"metabolism\":").append(s.metabolismPerTick()).append(",\"births\":").append(s.births())
         .append(",\"deaths\":").append(s.deaths()).append(",\"matrix\":[");
        for(int y=0;y<grid.getHeight();y++){ if(y>0)j.append(','); j.append('[');
            for(int xx=0;xx<grid.getWidth();xx++){ if(xx>0)j.append(','); Organism o=grid.peek(new Point(xx,y));
                j.append('"').append(o==null||!o.isAlive()?' ':o.glyph()).append('"'); }
            j.append(']'); }
        j.append("]}"); sendJson(x,j.toString());
    }
    private void aiApi(HttpExchange x)throws IOException{ sendJson(x,ai.analyze()); }
    private static void sendJson(HttpExchange x,String json)throws IOException{ byte[]b=json.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");x.sendResponseHeaders(200,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}}

    private String html(){ return """
<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
<title>BioSphere-21 | Live Ecosystem</title>
<style>
:root{--bg:#080b10;--panel:#111720;--line:#27313d;--text:#edf2f7;--muted:#8d99a8;--p:#22c55e;--t:#166534;--h:#4ade80;--c:#fb4b5d;--a:#66a9ff;--y:#f3c65b;--v:#b49cff}
*{box-sizing:border-box}body{margin:0;background:radial-gradient(circle at 50% -10%,#172536,transparent 38%),var(--bg);color:var(--text);font-family:Inter,system-ui,sans-serif}.shell{width:min(1450px,96vw);margin:auto;padding:28px 0 50px}header{display:flex;justify-content:space-between;align-items:end;margin-bottom:20px}.eyebrow{color:#35df8b;font-size:12px;font-weight:900;letter-spacing:.16em;text-transform:uppercase}h1{font-size:clamp(30px,4vw,52px);margin:7px 0 0;letter-spacing:-.04em}.live{color:#35df8b;font-weight:800}.dot{display:inline-block;width:9px;height:9px;background:currentColor;border-radius:50%;box-shadow:0 0 15px currentColor;margin-right:7px}
.stats{display:grid;grid-template-columns:1.25fr repeat(5,1fr);gap:10px;margin-bottom:16px}.stat,.card{background:rgba(17,23,32,.95);border:1px solid var(--line);border-radius:16px;box-shadow:0 18px 55px #0004}.stat{padding:15px}.label{font-size:10px;color:var(--muted);text-transform:uppercase;letter-spacing:.1em;font-weight:800}.value{font-size:27px;font-weight:900;margin-top:4px}.total .value{color:var(--a)}.p .value{color:var(--p)}.t .value{color:#58c77c}.h .value{color:var(--h)}.c .value{color:var(--c)}
.gridLayout{display:grid;grid-template-columns:minmax(0,1fr) 390px;gap:16px}.head{display:flex;justify-content:space-between;align-items:center;padding:15px 17px;border-bottom:1px solid var(--line)}.title{font-weight:900}.sub{font-size:11px;color:var(--muted);margin-top:3px}.gridWrap{padding:17px;overflow:auto}.grid{display:grid;gap:1px;width:max-content;margin:auto;background:#2c3540;border:1px solid #313b47}.cell{width:17px;height:17px;display:flex;align-items:center;justify-content:center;font-size:9px;font-weight:900}.empty{background:#12171d}.plant{background:#1f9d55}.tree{background:#0e5f34}.herb{background:#16853d}.carn{background:#bd3041}
.legend{display:flex;gap:13px;flex-wrap:wrap;padding:0 17px 15px;color:var(--muted);font-size:11px}.legend b{padding:2px 7px;border-radius:5px;color:white}.food{padding:15px 17px;border-top:1px solid var(--line);display:flex;justify-content:center;gap:9px;align-items:center;font-weight:900}.arrow{color:#64748b}.P{background:#1f9d55}.T{background:#0e5f34}.H{background:#16853d}.C{background:#bd3041}
.metrics{display:grid;grid-template-columns:1fr 1fr;gap:9px;padding:15px}.metric{background:#0b1016;border:1px solid var(--line);border-radius:10px;padding:11px}.metric span{display:block;color:var(--muted);font-size:10px;text-transform:uppercase}.metric strong{font-size:21px}.aiBody{padding:14px;max-height:650px;overflow:auto}.aiStats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-bottom:10px}.mini{background:#0b1016;border:1px solid var(--line);border-radius:10px;padding:10px;text-align:center}.mini strong{font-size:21px;display:block}.mini span{font-size:9px;color:var(--muted);text-transform:uppercase}.issue{background:#0c1118;border:1px solid var(--line);border-radius:11px;padding:11px;margin-bottom:9px}.top{display:flex;gap:7px;align-items:center}.badge{font-size:9px;font-weight:900;padding:3px 7px;border-radius:99px}.HIGH{background:#501922;color:#ff8390}.MEDIUM{background:#4a3a17;color:#f6d06b}.LOW{background:#18324b;color:#7bbcff}.issueTitle{font-size:12px;font-weight:900}.file{font-size:10px;color:var(--a);margin-top:5px}.issue p{font-size:11px;color:#b8c2cf;line-height:1.45}.suggest{border-left:2px solid var(--v);padding-left:8px;color:#d8d0ff;font-size:11px;line-height:1.45}.btn{background:#132a43;border:1px solid #315d89;color:#9bceff;border-radius:9px;padding:8px 11px;font-weight:800;cursor:pointer}.foot{font-size:10px;color:var(--muted);padding:0 14px 14px;line-height:1.45}@media(max-width:1050px){.stats{grid-template-columns:repeat(3,1fr)}.gridLayout{grid-template-columns:1fr}}@media(max-width:520px){.stats{grid-template-columns:repeat(2,1fr)}:root{--cell:14px}}
</style></head><body><div class='shell'>
<header><div><div class='eyebrow'>Java 21 · Virtual Threads · Food Chain · AI Analysis</div><h1>BioSphere-21 Live Ecosystem</h1></div><div class='live'><span class='dot'></span>LIVE</div></header>
<section class='stats'><div class='stat total'><div class='label'>Living organisms</div><div class='value' id='total'>0</div></div><div class='stat p'><div class='label'>Young Plants P</div><div class='value' id='plants'>0</div></div><div class='stat t'><div class='label'>Mature Trees T</div><div class='value' id='trees'>0</div></div><div class='stat h'><div class='label'>Herbivores H</div><div class='value' id='herb'>0</div></div><div class='stat c'><div class='label'>Carnivores C</div><div class='value' id='carn'>0</div></div><div class='stat'><div class='label'>Total Energy</div><div class='value' id='energy'>0</div></div></section>
<div class='gridLayout'><section class='card'><div class='head'><div><div class='title'>Concurrent Ecosystem Grid</div><div class='sub'>C eats H · H eats P · T cannot be eaten · P matures into T</div></div><div class='sub' id='clock'>--:--:--</div></div><div class='gridWrap'><div class='grid' id='grid'></div></div><div class='legend'><span><b class='P'>P</b> young plant</span><span><b class='T'>T</b> mature tree</span><span><b class='H'>H</b> herbivore</span><span><b class='C'>C</b> carnivore</span></div><div class='food'><span class='P'>P</span><span>→</span><span class='H'>H eats P</span><span class='arrow'>→</span><span class='C'>C eats H</span><span class='arrow'>|</span><span class='T'>T protected</span></div></section>
<aside class='card'><div class='head'><div><div class='title'>⚙ Ecosystem Metrics</div><div class='sub'>Live energy, metabolism and lifecycle data</div></div></div><div class='metrics'><div class='metric'><span>Average energy</span><strong id='avg'>0</strong></div><div class='metric'><span>Metabolism / tick</span><strong id='met'>0</strong></div><div class='metric'><span>Plant / Tree energy</span><strong id='ptenergy'>0 / 0</strong></div><div class='metric'><span>H / C energy</span><strong id='hcenergy'>0 / 0</strong></div><div class='metric'><span>Births</span><strong id='births'>0</strong></div><div class='metric'><span>Deaths</span><strong id='deaths'>0</strong></div></div>
<div class='head'><div><div class='title'>🧠 AI Code Intelligence</div><div class='sub'>Detection + automated refactoring suggestions</div></div><button class='btn' onclick='loadAI()'>Rescan</button></div><div class='aiBody'><div class='aiStats'><div class='mini'><strong id='high'>-</strong><span>High</span></div><div class='mini'><strong id='medium'>-</strong><span>Medium</span></div><div class='mini'><strong id='low'>-</strong><span>Low</span></div></div><div id='issues'>Scanning source…</div></div><div class='foot'>Local AI-assisted static analysis. Suggestions are reviewed by the developer before code changes are applied.</div></aside></div></div>
<script>
const $=id=>document.getElementById(id);const grid=$('grid');
async function update(){try{const d=await (await fetch('/api/grid',{cache:'no-store'})).json();$('total').textContent=d.total;$('plants').textContent=d.plants;$('trees').textContent=d.trees;$('herb').textContent=d.herbivores;$('carn').textContent=d.carnivores;$('energy').textContent=d.energy.toLocaleString();$('avg').textContent=d.avgEnergy;$('met').textContent=d.metabolism;$('ptenergy').textContent=d.plantEnergy+' / '+d.treeEnergy;$('hcenergy').textContent=d.herbivoreEnergy+' / '+d.carnivoreEnergy;$('births').textContent=d.births;$('deaths').textContent=d.deaths;$('clock').textContent=new Date().toLocaleTimeString();grid.style.gridTemplateColumns=`repeat(${d.width},17px)`;grid.innerHTML='';d.matrix.flat().forEach(x=>{const e=document.createElement('div');e.className='cell '+(x==='P'?'plant':x==='T'?'tree':x==='H'?'herb':x==='C'?'carn':'empty');e.textContent=x===' '?'' : x;grid.appendChild(e)})}catch(e){console.error(e)}}
function esc(s){return String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;')}
async function loadAI(){const box=$('issues');box.innerHTML='<div class="issue">Running AI-assisted source analysis…</div>';try{const d=await (await fetch('/api/ai-analysis',{cache:'no-store'})).json();$('high').textContent=d.high;$('medium').textContent=d.medium;$('low').textContent=d.low;if(!d.issues.length){box.innerHTML='<div class="issue"><div class="issueTitle">✅ No configured issues detected</div></div>';return}box.innerHTML=d.issues.map(i=>`<div class="issue"><div class="top"><span class="badge ${i.severity}">${i.severity}</span><span class="issueTitle">${esc(i.type)}</span></div><div class="file">${esc(i.file)} · ${esc(i.location)}</div><p>${esc(i.explanation)}</p><div class="suggest"><b>Refactoring suggestion:</b> ${esc(i.suggestion)}</div></div>`).join('')}catch(e){box.innerHTML='<div class="issue"><div class="issueTitle">⚠️ Analyzer error</div><p>'+esc(e.message)+'</p></div>'}}
setInterval(update,500);update();loadAI();
</script></body></html>"""; }
}
