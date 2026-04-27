#!/usr/bin/env python3
"""
Fair comparison: startup time + time-to-first-response (TTFR) for
LangChain4j Pure, Spring AI, Quarkus (EasyRAG), Quarkus (No EasyRAG).

All projects use the same LLM (Ollama qwen3:1.7b) and embedding model (nomic-embed-text).

Usage:
  python3 scripts/measure_all.py [--runs N] [--skip-ttfr] [--only PROJECT]

Projects: lc4j-pure, spring-ai, quarkus-easyrag, quarkus-no-easyrag
"""

import argparse
import json
import os
import re
import signal
import subprocess
import sys
import time
import urllib.request
import urllib.error
from dataclasses import dataclass, field

JAVA_HOME = "/home/omatheusmesmo/.sdkman/candidates/java/25.0.3-tem"
BASE_DIR = "/mnt/fileshare/projects/ai/java-ai-comparison"

PROJECTS = {
    "lc4j-pure": {
        "dir": f"{BASE_DIR}/langchain4j-pure",
        "cmd": ["java", "-jar", "target/langchain4j-pure-demo-1.0.0-SNAPSHOT-all.jar"],
        "port": 8081,
        "health_path": "/ai/chat",
        "health_method": "GET",
        "health_params": "?user=measure&q=hello",
        "ttfr_endpoint": "/ai/chat?user=measure&q=What+is+LangChain4j%3F",
        "ttfr_method": "GET",
        "rag_endpoint": "/ai/rag?user=measure&q=What+is+LangChain4j%3F",
        "tools_endpoint": "/ai/tools?user=measure&q=What+is+5+times+3%3F",
        "self_reported_regex": r"Javalin started in (\d+)ms",
        "self_reported_unit": "ms",
        "cwd": f"{BASE_DIR}/langchain4j-pure",
        "pre_cmd": ["mvn", "package", "-DskipTests", "-q"],
    },
    "spring-ai": {
        "dir": f"{BASE_DIR}/spring-ai",
        "cmd": ["java", "-jar", "target/spring-ai-demo-1.0.0-SNAPSHOT.jar"],
        "port": 9090,
        "health_path": "/actuator/health",
        "health_method": "GET",
        "health_params": "",
        "ttfr_endpoint": "/ai/chat?user=measure&q=What+is+Spring+AI%3F",
        "ttfr_method": "GET",
        "rag_endpoint": "/ai/rag?user=measure&q=What+is+Spring+AI%3F",
        "tools_endpoint": "/ai/tools?user=measure&q=What+is+5+times+3%3F",
        "self_reported_regex": r"Started Application in ([0-9.]+) seconds",
        "self_reported_unit": "s",
        "cwd": f"{BASE_DIR}/spring-ai",
        "pre_cmd": ["mvn", "package", "-DskipTests", "-q"],
    },
    "quarkus-easyrag": {
        "dir": f"{BASE_DIR}/quarkus-langchain4j",
        "cmd": ["java", "-jar", "target/quarkus-app/quarkus-run.jar"],
        "port": 8087,
        "health_path": "/q/health/live",
        "health_method": "GET",
        "health_params": "",
        "ttfr_endpoint": "/ai/chat?user=measure&q=What+is+Quarkus%3F",
        "ttfr_method": "GET",
        "rag_endpoint": "/ai/rag?user=measure&q=What+is+Quarkus%3F",
        "tools_endpoint": "/ai/tools?user=measure&q=What+is+5+times+3%3F",
        "self_reported_regex": r"started in ([0-9.]+)s",
        "self_reported_unit": "s",
        "cwd": f"{BASE_DIR}/quarkus-langchain4j",
        "pre_cmd": ["mvn", "package", "-DskipTests", "-q"],
    },
    "quarkus-no-easyrag": {
        "dir": f"{BASE_DIR}/quarkus-langchain4j-no-easyrag",
        "cmd": ["java", "-jar", "target/quarkus-app/quarkus-run.jar"],
        "port": 8088,
        "health_path": "/",
        "health_method": "GET",
        "health_params": "",
        "ttfr_endpoint": "/ai/chat?user=measure&q=What+is+Quarkus%3F",
        "ttfr_method": "GET",
        "rag_endpoint": "/ai/rag?user=measure&q=What+is+Quarkus%3F",
        "tools_endpoint": "/ai/tools?user=measure&q=What+is+5+times+3%3F",
        "self_reported_regex": r"started in ([0-9.]+)s",
        "self_reported_unit": "s",
        "cwd": f"{BASE_DIR}/quarkus-langchain4j-no-easyrag",
        "pre_cmd": ["mvn", "package", "-DskipTests", "-q"],
    },
}


