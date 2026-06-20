import java.io.*;
import java.util.*;

/**
 * BenchmarkDataStructures
 * ========================
 * Measures nanoseconds-per-operation for the eight core Java collection
 * classes across five structure sizes.
 *
 * Compile:  javac BenchmarkDataStructures.java
 * Run    :  java -Xmx3g BenchmarkDataStructures
 *
 * Output :  console table  +  timeA.csv
 *
 * Design notes
 * ------------
 * • All random data is generated BEFORE any timed loop (no disk I/O inside timing).
 * • 1 000 000 untimed warm-up operations precede each timed window so the JIT
 *   has compiled hot methods before measurement starts.
 * • Every result is accumulated into `sink` (volatile long) and printed at the
 *   end so the JIT cannot eliminate any computation as dead code.
 * • O(n)-per-call operations (ArrayList.add-at-front, ArrayList.contains,
 *   LinkedList.get, LinkedList.contains, ArrayDeque.contains) are scaled so
 *   total work ≈ 2 × 10⁹ elementary steps; the reported value is still
 *   nanoseconds per operation.
 * • OutOfMemoryError is caught for each (structure, n) pair; affected cells
 *   are reported as OOM.
 * • Big-O is guessed from the geometric-mean ratio of consecutive timings as
 *   n grows by 10×.
 */
public class BenchmarkDataStructures {

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------
    static final int[] SIZES = { 1_000, 10_000, 100_000, 1_000_000, 10_000_000 };
    static final int   WARM  = 1_000_000;   // untimed warm-up operations
    static final int   TIMED = 1_000_000;   // timed operations

    // -------------------------------------------------------------------------
    // Global state
    // -------------------------------------------------------------------------
    static volatile long sink = 0;          // volatile → prevents dead-code elimination
    static final Random  RNG  = new Random(42);

    static final List<String>   rowLabels = new ArrayList<>();
    static final List<double[]> rowData   = new ArrayList<>();

    // =========================================================================
    // main
    // =========================================================================
    public static void main(String[] args) throws IOException {
        System.out.println("=== Data-Structure Benchmark ===");
        System.out.printf("Sizes           : %s%n", Arrays.toString(SIZES));
        System.out.printf("Warm-up ops     : %,d%n", WARM);
        System.out.printf("Timed ops       : %,d%n%n", TIMED);

        benchmarkArrayList();
        benchmarkLinkedList();
        benchmarkArrayDeque();
        benchmarkHashSet();
        benchmarkTreeSet();
        benchmarkHashMap();
        benchmarkTreeMap();
        benchmarkPriorityQueue();

        System.out.println();
        printTable();
        writeCSV("timeA.csv");
        System.out.println("\nsink = " + sink + "  (guard: prevents dead-code elimination)");
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Returns the number of operations to use for an O(n)-per-call operation
     * so that total work ≈ 2 × 10⁹ elementary steps.  Always at least 20.
     * For O(1) / O(log n) operations always returns TIMED.
     */
    static int opsFor(int n, boolean isLinearPerCall) {
        if (!isLinearPerCall) return TIMED;
        return (int) Math.max(20, Math.min(TIMED, 2_000_000_000L / n));
    }

    /** Pre-generate `count` random ints in [0, bound). */
    static int[] rnd(int count, int bound) {
        int[] a = new int[count];
        for (int i = 0; i < count; i++) a[i] = RNG.nextInt(bound);
        return a;
    }

    /** Compute ns / operation given a System.nanoTime() start and an op count. */
    static double nsOp(long startNanos, int ops) {
        return (double) (System.nanoTime() - startNanos) / ops;
    }

    /** Append a result row. */
    static void record(String label, double[] row) {
        rowLabels.add(label);
        rowData.add(row);
    }

    // =========================================================================
    // ArrayList
    // =========================================================================
    static void benchmarkArrayList() {
        System.out.println("--- ArrayList ---");
        double[] tGet = new double[SIZES.length];
        double[] tAE  = new double[SIZES.length];
        double[] tAF  = new double[SIZES.length];
        double[] tCon = new double[SIZES.length];

        for (int si = 0; si < SIZES.length; si++) {
            int n = SIZES[si];
            System.out.printf("  n = %,d%n", n);
            try {
                int bnd    = Math.max(n * 2, 1_000);
                int[] keys = rnd(WARM + TIMED, bnd);   // search / insert keys
                int[] idxs = rnd(WARM + TIMED, n);     // random valid indices

                // Build base list once
                ArrayList<Integer> base = new ArrayList<>(n);
                for (int i = 0; i < n; i++) base.add(RNG.nextInt(bnd));

                // ── get(index)  O(1) ─────────────────────────────────────────
                for (int i = 0; i < WARM;  i++) sink += base.get(idxs[i % idxs.length]);
                long t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) sink += base.get(idxs[i % idxs.length]);
                tGet[si] = nsOp(t, TIMED);

                // ── add at end  O(1) amortized ───────────────────────────────
                ArrayList<Integer> ae = new ArrayList<>(base);
                for (int i = 0; i < WARM;  i++) ae.add(keys[i % keys.length]);
                ae = new ArrayList<>(base);             // reset
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) ae.add(keys[i % keys.length]);
                tAE[si] = nsOp(t, TIMED);
                sink += ae.size();

                // ── add at front  O(n) ───────────────────────────────────────
                int afOps = opsFor(n, true);
                int afW   = Math.min(WARM, afOps);
                ArrayList<Integer> af = new ArrayList<>(base);
                for (int i = 0; i < afW;   i++) af.add(0, keys[i % keys.length]);
                af = new ArrayList<>(base);             // reset
                t = System.nanoTime();
                for (int i = 0; i < afOps; i++) af.add(0, keys[i % keys.length]);
                tAF[si] = nsOp(t, afOps);
                sink += af.size();

                // ── contains  O(n) ───────────────────────────────────────────
                int cOps = opsFor(n, true);
                int cW   = Math.min(WARM, cOps);
                for (int i = 0; i < cW;    i++) { if (base.contains(keys[i % keys.length])) sink++; }
                t = System.nanoTime();
                for (int i = 0; i < cOps;  i++) { if (base.contains(keys[i % keys.length])) sink++; }
                tCon[si] = nsOp(t, cOps);

            } catch (OutOfMemoryError oom) {
                System.out.println("    [OOM]");
                tGet[si] = tAE[si] = tAF[si] = tCon[si] = Double.NaN;
                System.gc();
            }
        }

