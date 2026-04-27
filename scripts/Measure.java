///usr/bin/env jbang
//DEPS info.picocli:picocli:4.7.7

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

@Command(name = "measure", mixinStandardHelpOptions = true,
        description = "Measures startup time and TTFR for Java AI projects")
public class Measure implements Callable<Integer> {

    @Option(names = {"--skip-ttfr"}, description = "Skip TTFR measurements")
    boolean skipTtfr;

    @Option(names = {"--only"}, description = "Only measure this project (lc4j-pure, spring-ai, quarkus-easyrag, quarkus-easyrag-warm, quarkus-no-easyrag)")
    String only;

    static final String JAVA_HOME = "/home/omatheusmesmo/.sdkman/candidates/java/25.0.3-tem";
    static final String BASE_DIR = "/mnt/fileshare/projects/ai/java-ai-comparison";

    static record ProjectConfig(
            String name, String dir, String[] cmd, int port,
            String healthPath, String selfReportedRegex, String selfReportedUnit,
            String chatEndpoint, String ragEndpoint, String toolsEndpoint,
            String[] preStartActions, String startupMarker
    ) {}

    static final Map<String, ProjectConfig> PROJECTS;

    static {
        var m = new LinkedHashMap<String, ProjectConfig>();
        m.put("lc4j-pure", new ProjectConfig(
                "lc4j-pure", BASE_DIR + "/langchain4j-pure",
                new String[]{"java", "-jar", "target/langchain4j-pure-demo-1.0.0-SNAPSHOT.jar"},
                8081, "/ai/chat?user=measure&q=hello",
                "Javalin started in (\\d+)ms", "ms",
                "/ai/chat?user=bench&q=What+is+Java%3F",
                "/ai/rag?user=bench&q=What+is+LangChain4j%3F",
                "/ai/tools?user=bench&q=What+is+5+times+3%3F",
                new String[]{}, "Javalin started"
        ));
        m.put("spring-ai", new ProjectConfig(
                "spring-ai", BASE_DIR + "/spring-ai",
                new String[]{"java", "-jar", "target/spring-ai-demo-1.0.0-SNAPSHOT.jar"},
                9090, "/actuator/health",
                "Started Application in ([0-9.]+) seconds", "s",
                "/ai/chat?user=bench&q=What+is+Java%3F",
                "/ai/rag?user=bench&q=What+is+LangChain4j%3F",
                "/ai/tools?user=bench&q=What+is+5+times+3%3F",
                new String[]{}, null
        ));
        m.put("quarkus-easyrag", new ProjectConfig(
                "quarkus-easyrag (cold)", BASE_DIR + "/quarkus-langchain4j",
                new String[]{"java", "-jar", "target/quarkus-app/quarkus-run.jar"},
                8087, "/q/health/live",
                "started in ([0-9.]+)s", "s",
                "/ai/chat?user=bench&q=What+is+Java%3F",
                "/ai/rag?user=bench&q=What+is+LangChain4j%3F",
                "/ai/tools?user=bench&q=What+is+5+times+3%3F",
                new String[]{"delete-embeddings-cache"}, null
        ));
        m.put("quarkus-easyrag-warm", new ProjectConfig(
                "quarkus-easyrag (warm)", BASE_DIR + "/quarkus-langchain4j",
                new String[]{"java", "-jar", "target/quarkus-app/quarkus-run.jar"},
                8087, "/q/health/live",
                "started in ([0-9.]+)s", "s",
                "/ai/chat?user=bench&q=What+is+Java%3F",
                "/ai/rag?user=bench&q=What+is+LangChain4j%3F",
                "/ai/tools?user=bench&q=What+is+5+times+3%3F",
                new String[]{}, null
        ));
        m.put("quarkus-no-easyrag", new ProjectConfig(
                "quarkus-no-easyrag", BASE_DIR + "/quarkus-langchain4j-no-easyrag",
                new String[]{"java", "-jar", "target/quarkus-app/quarkus-run.jar"},
                8088, "/",
                "started in ([0-9.]+)s", "s",
                "/ai/chat?user=bench&q=What+is+Java%3F",
                "/ai/rag?user=bench&q=What+is+LangChain4j%3F",
                "/ai/tools?user=bench&q=What+is+5+times+3%3F",
                new String[]{}, null
        ));
        PROJECTS = Map.copyOf(m);
    }