def get_rss_mb(pid):
    try:
        with open(f"/proc/{pid}/status") as f:
            for line in f:
                if line.startswith("VmRSS:"):
                    return int(line.split()[1]) // 1024
    except:
        pass
    return None


def wait_for_port(port, path, timeout=60):
    url = f"http://localhost:{port}{path}"
    start = time.time()
    while time.time() - start < timeout:
        try:
            urllib.request.urlopen(url, timeout=2)
            return True
        except (urllib.error.URLError, ConnectionRefusedError, OSError):
            time.sleep(0.3)
    return False


def kill_java():
    subprocess.run(["killall", "-9", "java"], capture_output=True)
    time.sleep(2)


def build_project(name, cfg):
    print(f"  Building {name}...", flush=True)
    env = os.environ.copy()
    env["JAVA_HOME"] = JAVA_HOME
    env["PATH"] = f"{JAVA_HOME}/bin:{env.get('PATH', '')}"
    result = subprocess.run(
        cfg["pre_cmd"],
        cwd=cfg["cwd"],
        env=env,
        capture_output=True,
        text=True,
        timeout=300,
    )
    if result.returncode != 0:
        print(f"  BUILD FAILED for {name}: {result.stderr[-500:]}", flush=True)
        return False
    print(f"  Build OK", flush=True)
    return True


def measure_startup(name, cfg, log_path):
    env = os.environ.copy()
    env["JAVA_HOME"] = JAVA_HOME
    env["PATH"] = f"{JAVA_HOME}/bin:{env.get('PATH', '')}"

    start = time.time()
    proc = subprocess.Popen(
        cfg["cmd"],
        cwd=cfg["cwd"],
        env=env,
        stdout=open(log_path, "w"),
        stderr=subprocess.STDOUT,
    )

    health_url = f"http://localhost:{cfg['port']}{cfg['health_path']}{cfg['health_params']}"
    ready = wait_for_port(cfg["port"], f"{cfg['health_path']}{cfg['health_params']}")
    end = time.time()
    wall_ms = int((end - start) * 1000)

    if not ready:
        print(f"  STARTUP TIMEOUT ({wall_ms}ms)", flush=True)
        proc.kill()
        return None

    rss = get_rss_mb(proc.pid)

    with open(log_path) as f:
        log = f.read()

    self_reported = None
    m = re.search(cfg["self_reported_regex"], log)
    if m:
        val = m.group(1)
        if cfg["self_reported_unit"] == "ms":
            self_reported = f"{val}ms"
        else:
            self_reported = f"{val}s"

    return {
        "wall_ms": wall_ms,
        "self_reported": self_reported,
        "rss_mb": rss,
        "pid": proc.pid,
        "log": log,
    }


def measure_ttfr(name, cfg, pid, endpoint_key, warmup_hits=1):
    url = f"http://localhost:{cfg['port']}{cfg[endpoint_key]}"

    for _ in range(warmup_hits):
        try:
            urllib.request.urlopen(url, timeout=60)
        except:
            pass
        time.sleep(1)

    start = time.time()
    try:
        resp = urllib.request.urlopen(url, timeout=120)
        elapsed_ms = int((time.time() - start) * 1000)
        body = resp.read().decode("utf-8", errors="replace")[:200]
        return {"ttfr_ms": elapsed_ms, "response": body}
    except Exception as e:
        elapsed_ms = int((time.time() - start) * 1000)
        return {"ttfr_ms": elapsed_ms, "error": str(e)}


