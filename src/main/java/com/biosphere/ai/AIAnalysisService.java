package com.biosphere.ai;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Local AI-assisted static analysis. Produces findings and refactoring plans without an API key. */
public final class AIAnalysisService {
    private static final Pattern METHOD = Pattern.compile("\\b(?:public|private|protected)\\s+(?:static\\s+)?[\\w<>\\[\\], ?]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{");

    public String analyze() {
        Path root = findProjectRoot();
        List<Issue> issues = new ArrayList<>();
        int files = 0, lines = 0;

        try {
            if (root != null) {
                try (var stream = Files.walk(root.resolve("src/main/java"))) {
                    for (Path p : stream.filter(x -> x.toString().endsWith(".java")).toList()) {
                        String s = Files.readString(p);
                        files++; lines += s.lines().count();
                        detectLongMethods(p.getFileName().toString(), s, issues);
                        if (s.lines().count() > 220) issues.add(new Issue("Large class", p.getFileName().toString(), "class", "LOW", "Class is becoming large.", "Extract a focused service or component to reduce responsibilities."));
                    }
                }
                detectFoodChainDuplication(root, issues);
                detectMagicValues(root, issues);
            }
        } catch (Exception e) {
            issues.add(new Issue("Analyzer warning", "Project source", "scan", "LOW", "Some source files could not be inspected: " + e.getMessage(), "Verify the project source directory is readable."));
        }

        long high = issues.stream().filter(i -> i.severity.equals("HIGH")).count();
        long medium = issues.stream().filter(i -> i.severity.equals("MEDIUM")).count();
        long low = issues.stream().filter(i -> i.severity.equals("LOW")).count();
        StringBuilder out = new StringBuilder("{\"filesScanned\":").append(files)
            .append(",\"lines\":").append(lines)
            .append(",\"high\":").append(high)
            .append(",\"medium\":").append(medium)
            .append(",\"low\":").append(low)
            .append(",\"issues\":[");
        for (int i=0;i<issues.size();i++) { if(i>0)out.append(','); append(out,issues.get(i)); }
        return out.append("]}").toString();
    }

    private void detectLongMethods(String file, String source, List<Issue> out) {
        if (file.equals("Point.java")) return;
        var m = METHOD.matcher(source);
        while (m.find()) {
            int depth=1, pos=m.end();
            while(pos<source.length() && depth>0){ char c=source.charAt(pos++); if(c=='{')depth++; else if(c=='}')depth--; }
            if(depth==0 && source.substring(m.end(),pos).lines().count()>45)
                out.add(new Issue("Long method",file,m.group(1),"MEDIUM","Method is longer than 45 lines.","Extract movement, feeding, reproduction, or reporting into focused private methods."));
        }
    }

    private void detectFoodChainDuplication(Path root, List<Issue> out) {
        try {
            String h=Files.readString(root.resolve("src/main/java/com/biosphere/entities/Herbivore.java"));
            String c=Files.readString(root.resolve("src/main/java/com/biosphere/entities/Carnivore.java"));
            if(h.contains("resolveInteraction") && c.contains("resolveInteraction"))
                out.add(new Issue("Repeated interaction workflow","Herbivore.java / Carnivore.java","feeding methods","LOW","Both consumers repeat the same neighbor-scan and atomic interaction structure.","Extract a reusable Predator/Feeder interaction service while keeping C→H and H→P rules species-specific."));
        } catch (IOException ignored) { }
    }

    private void detectMagicValues(Path root, List<Issue> out) {
        try {
            String a=Files.readString(root.resolve("src/main/java/com/biosphere/entities/Animal.java"));
            if(a.contains("metabolism") && a.contains("2"))
                out.add(new Issue("Magic simulation constant","Animal.java","metabolism","LOW","Metabolism is embedded as a numeric rule.","Expose metabolism through a named constant or species configuration so balancing is easier."));
        } catch(IOException ignored) { }
    }

    private Path findProjectRoot() {
        Path p=Paths.get(System.getProperty("biosphere.project.root", System.getProperty("user.dir"))).toAbsolutePath();
        while(p!=null){ if(Files.isDirectory(p.resolve("src/main/java")) && Files.isRegularFile(p.resolve("pom.xml"))) return p; p=p.getParent(); }
        return null;
    }

    private static void append(StringBuilder b, Issue i){
        b.append("{\"type\":\"").append(e(i.type)).append("\",\"file\":\"").append(e(i.file))
         .append("\",\"location\":\"").append(e(i.location)).append("\",\"severity\":\"").append(e(i.severity))
         .append("\",\"explanation\":\"").append(e(i.explanation)).append("\",\"suggestion\":\"").append(e(i.suggestion)).append("\"}");
    }
    private static String e(String s){ return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n"); }
    private record Issue(String type,String file,String location,String severity,String explanation,String suggestion){}
}