    @Override
    public Integer call() throws Exception {
        var targets = new LinkedHashMap<>(PROJECTS);
        if (only != null) {
            if (!PROJECTS.containsKey(only)) {
                System.err.println("Unknown project: " + only + ". Available: " + PROJECTS.keySet());
                return 1;
            }
            targets.clear();
            targets.put(only, PROJECTS.get(only));
        }

        killTargets();
        Thread.sleep(2000);

        var allResults = new LinkedHashMap<String, RunResult>();

        for (var entry : targets.entrySet()) {
            var key = entry.getKey();
            var cfg = entry.getValue();
            System.out.printf("%n=== %s ===%n", cfg.name);

            executePreStart(cfg);

            killTargets();
            Thread.sleep(2000);

            var sr = measureStartup(cfg);
            if (sr == null) {
                System.out.println("  FAILED - skipping");
                continue;
            }
            System.out.printf("  Startup: wall=%dms  self=%s  rss=%dMB%n",
                    sr.wallMs, sr.selfReported, sr.rssMb);

            allResults.put(key, new RunResult((int) sr.wallMs, sr.selfReported, sr.rssMb));

            sr.process.destroyForcibly();
            sr.process.waitFor(10, TimeUnit.SECONDS);
        }

        printSummary(allResults);
        return 0;
    }

    record StartupResult(long wallMs, String selfReported, int rssMb, Process process) {}
    record RunResult(int wallMs, String selfReported, int rssMb) {}

    void executePreStart(ProjectConfig cfg) throws Exception {
        for (String action : cfg.preStartActions) {
            if ("delete-embeddings-cache".equals(action)) {
                var cache = Path.of(cfg.dir, "easy-rag-embeddings.json");
                if (Files.exists(cache)) {
                    Files.delete(cache);
                    System.out.println("  Deleted embeddings cache (cold start)");
                }
            }
        }
    }

    StartupResult measureStartup(ProjectConfig cfg) throws Exception {
        var logFile = Path.of("/tmp/measure_" + cfg.name.replace(" ", "_") + ".log");
        var pb = new ProcessBuilder(cfg.cmd);
        pb.directory(Path.of(cfg.dir).toFile());
        pb.environment().put("JAVA_HOME", JAVA_HOME);
        pb.environment().put("PATH", JAVA_HOME + "/bin:" + System.getenv("PATH"));
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()));

        long start = System.currentTimeMillis();
        var proc = pb.start();

        boolean ready;
        if (cfg.startupMarker != null) {
            ready = waitForLogMarker(logFile, cfg.startupMarker, 60);
        } else {
            ready = waitForPort(cfg.port, cfg.healthPath, 60);
        }
        long wall = System.currentTimeMillis() - start;

        if (!ready) {
            proc.destroyForcibly();
            System.out.printf("  TIMEOUT after %dms%n", wall);
            return null;
        }

        if (cfg.startupMarker != null) {
            Thread.sleep(500);
        }

        int rss = readRssMb(proc.pid());

        String selfReported = "N/A";
        try {
            var log = Files.readString(logFile);
            var m = Pattern.compile(cfg.selfReportedRegex).matcher(log);
            if (m.find()) {
                selfReported = cfg.selfReportedUnit.equals("ms")
                        ? m.group(1) + "ms"
                        : m.group(1) + "s";
            }
        } catch (Exception ignored) {}

        return new StartupResult(wall, selfReported, rss, proc);
    }

    boolean waitForLogMarker(Path logFile, String marker, int timeoutSec) {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (Files.exists(logFile) && Files.size(logFile) > 0) {
                    var content = Files.readString(logFile);
                    if (content.contains(marker)) return true;
                }
            } catch (Exception ignored) {}
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    boolean waitForPort(int port, String path, int timeoutSec) {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                var conn = (HttpURLConnection) new URL(
                        "http://localhost:" + port + path).openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code > 0) return true;
            } catch (Exception ignored) {}
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    int readRssMb(long pid) {
        try {
            var lines = Files.readAllLines(Path.of("/proc/" + pid + "/status"));
            for (String line : lines) {
                if (line.startsWith("VmRSS:")) {
                    return Integer.parseInt(line.trim().split("\\s+")[1]) / 1024;
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    void killTargets() throws Exception {
        for (var pattern : List.of("quarkus-run\\.jar", "langchain4j-pure-demo", "spring-ai-demo")) {
            var pb = new ProcessBuilder("pkill", "-9", "-f", pattern);
            pb.redirectErrorStream(true);
            pb.start().waitFor(3, TimeUnit.SECONDS);
        }
        Thread.sleep(500);
    }

    void printSummary(Map<String, RunResult> results) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("RESULTS SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("%-28s %-10s %-14s %-9s%n",
                "Project", "Wall(ms)", "Self-reported", "RSS(MB)");
        System.out.println("-".repeat(70));
        for (var e : results.entrySet()) {
            var r = e.getValue();
            System.out.printf("%-28s %-10d %-14s %-9d%n",
                    PROJECTS.get(e.getKey()).name,
                    r.wallMs,
                    r.selfReported,
                    r.rssMb
            );
        }
        System.out.println();
        System.out.println("Cold = embeddings re-computed at startup. Warm = cached embeddings.");
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Measure()).execute(args));
    }
}