def run_project(name, cfg, run_idx, skip_ttfr=False):
    log_path = f"/tmp/measure_{name}_run{run_idx}.log"
    print(f"\n=== {name} (run {run_idx}) ===", flush=True)

    kill_java()

    result = measure_startup(name, cfg, log_path)
    if result is None:
        kill_java()
        return None

    print(f"  Startup: wall={result['wall_ms']}ms  self={result['self_reported']}  rss={result['rss_mb']}MB", flush=True)

    ttfr = {}
    if not skip_ttfr:
        for ep_key in ["ttfr_endpoint", "rag_endpoint", "tools_endpoint"]:
            label = ep_key.replace("_endpoint", "")
            print(f"  Measuring TTFR for {label}...", flush=True)
            ttfr_result = measure_ttfr(name, cfg, result["pid"], ep_key, warmup_hits=0)
            ttfr[label] = ttfr_result
            print(f"    {label}: {ttfr_result['ttfr_ms']}ms", flush=True)
            time.sleep(2)

    kill_java()

    return {
        "wall_ms": result["wall_ms"],
        "self_reported": result["self_reported"],
        "rss_mb": result["rss_mb"],
        "ttfr": ttfr,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", type=int, default=1, help="Number of runs per project")
    parser.add_argument("--skip-ttfr", action="store_true", help="Skip TTFR measurements")
    parser.add_argument("--only", type=str, default=None, help="Only run this project")
    parser.add_argument("--skip-build", action="store_true", help="Skip build step")
    args = parser.parse_args()

    projects = PROJECTS
    if args.only:
        if args.only not in PROJECTS:
            print(f"Unknown project: {args.only}. Available: {list(PROJECTS.keys())}")
            sys.exit(1)
        projects = {args.only: PROJECTS[args.only]}

    if not args.skip_build:
        for name, cfg in projects.items():
            if not build_project(name, cfg):
                print(f"Skipping {name} due to build failure")
                del projects[name]

    all_results = {}
    for name, cfg in projects.items():
        runs = []
        for i in range(1, args.runs + 1):
            r = run_project(name, cfg, i, skip_ttfr=args.skip_ttfr)
            if r:
                runs.append(r)
            time.sleep(3)
        if runs:
            avg_wall = sum(r["wall_ms"] for r in runs) // len(runs)
            avg_rss = sum(r["rss_mb"] for r in runs) // len(runs) if runs[0]["rss_mb"] else None
            self_reported = runs[0]["self_reported"]
            ttfr_avg = {}
            if not args.skip_ttfr and runs[0].get("ttfr"):
                for label in runs[0]["ttfr"]:
                    vals = [r["ttfr"][label]["ttfr_ms"] for r in runs if label in r.get("ttfr", {})]
                    if vals:
                        ttfr_avg[label] = sum(vals) // len(vals)

            all_results[name] = {
                "runs": len(runs),
                "avg_wall_ms": avg_wall,
                "self_reported": self_reported,
                "avg_rss_mb": avg_rss,
                "ttfr_avg_ms": ttfr_avg,
            }

    print("\n" + "=" * 70)
    print("RESULTS SUMMARY")
    print("=" * 70)
    print(f"{'Project':<25} {'Wall(ms)':<12} {'Self':<15} {'RSS(MB)':<10} {'TTFR-chat':<12} {'TTFR-rag':<12} {'TTFR-tools':<12}")
    print("-" * 70)
    for name, r in all_results.items():
        ttfr = r.get("ttfr_avg_ms", {})
        print(
            f"{name:<25} {r['avg_wall_ms']:<12} {r['self_reported']:<15} {r['avg_rss_mb'] or 'N/A':<10} "
            f"{ttfr.get('ttfr', 'N/A'):<12} {ttfr.get('rag', 'N/A'):<12} {ttfr.get('tools', 'N/A'):<12}"
        )

    with open("/tmp/measure_all_results.json", "w") as f:
        json.dump(all_results, f, indent=2)
    print(f"\nRaw results saved to /tmp/measure_all_results.json")


if __name__ == "__main__":
    main()
