import java.io.*;
import java.util.*;

/**
 * CompareD  —  Head-to-head comparison benchmark
 * ================================================
 * Pits the custom MyArrayList and MyHashMap implementations against Java's
 * native ArrayList and HashMap using two measurement regimes:
 *
 *   Part A  (timing)  — nanoseconds per operation across five structure sizes.
 *                        Follows the EXACT same protocol as BenchmarkDataStructures:
 *                          • all random data pre-generated before any timed loop
 *                          • 1 000 000 untimed warm-up operations
 *                          • 1 000 000 timed operations via System.nanoTime()
 *                          • O(n)-per-call ops scaled so total work ≈ 2 × 10⁹ steps
 *                          • every result folded into volatile long `sink`
 *   Part B  (memory)  — bytes per element for 3 000 000-element structures.
 *                        Follows the EXACT same 7-step protocol as MemoryBenchmark:
 *                          gc/sleep → baseline → fill → gc/sleep → snapshot →
 *                          compute → null/gc/sleep
 *
 * Compile:  javac MyCollections.java CompareD.java
 * Run    :  java -Xmx3g CompareD
 *
 * Output :  console tables  +  compareD.csv
 *
 * CSV layout
 * ----------
 *  Section "TIMING":
 *    Operation | n=1K (My) | n=1K (Java) | n=1K Ratio | … | Guessed-O (My) | Guessed-O (Java)
 *    Ratio = Java ns/op ÷ My ns/op   (< 1.0 → native is faster; > 1.0 → custom is faster)
 *
 *  Section "MEMORY":
 *    Structure | Elements | My Heap (MB) | Java Heap (MB) | My Bytes/elem | Java Bytes/elem | Ratio
 *    Ratio = Java bytes/elem ÷ My bytes/elem
 */
public class CompareD {

    // =========================================================================
    // Configuration  (mirrors Part A + Part B exactly)
    // =========================================================================
    static final int[] SIZES = { 1_000, 10_000, 100_000, 1_000_000, 10_000_000 };
    static final int   WARM  = 1_000_000;   // untimed warm-up ops (Part A)
    static final int   TIMED = 1_000_000;   // timed ops           (Part A)
    static final int   MEM_N = 3_000_000;   // elements for memory test (Part B)

    // =========================================================================
    // Global state
    // =========================================================================
    /** volatile → prevents the JIT from treating timed loops as dead code. */
    static volatile long sink = 0;
    static final Random  RNG  = new Random(42);

    // ---- Timing result storage -----------------------------------------------
    // Parallel lists: rowLabels[i] → the operation name
    //                 myNs[i]      → ns/op per size for My* implementation
    //                 jvNs[i]      → ns/op per size for Java* implementation
    static final List<String>   rowLabels = new ArrayList<>();
    static final List<double[]> myNs      = new ArrayList<>();
    static final List<double[]> jvNs      = new ArrayList<>();

    // ---- Memory result storage -----------------------------------------------
    static final List<MemRow> memRows = new ArrayList<>();

    record MemRow(
        String name,
        int    elements,
        double myMB,    double javaMB,
        double myBPE,   double javaBPE
    ) {}

    // =========================================================================
    // Utilities  (identical logic to BenchmarkDataStructures)
    // =========================================================================

    /**
     * Returns the number of operations for an O(n)-per-call operation
     * so total work ≈ 2 × 10⁹ elementary steps, capped at TIMED.
     */
    static int opsFor(int n, boolean linearPerCall) {
        if (!linearPerCall) return TIMED;
        return (int) Math.max(20, Math.min(TIMED, 2_000_000_000L / n));
    }

    /** Pre-generate {@code count} random ints in [0, bound). */
    static int[] rnd(int count, int bound) {
        int[] a = new int[count];
        for (int i = 0; i < count; i++) a[i] = RNG.nextInt(bound);
        return a;
    }

    /** ns/op from a start timestamp and an operation count. */
    static double nsOp(long startNanos, int ops) {
        return (double)(System.nanoTime() - startNanos) / ops;
    }

    /** Append a paired timing row. */
    static void record(String label, double[] my, double[] jv) {
        rowLabels.add(label);
        myNs.add(my);
        jvNs.add(jv);
    }