        record("ArrayList.get(index)", tGet);
        record("ArrayList.add(end)",   tAE);
        record("ArrayList.add(front)", tAF);
        record("ArrayList.contains",   tCon);
    }

    // =========================================================================
    // LinkedList
    // =========================================================================
    static void benchmarkLinkedList() {
        System.out.println("--- LinkedList ---");
        double[] tGet = new double[SIZES.length];
        double[] tAE  = new double[SIZES.length];
        double[] tAF  = new double[SIZES.length];
        double[] tCon = new double[SIZES.length];

        for (int si = 0; si < SIZES.length; si++) {
            int n = SIZES[si];
            System.out.printf("  n = %,d%n", n);
            try {
                int bnd    = Math.max(n * 2, 1_000);
                int[] keys = rnd(WARM + TIMED, bnd);
                int[] idxs = rnd(WARM + TIMED, n);

                LinkedList<Integer> base = new LinkedList<>();
                for (int i = 0; i < n; i++) base.add(RNG.nextInt(bnd));

                // ── get(index)  O(n) ─────────────────────────────────────────
                int gOps = opsFor(n, true);
                int gW   = Math.min(WARM, gOps);
                for (int i = 0; i < gW;   i++) sink += base.get(idxs[i % idxs.length]);
                long t = System.nanoTime();
                for (int i = 0; i < gOps; i++) sink += base.get(idxs[i % idxs.length]);
                tGet[si] = nsOp(t, gOps);

                // ── addLast  O(1) ────────────────────────────────────────────
                LinkedList<Integer> ae = new LinkedList<>(base);
                for (int i = 0; i < WARM;  i++) ae.addLast(keys[i % keys.length]);
                ae = new LinkedList<>(base);
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) ae.addLast(keys[i % keys.length]);
                tAE[si] = nsOp(t, TIMED);
                sink += ae.size();

                // ── addFirst  O(1) ───────────────────────────────────────────
                LinkedList<Integer> af = new LinkedList<>(base);
                for (int i = 0; i < WARM;  i++) af.addFirst(keys[i % keys.length]);
                af = new LinkedList<>(base);
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) af.addFirst(keys[i % keys.length]);
                tAF[si] = nsOp(t, TIMED);
                sink += af.size();

                // ── contains  O(n) ───────────────────────────────────────────
                int cOps = opsFor(n, true);
                int cW   = Math.min(WARM, cOps);
                for (int i = 0; i < cW;    i++) { if (base.contains(keys[i % keys.length])) sink++; }
                t = System.nanoTime();
                for (int i = 0; i < cOps;  i++) { if (base.contains(keys[i % keys.length])) sink++; }
                tCon[si] = nsOp(t, cOps);

            } catch (OutOfMemoryError oom) {
                System.out.println("    [OOM]");
                tGet[si] = tAE[si] = tAF[si] = tCon[si] = Double.NaN;
                System.gc();
            }
        }

        record("LinkedList.get(index)", tGet);
        record("LinkedList.addLast",    tAE);
        record("LinkedList.addFirst",   tAF);
        record("LinkedList.contains",   tCon);
    }

    // =========================================================================
    // ArrayDeque
    // =========================================================================
    static void benchmarkArrayDeque() {
        System.out.println("--- ArrayDeque ---");
        double[] tAE  = new double[SIZES.length];
        double[] tAF  = new double[SIZES.length];
        double[] tCon = new double[SIZES.length];

        for (int si = 0; si < SIZES.length; si++) {
            int n = SIZES[si];
            System.out.printf("  n = %,d%n", n);
            try {
                int bnd    = Math.max(n * 2, 1_000);
                int[] keys = rnd(WARM + TIMED, bnd);

                ArrayDeque<Integer> base = new ArrayDeque<>(n);
                for (int i = 0; i < n; i++) base.addLast(RNG.nextInt(bnd));

                // ── addLast  O(1) amortized ──────────────────────────────────
                ArrayDeque<Integer> ae = new ArrayDeque<>(base);
                for (int i = 0; i < WARM;  i++) ae.addLast(keys[i % keys.length]);
                ae = new ArrayDeque<>(base);
                long t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) ae.addLast(keys[i % keys.length]);
                tAE[si] = nsOp(t, TIMED);
                sink += ae.size();

                // ── addFirst  O(1) amortized ─────────────────────────────────
                ArrayDeque<Integer> af = new ArrayDeque<>(base);
                for (int i = 0; i < WARM;  i++) af.addFirst(keys[i % keys.length]);
                af = new ArrayDeque<>(base);
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) af.addFirst(keys[i % keys.length]);
                tAF[si] = nsOp(t, TIMED);
                sink += af.size();

                // ── contains  O(n) ───────────────────────────────────────────
                int cOps = opsFor(n, true);
                int cW   = Math.min(WARM, cOps);
                for (int i = 0; i < cW;    i++) { if (base.contains(keys[i % keys.length])) sink++; }
                t = System.nanoTime();
                for (int i = 0; i < cOps;  i++) { if (base.contains(keys[i % keys.length])) sink++; }
                tCon[si] = nsOp(t, cOps);

            } catch (OutOfMemoryError oom) {
                System.out.println("    [OOM]");
                tAE[si] = tAF[si] = tCon[si] = Double.NaN;
                System.gc();
            }
        }

        record("ArrayDeque.addLast",  tAE);
        record("ArrayDeque.addFirst", tAF);
        record("ArrayDeque.contains", tCon);
    }

    // =========================================================================
    // HashSet
    // =========================================================================
    static void benchmarkHashSet() {
        System.out.println("--- HashSet ---");
        double[] tAdd = new double[SIZES.length];
        double[] tCon = new double[SIZES.length];

        for (int si = 0; si < SIZES.length; si++) {
            int n = SIZES[si];
            System.out.printf("  n = %,d%n", n);
            try {
                int bnd    = Math.max(n * 2, 1_000);
                int[] keys = rnd(WARM + TIMED, bnd);

                // Pre-size to avoid rehashing during timing
                HashSet<Integer> base = new HashSet<>((int)(n / 0.75) + 1);
                for (int i = 0; i < n; i++) base.add(RNG.nextInt(bnd));

                // ── add  O(1) amortized ──────────────────────────────────────
                HashSet<Integer> sa = new HashSet<>(base);
                for (int i = 0; i < WARM;  i++) sa.add(keys[i % keys.length]);
                sa = new HashSet<>(base);
                long t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) sa.add(keys[i % keys.length]);
                tAdd[si] = nsOp(t, TIMED);
                sink += sa.size();

                // ── contains  O(1) amortized ─────────────────────────────────
                for (int i = 0; i < WARM;  i++) { if (base.contains(keys[i % keys.length])) sink++; }
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) { if (base.contains(keys[i % keys.length])) sink++; }
                tCon[si] = nsOp(t, TIMED);

            } catch (OutOfMemoryError oom) {
                System.out.println("    [OOM]");
                tAdd[si] = tCon[si] = Double.NaN;
                System.gc();
            }
        }

        record("HashSet.add",      tAdd);
        record("HashSet.contains", tCon);
    }

    // =========================================================================
    // TreeSet
    // =========================================================================
    static void benchmarkTreeSet() {
        System.out.println("--- TreeSet ---");
        double[] tAdd = new double[SIZES.length];
        double[] tCon = new double[SIZES.length];

        for (int si = 0; si < SIZES.length; si++) {
            int n = SIZES[si];
            System.out.printf("  n = %,d%n", n);
            try {
                int bnd    = Math.max(n * 2, 1_000);
                int[] keys = rnd(WARM + TIMED, bnd);

                TreeSet<Integer> base = new TreeSet<>();
                for (int i = 0; i < n; i++) base.add(RNG.nextInt(bnd));

                // ── add  O(log n) ────────────────────────────────────────────
                TreeSet<Integer> sa = new TreeSet<>(base);
                for (int i = 0; i < WARM;  i++) sa.add(keys[i % keys.length]);
                sa = new TreeSet<>(base);
                long t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) sa.add(keys[i % keys.length]);
                tAdd[si] = nsOp(t, TIMED);
                sink += sa.size();

                // ── contains  O(log n) ───────────────────────────────────────
                for (int i = 0; i < WARM;  i++) { if (base.contains(keys[i % keys.length])) sink++; }
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) { if (base.contains(keys[i % keys.length])) sink++; }
                tCon[si] = nsOp(t, TIMED);

            } catch (OutOfMemoryError oom) {
                System.out.println("    [OOM]");
                tAdd[si] = tCon[si] = Double.NaN;
                System.gc();
            }
        }

        record("TreeSet.add",      tAdd);
        record("TreeSet.contains", tCon);
    }

    // =========================================================================
    // HashMap
    // =========================================================================
    static void benchmarkHashMap() {
        System.out.println("--- HashMap ---");
        double[] tPut = new double[SIZES.length];
        double[] tGet = new double[SIZES.length];
        double[] tCK  = new double[SIZES.length];

        for (int si = 0; si < SIZES.length; si++) {
            int n = SIZES[si];
            System.out.printf("  n = %,d%n", n);
            try {
                int bnd    = Math.max(n * 2, 1_000);
                int[] keys = rnd(WARM + TIMED, bnd);
                int[] vals = rnd(WARM + TIMED, bnd);

                HashMap<Integer, Integer> base = new HashMap<>((int)(n / 0.75) + 1);
                for (int i = 0; i < n; i++) base.put(RNG.nextInt(bnd), RNG.nextInt(bnd));

                // ── put  O(1) amortized ──────────────────────────────────────
                HashMap<Integer, Integer> mp = new HashMap<>(base);
                for (int i = 0; i < WARM;  i++) mp.put(keys[i % keys.length], vals[i % vals.length]);
                mp = new HashMap<>(base);
                long t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) mp.put(keys[i % keys.length], vals[i % vals.length]);
                tPut[si] = nsOp(t, TIMED);
                sink += mp.size();

                // ── get  O(1) amortized ──────────────────────────────────────
                for (int i = 0; i < WARM;  i++) { Integer v = base.get(keys[i % keys.length]); if (v != null) sink += v; }
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) { Integer v = base.get(keys[i % keys.length]); if (v != null) sink += v; }
                tGet[si] = nsOp(t, TIMED);

                // ── containsKey  O(1) amortized ──────────────────────────────
                for (int i = 0; i < WARM;  i++) { if (base.containsKey(keys[i % keys.length])) sink++; }
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) { if (base.containsKey(keys[i % keys.length])) sink++; }
                tCK[si] = nsOp(t, TIMED);

            } catch (OutOfMemoryError oom) {
                System.out.println("    [OOM]");
                tPut[si] = tGet[si] = tCK[si] = Double.NaN;
                System.gc();
            }
        }

        record("HashMap.put",         tPut);
        record("HashMap.get",         tGet);
        record("HashMap.containsKey", tCK);
    }

    // =========================================================================
    // TreeMap
    // =========================================================================
    static void benchmarkTreeMap() {
        System.out.println("--- TreeMap ---");
        double[] tPut = new double[SIZES.length];
        double[] tGet = new double[SIZES.length];
        double[] tCK  = new double[SIZES.length];

        for (int si = 0; si < SIZES.length; si++) {
            int n = SIZES[si];
            System.out.printf("  n = %,d%n", n);
            try {
                int bnd    = Math.max(n * 2, 1_000);
                int[] keys = rnd(WARM + TIMED, bnd);
                int[] vals = rnd(WARM + TIMED, bnd);

                TreeMap<Integer, Integer> base = new TreeMap<>();
                for (int i = 0; i < n; i++) base.put(RNG.nextInt(bnd), RNG.nextInt(bnd));

                // ── put  O(log n) ────────────────────────────────────────────
                TreeMap<Integer, Integer> mp = new TreeMap<>(base);
                for (int i = 0; i < WARM;  i++) mp.put(keys[i % keys.length], vals[i % vals.length]);
                mp = new TreeMap<>(base);
                long t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) mp.put(keys[i % keys.length], vals[i % vals.length]);
                tPut[si] = nsOp(t, TIMED);
                sink += mp.size();

                // ── get  O(log n) ────────────────────────────────────────────
                for (int i = 0; i < WARM;  i++) { Integer v = base.get(keys[i % keys.length]); if (v != null) sink += v; }
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) { Integer v = base.get(keys[i % keys.length]); if (v != null) sink += v; }
                tGet[si] = nsOp(t, TIMED);

                // ── containsKey  O(log n) ────────────────────────────────────
                for (int i = 0; i < WARM;  i++) { if (base.containsKey(keys[i % keys.length])) sink++; }
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) { if (base.containsKey(keys[i % keys.length])) sink++; }
                tCK[si] = nsOp(t, TIMED);

            } catch (OutOfMemoryError oom) {
                System.out.println("    [OOM]");
                tPut[si] = tGet[si] = tCK[si] = Double.NaN;
                System.gc();
            }
        }

        record("TreeMap.put",         tPut);
        record("TreeMap.get",         tGet);
        record("TreeMap.containsKey", tCK);
    }

    // =========================================================================
    // PriorityQueue
    // =========================================================================
    static void benchmarkPriorityQueue() {
        System.out.println("--- PriorityQueue ---");
        double[] tOffer = new double[SIZES.length];
        double[] tPoll  = new double[SIZES.length];
        double[] tPeek  = new double[SIZES.length];

        for (int si = 0; si < SIZES.length; si++) {
            int n = SIZES[si];
            System.out.printf("  n = %,d%n", n);
            try {
                int bnd    = Math.max(n * 2, 1_000);
                int[] keys = rnd(WARM + TIMED, bnd);

                // Base PQ with n elements
                PriorityQueue<Integer> base = new PriorityQueue<>(n);
                for (int i = 0; i < n; i++) base.offer(RNG.nextInt(bnd));

                // ── offer  O(log n) ──────────────────────────────────────────
                PriorityQueue<Integer> pqO = new PriorityQueue<>(base);
                for (int i = 0; i < WARM;  i++) pqO.offer(keys[i % keys.length]);
                pqO = new PriorityQueue<>(base);        // reset
                long t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) pqO.offer(keys[i % keys.length]);
                tOffer[si] = nsOp(t, TIMED);
                sink += pqO.size();

                // ── poll  O(log n) ───────────────────────────────────────────
                // Pre-fill with n + WARM + TIMED elements so the queue never empties.
                PriorityQueue<Integer> pqP = new PriorityQueue<>(base);
                for (int i = 0; i < WARM + TIMED; i++) pqP.offer(keys[i % keys.length]);
                // untimed warm-up polls
                for (int i = 0; i < WARM;  i++) { Integer v = pqP.poll(); if (v != null) sink += v; }
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) { Integer v = pqP.poll(); if (v != null) sink += v; }
                tPoll[si] = nsOp(t, TIMED);

                // ── peek  O(1) ───────────────────────────────────────────────
                for (int i = 0; i < WARM;  i++) { Integer v = base.peek(); if (v != null) sink += v; }
                t = System.nanoTime();
                for (int i = 0; i < TIMED; i++) { Integer v = base.peek(); if (v != null) sink += v; }
                tPeek[si] = nsOp(t, TIMED);

            } catch (OutOfMemoryError oom) {
                System.out.println("    [OOM]");
                tOffer[si] = tPoll[si] = tPeek[si] = Double.NaN;
                System.gc();
            }
        }

        record("PriorityQueue.offer", tOffer);
        record("PriorityQueue.poll",  tPoll);
        record("PriorityQueue.peek",  tPeek);
    }

    // =========================================================================
    // Output helpers
    // =========================================================================

    /**
     * Guess Big-O from the geometric-mean ratio of consecutive ns/op values
     * as n grows by 10x.
     *
     *   ratio ~  1.0        -> O(1)
     *   ratio ~  1.1-1.35   -> O(log n)   [log2 grows slowly with 10x size steps]
     *   ratio ~  3.16       -> O(sqrt n)
     *   ratio ~ 10-20       -> O(n)        [cache penalties push ratios above 10]
     *   ratio > 22          -> O(n log n) or worse
     *
     * Note: distinguishing O(n) from O(n log n) empirically is hard because
     * cache-hierarchy penalties inflate O(n) ratios to 12-20 at practical sizes.
     */
    static String guessComplexity(double[] row) {
        double sumLog = 0;
        int cnt = 0;
        for (int i = 1; i < row.length; i++) {
            if (Double.isNaN(row[i]) || Double.isNaN(row[i - 1])
                    || row[i - 1] <= 0 || row[i] <= 0) continue;
            sumLog += Math.log(row[i] / row[i - 1]);
            cnt++;
        }
        if (cnt == 0) return "N/A";
        double r = Math.exp(sumLog / cnt);  // geometric mean of per-decade ratios

        if (r < 1.15) return "O(1)";
        if (r < 2.50) return "O(log n)";
        if (r < 5.50) return "O(sqrt n)";
        if (r < 22.0) return "O(n)";
        return "O(n log n)+";
    }

    /** Format one ns/op cell (right-aligned, 9 chars). */
    static String fmt(double v) {
        if (Double.isNaN(v)) return "      OOM";
        return String.format("%9.2f", v);
    }

    static void printTable() {
        final int LW = 28;  // label column width

        // ── header ──────────────────────────────────────────────────────────
        System.out.printf("%-" + LW + "s", "Operation");
        for (int n : SIZES) System.out.printf(" %9s", "n=" + n);
        System.out.printf("  %-13s%n", "Guessed-O");
        System.out.println("-".repeat(LW + SIZES.length * 10 + 16));

        // ── rows ─────────────────────────────────────────────────────────────
        for (int r = 0; r < rowLabels.size(); r++) {
            System.out.printf("%-" + LW + "s", rowLabels.get(r));
            double[] row = rowData.get(r);
            for (double v : row) System.out.print(" " + fmt(v));
            System.out.printf("  %-13s%n", guessComplexity(row));
        }

        System.out.println("\n(all values are nanoseconds per operation)");
        System.out.println("(scaled-down op counts are used for O(n)-per-call operations;\n" +
                           " ns/op is still correct — only the sample count differs)\n" +
                           "(ratios between adjacent sizes × 10 are used to guess Big-O;\n" +
                           " cache effects can push O(n) ratios above 10, up to ~20)");
    }

    static void writeCSV(String filename) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            // header row
            pw.print("Operation");
            for (int n : SIZES) pw.print(",n=" + n);
            pw.println(",Guessed-O");

            // data rows
            for (int r = 0; r < rowLabels.size(); r++) {
                pw.print(rowLabels.get(r));
                double[] row = rowData.get(r);
                for (double v : row) {
                    pw.print(",");
                    if (Double.isNaN(v)) pw.print("OOM");
                    else                 pw.printf("%.3f", v);
                }
                pw.print("," + guessComplexity(row));
                pw.println();
            }
        }
        System.out.println("CSV written → " + filename);
    }
}