    // =========================================================================
    // main
    // =========================================================================
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("=== CompareD: My* vs Java* Head-to-Head Benchmark ===");
        System.out.printf("Sizes      : %s%n", Arrays.toString(SIZES));
        System.out.printf("Warm-up    : %,d ops  |  Timed: %,d ops%n", WARM, TIMED);
        System.out.printf("Memory N   : %,d elements%n%n", MEM_N);

        // ── Part A: timing ────────────────────────────────────────────────────
        benchmarkArrayList();
        benchmarkHashMap();

        // ── Part B: memory ────────────────────────────────────────────────────
        measureMemory();

        // ── Output ────────────────────────────────────────────────────────────
        printTimingTable();
        printMemoryTable();
        writeCSV("compareD.csv");

        System.out.println("\nsink = " + sink + "  (guard: prevents dead-code elimination)");
    }

    // =========================================================================
    // Part A — Timing benchmarks
    // =========================================================================

    // -------------------------------------------------------------------------
    // MyArrayList  vs  ArrayList
    // Operations: get(index) O(1) | add(end) O(1) amort | add(front) O(n) | contains O(n)
    // -------------------------------------------------------------------------
    static void benchmarkArrayList() {
        System.out.println("--- MyArrayList  vs  ArrayList ---");

        double[] myGet = new double[SIZES.length];
        double[] jGet  = new double[SIZES.length];
        double[] myAE  = new double[SIZES.length];
        double[] jAE   = new double[SIZES.length];
        double[] myAF  = new double[SIZES.length];
        double[] jAF   = new double[SIZES.length];
        double[] myCon = new double[SIZES.length];
        double[] jCon  = new double[SIZES.length];

        for (int si = 0; si < SIZES.length; si++) {
            int n = SIZES[si];
            System.out.printf("  n = %,d%n", n);
            try {
                int bnd    = Math.max(n * 2, 1_000);
                int[] keys = rnd(WARM + TIMED, bnd);
                int[] idxs = rnd(WARM + TIMED, n);

                // ── Build base structures (both seeded with same data) ────────
                MyCollections.MyArrayList<Integer> myBase = new MyCollections.MyArrayList<>(n);
                ArrayList<Integer>                  jBase  = new ArrayList<>(n);
                RNG.setSeed(99);
                for (int i = 0; i < n; i++) { int v = RNG.nextInt(bnd); myBase.add(v); jBase.add(v); }

                // ── get(index)  O(1) ─────────────────────────────────────────
                // My warm-up
                for (int i = 0; i < WARM;  i++) sink += myBase.get(idxs[i % idxs.length]);
                long t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) sink += myBase.get(idxs[i % idxs.length]);
                myGet[si] = nsOp(t, TIMED);

                // Java warm-up
                for (int i = 0; i < WARM;  i++) sink += jBase.get(idxs[i % idxs.length]);
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) sink += jBase.get(idxs[i % idxs.length]);
                jGet[si] = nsOp(t, TIMED);

                // ── add at end  O(1) amortized ───────────────────────────────
                // My — warm-up then reset, then timed
                MyCollections.MyArrayList<Integer> myAeList = copyMyList(myBase);
                for (int i = 0; i < WARM;  i++) myAeList.add(keys[i % keys.length]);
                myAeList = copyMyList(myBase);
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) myAeList.add(keys[i % keys.length]);
                myAE[si] = nsOp(t, TIMED);
                sink += myAeList.size();

                // Java
                ArrayList<Integer> jAeList = new ArrayList<>(jBase);
                for (int i = 0; i < WARM;  i++) jAeList.add(keys[i % keys.length]);
                jAeList = new ArrayList<>(jBase);
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) jAeList.add(keys[i % keys.length]);
                jAE[si] = nsOp(t, TIMED);
                sink += jAeList.size();

                // ── add at front  O(n) ───────────────────────────────────────
                int afOps = opsFor(n, true);
                int afW   = Math.min(WARM, afOps);

                // My
                MyCollections.MyArrayList<Integer> myAfList = copyMyList(myBase);
                for (int i = 0; i < afW;   i++) myAfList.add(0, keys[i % keys.length]);
                myAfList = copyMyList(myBase);
                t = System.nanoTime();
                for (int i = 0; i < afOps; i++) myAfList.add(0, keys[i % keys.length]);
                myAF[si] = nsOp(t, afOps);
                sink += myAfList.size();

                // Java
                ArrayList<Integer> jAfList = new ArrayList<>(jBase);
                for (int i = 0; i < afW;   i++) jAfList.add(0, keys[i % keys.length]);
                jAfList = new ArrayList<>(jBase);
                t = System.nanoTime();
                for (int i = 0; i < afOps; i++) jAfList.add(0, keys[i % keys.length]);
                jAF[si] = nsOp(t, afOps);
                sink += jAfList.size();

                // ── contains  O(n) ───────────────────────────────────────────
                int cOps = opsFor(n, true);
                int cW   = Math.min(WARM, cOps);

                // My
                for (int i = 0; i < cW;    i++) { if (myBase.contains(keys[i % keys.length])) sink++; }
                t = System.nanoTime();
                for (int i = 0; i < cOps;  i++) { if (myBase.contains(keys[i % keys.length])) sink++; }
                myCon[si] = nsOp(t, cOps);

                // Java
                for (int i = 0; i < cW;    i++) { if (jBase.contains(keys[i % keys.length])) sink++; }
                t = System.nanoTime();
                for (int i = 0; i < cOps;  i++) { if (jBase.contains(keys[i % keys.length])) sink++; }
                jCon[si] = nsOp(t, cOps);

            } catch (OutOfMemoryError oom) {
                System.out.println("    [OOM]");
                myGet[si]=jGet[si]=myAE[si]=jAE[si]=myAF[si]=jAF[si]=myCon[si]=jCon[si]=Double.NaN;
                System.gc();
            }
        }

        record("ArrayList  get(index)", myGet, jGet);
        record("ArrayList  add(end)",   myAE,  jAE);
        record("ArrayList  add(front)", myAF,  jAF);
        record("ArrayList  contains",   myCon, jCon);
    }

    // -------------------------------------------------------------------------
    // MyHashMap  vs  HashMap
    // Operations: put O(1) amort | get O(1) amort | containsKey O(1) amort
    // -------------------------------------------------------------------------
    static void benchmarkHashMap() {
        System.out.println("--- MyHashMap  vs  HashMap ---");

        double[] myPut = new double[SIZES.length];
        double[] jPut  = new double[SIZES.length];
        double[] myGet = new double[SIZES.length];
        double[] jGet  = new double[SIZES.length];
        double[] myCK  = new double[SIZES.length];
        double[] jCK   = new double[SIZES.length];

        for (int si = 0; si < SIZES.length; si++) {
            int n = SIZES[si];
            System.out.printf("  n = %,d%n", n);
            try {
                int bnd    = Math.max(n * 2, 1_000);
                int[] keys = rnd(WARM + TIMED, bnd);
                int[] vals = rnd(WARM + TIMED, bnd);

                // ── Build base maps (both seeded with same data) ──────────────
                MyCollections.MyHashMap<Integer,Integer> myBase = new MyCollections.MyHashMap<>(Math.max(16, n));
                HashMap<Integer,Integer>                  jBase  = new HashMap<>((int)(n / 0.75) + 1);
                RNG.setSeed(77);
                for (int i = 0; i < n; i++) { int k = RNG.nextInt(bnd), v = RNG.nextInt(bnd); myBase.put(k,v); jBase.put(k,v); }

                // ── put  O(1) amortized ──────────────────────────────────────
                // My
                MyCollections.MyHashMap<Integer,Integer> myMp = copyMyMap(myBase, n);
                for (int i = 0; i < WARM;  i++) myMp.put(keys[i % keys.length], vals[i % vals.length]);
                myMp = copyMyMap(myBase, n);
                long t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) myMp.put(keys[i % keys.length], vals[i % vals.length]);
                myPut[si] = nsOp(t, TIMED);
                sink += myMp.size();

                // Java
                HashMap<Integer,Integer> jMp = new HashMap<>(jBase);
                for (int i = 0; i < WARM;  i++) jMp.put(keys[i % keys.length], vals[i % vals.length]);
                jMp = new HashMap<>(jBase);
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) jMp.put(keys[i % keys.length], vals[i % vals.length]);
                jPut[si] = nsOp(t, TIMED);
                sink += jMp.size();

                // ── get  O(1) amortized ──────────────────────────────────────
                // My
                for (int i = 0; i < WARM;  i++) { Integer v = myBase.get(keys[i % keys.length]); if (v != null) sink += v; }
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) { Integer v = myBase.get(keys[i % keys.length]); if (v != null) sink += v; }
                myGet[si] = nsOp(t, TIMED);

                // Java
                for (int i = 0; i < WARM;  i++) { Integer v = jBase.get(keys[i % keys.length]); if (v != null) sink += v; }
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) { Integer v = jBase.get(keys[i % keys.length]); if (v != null) sink += v; }
                jGet[si] = nsOp(t, TIMED);

                // ── containsKey  O(1) amortized ──────────────────────────────
                // My
                for (int i = 0; i < WARM;  i++) { if (myBase.containsKey(keys[i % keys.length])) sink++; }
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) { if (myBase.containsKey(keys[i % keys.length])) sink++; }
                myCK[si] = nsOp(t, TIMED);

                // Java
                for (int i = 0; i < WARM;  i++) { if (jBase.containsKey(keys[i % keys.length])) sink++; }
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) { if (jBase.containsKey(keys[i % keys.length])) sink++; }
                jCK[si] = nsOp(t, TIMED);

            } catch (OutOfMemoryError oom) {
                System.out.println("    [OOM]");
                myPut[si]=jPut[si]=myGet[si]=jGet[si]=myCK[si]=jCK[si]=Double.NaN;
                System.gc();
            }
        }

        record("HashMap  put",         myPut, jPut);
        record("HashMap  get",         myGet, jGet);
        record("HashMap  containsKey", myCK,  jCK);
    }

    // =========================================================================
    // Part B — Memory footprint
    // =========================================================================
    static void measureMemory() throws InterruptedException {
        System.out.println("\n--- Memory Footprint (3,000,000 elements) ---");
        memRows.add(measureMemPair("ArrayList", () -> {
            MyCollections.MyArrayList<Integer> m = new MyCollections.MyArrayList<>(MEM_N);
            for (int i = 0; i < MEM_N; i++) m.add(RNG.nextInt()); return m;
        }, () -> {
            ArrayList<Integer> j = new ArrayList<>(MEM_N);
            for (int i = 0; i < MEM_N; i++) j.add(RNG.nextInt()); return j;
        }));
        memRows.add(measureMemPair("HashMap", () -> {
            // Use next power-of-two >= MEM_N so bit-mask indexing distributes uniformly
            int cap = 1; while (cap < MEM_N) cap <<= 1;
            MyCollections.MyHashMap<Integer,Integer> m = new MyCollections.MyHashMap<>(cap);
            for (int i = 0; i < MEM_N; i++) m.put(RNG.nextInt(), RNG.nextInt()); return m;
        }, () -> {
            HashMap<Integer,Integer> j = new HashMap<>((int)(MEM_N / 0.75) + 1, 0.75f);
            for (int i = 0; i < MEM_N; i++) j.put(RNG.nextInt(), RNG.nextInt()); return j;
        }));
    }

    @FunctionalInterface
    interface Filler { Object fill(); }

    /**
     * Runs the 7-step MemoryBenchmark protocol for BOTH the My* and Java*
     * variant of one structure, back to back.
     */
    static MemRow measureMemPair(String name, Filler myFill, Filler jvFill)
            throws InterruptedException {
        Runtime rt = Runtime.getRuntime();

        // ── My* measurement ──────────────────────────────────────────────────
        System.out.printf("  My%-18s ...", name);
        System.gc(); Thread.sleep(100);
        long before = rt.totalMemory() - rt.freeMemory();
        Object myCol = myFill.fill();
        System.gc(); Thread.sleep(100);
        long after = rt.totalMemory() - rt.freeMemory();
        double myDeltaMB = (after - before) / (1024.0 * 1024.0);
        double myBPE     = (double)(after - before) / MEM_N;
        myCol = null;
        System.gc(); Thread.sleep(100);
        System.out.printf(" %.2f MB  (%.1f B/elem)%n", myDeltaMB, myBPE);

        // ── Java* measurement ─────────────────────────────────────────────────
        System.out.printf("  Java%-16s ...", name);
        System.gc(); Thread.sleep(100);
        before = rt.totalMemory() - rt.freeMemory();
        Object jvCol = jvFill.fill();
        System.gc(); Thread.sleep(100);
        after = rt.totalMemory() - rt.freeMemory();
        double jvDeltaMB = (after - before) / (1024.0 * 1024.0);
        double jvBPE     = (double)(after - before) / MEM_N;
        jvCol = null;
        System.gc(); Thread.sleep(100);
        System.out.printf(" %.2f MB  (%.1f B/elem)%n", jvDeltaMB, jvBPE);

        return new MemRow(name, MEM_N, myDeltaMB, jvDeltaMB, myBPE, jvBPE);
    }

    // =========================================================================
    // Copy helpers  (used to reset structures between warm-up and timed runs)
    // =========================================================================

    /**
     * O(n) bulk copy using MyArrayList's copy constructor (System.arraycopy internally).
     * Far faster than element-by-element copying for large n.
     */
    static MyCollections.MyArrayList<Integer> copyMyList(MyCollections.MyArrayList<Integer> src) {
        return new MyCollections.MyArrayList<>(src);
    }

    /**
     * Replays the same deterministic RNG sequence used to build the base map.
     * The initial capacity is the next power of two above n so the bit-mask
     * index function distributes entries uniformly from the start (no rehash
     * during the timed window).
     */
    static MyCollections.MyHashMap<Integer,Integer> copyMyMap(
            MyCollections.MyHashMap<Integer,Integer> src, int n) {
        int bnd = Math.max(n * 2, 1_000);
        // next power of two >= n keeps load below 1.0 before any resize
        int cap = 1; while (cap < n) cap <<= 1;
        MyCollections.MyHashMap<Integer,Integer> dst = new MyCollections.MyHashMap<>(cap);
        RNG.setSeed(77);
        for (int i = 0; i < n; i++) dst.put(RNG.nextInt(bnd), RNG.nextInt(bnd));
        return dst;
    }

    // =========================================================================
    // Big-O guesser  (identical logic to BenchmarkDataStructures)
    // =========================================================================
    static String guessComplexity(double[] row) {
        double sumLog = 0; int cnt = 0;
        for (int i = 1; i < row.length; i++) {
            if (Double.isNaN(row[i]) || Double.isNaN(row[i-1]) || row[i-1] <= 0 || row[i] <= 0) continue;
            sumLog += Math.log(row[i] / row[i-1]); cnt++;
        }
        if (cnt == 0) return "N/A";
        double r = Math.exp(sumLog / cnt);
        if (r < 1.15) return "O(1)";
        if (r < 2.50) return "O(log n)";
        if (r < 5.50) return "O(sqrt n)";
        if (r < 22.0) return "O(n)";
        return "O(n log n)+";
    }

    // =========================================================================
    // Console output
    // =========================================================================

    static String fmtNs(double v) {
        return Double.isNaN(v) ? "     OOM" : String.format("%8.2f", v);
    }
    static String fmtRatio(double my, double jv) {
        if (Double.isNaN(my) || Double.isNaN(jv) || my == 0) return "     N/A";
        return String.format("%8.3f", jv / my);
    }

    static void printTimingTable() {
        System.out.println("\n=== Part A — Timing (nanoseconds per operation) ===");
        System.out.println("  Ratio = Java ns/op ÷ My ns/op  (< 1.0 → native is faster)");
        System.out.println();

        // Print one block per row (avoids extremely wide tables)
        for (int r = 0; r < rowLabels.size(); r++) {
            String   lbl = rowLabels.get(r);
            double[] my  = myNs.get(r);
            double[] jv  = jvNs.get(r);

            System.out.printf("  %-28s%n", lbl);
            System.out.printf("    %-10s", "");
            for (int n : SIZES) System.out.printf("  %10s", "n=" + fmtN(n));
            System.out.printf("  %-12s%n", "Guessed-O");

            System.out.printf("    %-10s", "My*");
            for (double v : my)  System.out.printf("  %10s", fmtNs(v));
            System.out.printf("  %-12s%n", guessComplexity(my));

            System.out.printf("    %-10s", "Java*");
            for (double v : jv)  System.out.printf("  %10s", fmtNs(v));
            System.out.printf("  %-12s%n", guessComplexity(jv));

            System.out.printf("    %-10s", "Ratio");
            for (int i = 0; i < SIZES.length; i++) System.out.printf("  %10s", fmtRatio(my[i], jv[i]));
            System.out.println();
            System.out.println();
        }
        System.out.println("  (all values are nanoseconds per operation)");
        System.out.println("  (O(n)-per-call ops use a reduced sample count; ns/op still correct)");
    }

    static String fmtN(int n) {
        if (n >= 1_000_000) return (n/1_000_000) + "M";
        if (n >= 1_000)     return (n/1_000) + "K";
        return String.valueOf(n);
    }

    static void printMemoryTable() {
        System.out.println("=== Part B — Memory Footprint (bytes per element, N = 3,000,000) ===");
        System.out.printf("  %-12s  %12s  %12s  %12s  %12s  %12s  %8s%n",
                "Structure", "My MB", "Java MB", "My B/elem", "Java B/elem", "Δ MB", "Ratio");
        System.out.println("  " + "-".repeat(88));
        for (MemRow row : memRows) {
            double ratio = row.myBPE() > 0 ? row.javaBPE() / row.myBPE() : Double.NaN;
            System.out.printf("  %-12s  %12.2f  %12.2f  %12.1f  %12.1f  %12.2f  %8.3f%n",
                    row.name(),
                    row.myMB(), row.javaMB(),
                    row.myBPE(), row.javaBPE(),
                    row.javaMB() - row.myMB(),
                    ratio);
        }
        System.out.println("  " + "-".repeat(88));
        System.out.println("  Ratio = Java bytes/elem ÷ My bytes/elem");
        System.out.println();
    }

    // =========================================================================
    // CSV output
    // =========================================================================
    static void writeCSV(String filename) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {

            // ── Timing section ────────────────────────────────────────────────
            pw.println("=== TIMING (nanoseconds per operation) ===");
            pw.println("Ratio = Java ns/op / My ns/op  (< 1.0 means native is faster)");

            // Header row: Operation | n=Xk (My) | n=Xk (Java) | n=Xk Ratio | …
            pw.print("Operation");
            for (int n : SIZES) {
                String s = fmtN(n);
                pw.print(",n=" + s + " (My),n=" + s + " (Java),n=" + s + " Ratio");
            }
            pw.println(",Guessed-O (My),Guessed-O (Java)");

            // Data rows
            for (int r = 0; r < rowLabels.size(); r++) {
                pw.print(rowLabels.get(r));
                double[] my = myNs.get(r);
                double[] jv = jvNs.get(r);
                for (int i = 0; i < SIZES.length; i++) {
                    // My
                    pw.print(",");
                    if (Double.isNaN(my[i])) pw.print("OOM"); else pw.printf("%.3f", my[i]);
                    // Java
                    pw.print(",");
                    if (Double.isNaN(jv[i])) pw.print("OOM"); else pw.printf("%.3f", jv[i]);
                    // Ratio
                    pw.print(",");
                    if (Double.isNaN(my[i]) || Double.isNaN(jv[i]) || my[i] == 0) pw.print("N/A");
                    else pw.printf("%.4f", jv[i] / my[i]);
                }
                pw.print("," + guessComplexity(my));
                pw.print("," + guessComplexity(jv));
                pw.println();
            }

            pw.println();

            // ── Memory section ────────────────────────────────────────────────
            pw.println("=== MEMORY (bytes per element, N = 3,000,000) ===");
            pw.println("Ratio = Java bytes/elem / My bytes/elem");
            pw.println("Structure,Elements,My Heap (MB),Java Heap (MB),My Bytes/elem,Java Bytes/elem,Ratio");

            for (MemRow row : memRows) {
                double ratio = row.myBPE() > 0 ? row.javaBPE() / row.myBPE() : Double.NaN;
                pw.printf("%s,%d,%.2f,%.2f,%.1f,%.1f,%.4f%n",
                        row.name(), row.elements(),
                        row.myMB(), row.javaMB(),
                        row.myBPE(), row.javaBPE(),
                        ratio);
            }
        }
        System.out.println("CSV written → " + filename);
    }
}

