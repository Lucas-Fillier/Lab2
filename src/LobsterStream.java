import java.io.*;
import java.lang.management.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * LobsterStream  —  STARTER CODE (you complete the parts marked TODO).
 *
 * Generates a LOBSTER-like stream of limit-order-book events ENTIRELY IN MEMORY and
 * feeds them into Java Collections that form an order book. Nothing is written to
 * disk. Each event object is created, applied to the book, and then immediately
 * discarded (it becomes eligible for garbage collection at once). Only the order-book
 * STATE accumulates in memory.
 *
 * This is how you "process 50 GB of data" without ever storing it: the throughput of
 * generated-and-consumed events is effectively unbounded, while the LIVE memory is just
 * the book. You drive the book until its live data reaches your target, measuring as
 * you go. Generating on the fly (never reading a file) also keeps timing honest: a disk
 * read inside a measured loop would swamp the operation you are trying to measure.
 *
 * The collections used here are exactly the framework structures you are studying:
 *    TreeMap     — each side of the book, keyed by price (kept sorted)      O(log n) per op
 *    ArrayDeque  — the FIFO queue of orders resting at a single price       O(1) at the ends
 *    HashMap     — order id -> order, so a cancel finds its order fast      O(1) average
 *
 * Run it:
 *    javac LobsterStream.java
 *    java -Xmx2g LobsterStream          // default target = 1.5 GB
 *    java -Xmx4g LobsterStream 3        // target = 3 GB, larger book for more checkpoints
 *
 * Measurement output: scaleC.csv
 */
public class LobsterStream {

    // -----------------------------------------------------------------------
    // Probe (timing-measurement) configuration
    // -----------------------------------------------------------------------
    /** Untimed warm-up operations per operation type inside each probe. */
    static final int PROBE_WARM  = 1_000;
    /** Timed operations per operation type inside each probe. */
    static final int PROBE_TIMED = 10_000;

    /**
     * Book-size checkpoints (resting orders in byId) at which a timing probe fires.
     * Covers three decades so the Big-O slope is visible.
     */
    static final long[] CHECKPOINTS = {
            10_000L, 25_000L, 50_000L, 100_000L, 250_000L,
            500_000L, 1_000_000L, 2_500_000L, 5_000_000L, 10_000_000L
    };

    // ---- one resting order ----
    static final class Order {
        final long id; final long price; int size; final int side;   // side: 1 = buy, -1 = sell
        Order(long id, long price, int size, int side){ this.id=id; this.price=price; this.size=size; this.side=side; }
    }

    // ---- the order book, built from framework collections ----
    final TreeMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(Collections.reverseOrder()); // highest price first
    final TreeMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();                            // lowest price first
    final HashMap<Long, Order> byId = new HashMap<>();    // id -> order, for fast cancels
    final ArrayList<Long> liveIds = new ArrayList<>();    // ids available to cancel

    long nextId = 1;
    long mid = 100_00;                                    // mid price in cents ($100.00)

    TreeMap<Long, ArrayDeque<Order>> side(int s){ return s == 1 ? bids : asks; }

    // ---- apply a NEW limit order (provided, fully working) ----
    void submit(int side, long price, int size){
        Order o = new Order(nextId++, price, size, side);
        side(side).computeIfAbsent(price, k -> new ArrayDeque<>()).addLast(o);  // price-time priority
        byId.put(o.id, o);
        liveIds.add(o.id);
    }

    // ---- cancel a resting order by id (provided, fully working) ----
    void cancel(long id){
        Order o = byId.remove(id);
        if (o == null) return;                            // already gone (e.g. executed)
        ArrayDeque<Order> q = side(o.side).get(o.price);
        if (q != null){ q.remove(o); if (q.isEmpty()) side(o.side).remove(o.price); }
    }

    // ====================================================================
    // TODO 1 (your coding task): matching / execution.
    // A marketable order arrives on `aggressorSide` and consumes `size` from the BEST
    // prices of the opposite book, honouring price-time priority (FIFO within a level).
    // Walk side(-aggressorSide) from its first entry, take from the head order of each
    // level, reduce or remove filled orders, drop emptied price levels, and remember to
    // remove fully-filled orders from byId. Stop when `size` is exhausted or the book
    // is empty. Write this yourself; this is part of the assessed coding.
    // ====================================================================
    void execute(int aggressorSide, int size){
        // TODO: implement the sweep described above.
    }

    // ---- generate ONE event on the fly, apply it, then let it be discarded ----
    void step(ThreadLocalRandom rng){
        mid += rng.nextInt(-3, 4);                        // slow random walk of the mid price
        double r = rng.nextDouble();
        if (r < 0.62 || liveIds.isEmpty()){               // submit (biased high so the book GROWS to target)
            int side  = rng.nextBoolean() ? 1 : -1;
            int depth = 0; while (rng.nextDouble() > 0.40 && depth < 40) depth++;   // most orders near the touch
            long price = side == 1 ? mid - 100 - 100L*depth : mid + 100 + 100L*depth; // wide spread -> orders rest
            int size  = 100 * (1 + (int)(rng.nextDouble() * 4));
            submit(side, price, size);
        } else if (r < 0.95){                             // cancel a random resting order
            int idx = rng.nextInt(liveIds.size());
            long id = liveIds.get(idx);
            liveIds.set(idx, liveIds.get(liveIds.size() - 1));
            liveIds.remove(liveIds.size() - 1);
            cancel(id);
        } else {                                          // a few percent are executions (TODO 1)
            execute(rng.nextBoolean() ? 1 : -1, 100 * (1 + rng.nextInt(5)));
        }
    }

    static long usedBytes(){ Runtime r = Runtime.getRuntime(); return r.totalMemory() - r.freeMemory(); }

    // -----------------------------------------------------------------------
    // Background monitor — prints system vitals once per second
    // -----------------------------------------------------------------------
    /**
     * Starts a daemon thread that wakes every second and prints one line of
     * system diagnostics to stdout:
     *
     *   [monitor] CPU=12.3%  heap=456 MB  RAM free 3210/16384 MB
     *             threads=6   GC 4 runs / 38 ms total
     *
     * Uses com.sun.management.OperatingSystemMXBean for CPU load and physical
     * RAM figures (available on all Oracle/OpenJDK JVMs since Java 8).
     * If that cast fails gracefully the thread still reports the other metrics.
     *
     * The thread is a daemon so it never prevents the JVM from exiting.
     */
    static void startMonitor() {
        Thread t = new Thread(() -> {

            // ---- OS bean (com.sun.management superset for CPU + physical RAM) ----
            com.sun.management.OperatingSystemMXBean osMx = null;
            try {
                osMx = (com.sun.management.OperatingSystemMXBean)
                        ManagementFactory.getOperatingSystemMXBean();
            } catch (ClassCastException ignored) {
                // Non-Oracle JVM: CPU load and physical RAM will show as N/A
            }

            ThreadMXBean        threadMx = ManagementFactory.getThreadMXBean();
            List<GarbageCollectorMXBean> gcBeans =
                    ManagementFactory.getGarbageCollectorMXBeans();

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    break;
                }

                // ---- CPU load ----
                String cpu;
                if (osMx != null) {
                    double pct = osMx.getProcessCpuLoad() * 100.0;
                    cpu = pct < 0 ? "  N/A " : String.format("%5.1f%%", pct);
                } else {
                    cpu = "  N/A ";
                }

                // ---- Heap ----
                long heapMB = usedBytes() / 1_048_576L;

                // ---- Physical RAM ----
                String ram;
                if (osMx != null) {
                    long freeMB  = osMx.getFreeMemorySize()  / 1_048_576L;
                    long totalMB = osMx.getTotalMemorySize() / 1_048_576L;
                    ram = String.format("%,d / %,d MB", freeMB, totalMB);
                } else {
                    ram = "N/A";
                }

                // ---- Live threads ----
                int threads = threadMx.getThreadCount();

                // ---- GC: total collection count + cumulative pause time ----
                long gcCount = 0, gcMs = 0;
                for (GarbageCollectorMXBean gc : gcBeans) {
                    long c = gc.getCollectionCount();
                    long m = gc.getCollectionTime();
                    if (c > 0) gcCount += c;
                    if (m > 0) gcMs   += m;
                }

                System.out.printf(
                    "[monitor] CPU=%-6s  heap=%,5d MB  RAM free %-22s  threads=%-3d  GC %d runs / %d ms%n",
                    cpu, heapMB, ram, threads, gcCount, gcMs);
            }
        }, "monitor");
        t.setDaemon(true);   // never blocks JVM shutdown
        t.start();
    }
    /**
     * Measures submit, cancel, and bids.firstEntry() performance at the current
     * book size, then writes one row to the CSV writer and the console table.
     *
     * Protocol (per operation type):
     *   1. Pre-generate all random parameters BEFORE any timed loop (no I/O inside timing).
     *   2. Run PROBE_WARM untimed warm-up operations so the JIT is hot.
     *   3. Run PROBE_TIMED timed operations; record System.nanoTime() deltas.
     *   4. Clean up: cancel every order submitted for timing so the book returns
     *      to its pre-probe size.  cancel() does not touch liveIds, so any IDs
     *      that submit() added remain in liveIds as harmless stale entries that
     *      step() will drain naturally through no-op cancel attempts.
     *   5. Force GC + sleep before snapshotting the heap so transient garbage
     *      is flushed and the measurement reflects only live book state.
     *
     * @param csv   open PrintWriter for scaleC.csv
     */
    void probe(PrintWriter csv) throws InterruptedException {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        final int W = PROBE_WARM;
        final int T = PROBE_TIMED;

        // ------------------------------------------------------------------
        // Memory snapshot: force GC so transient garbage is collected first.
        // ------------------------------------------------------------------
        System.gc();
        Thread.sleep(100);
        Runtime rt = Runtime.getRuntime();
        long heapUsed   = rt.totalMemory() - rt.freeMemory();
        int  bookSize   = byId.size();
        long bytesPerOrder = bookSize == 0 ? 0 : heapUsed / bookSize;

        // ------------------------------------------------------------------
        // Pre-generate submit parameters (nothing computed inside timed loops).
        // ------------------------------------------------------------------
        int[]  pSide  = new int [W + T];
        long[] pPrice = new long[W + T];
        int[]  pSize  = new int [W + T];
        for (int i = 0; i < W + T; i++) {
            pSide[i]  = rng.nextBoolean() ? 1 : -1;
            int depth = 0;
            while (rng.nextDouble() > 0.40 && depth < 40) depth++;
            pPrice[i] = pSide[i] == 1 ? mid - 100 - 100L * depth
                                       : mid + 100 + 100L * depth;
            pSize[i]  = 100 * (1 + rng.nextInt(4));
        }

        // ------------------------------------------------------------------
        // Submit timing
        //   warm-up: W submits (untimed)
        //   timed  : T submits; IDs are [timedStartId, afterSubmitId)
        // ------------------------------------------------------------------
        long warmStartId = nextId;
        for (int i = 0; i < W; i++) submit(pSide[i], pPrice[i], pSize[i]);

        long timedStartId = nextId;
        long t = System.nanoTime();
        for (int i = W; i < W + T; i++) submit(pSide[i], pPrice[i], pSize[i]);
        double submitNs = (double)(System.nanoTime() - t) / T;

        long afterSubmitId = nextId;

        // ------------------------------------------------------------------
        // Cancel timing
        //   warm-up: cancel the W warm-up orders (untimed)
        //   timed  : cancel the T timed orders
        // Orders are identified by their sequential IDs; cancel() only touches
        // byId and bids/asks — it does NOT remove from liveIds.
        // ------------------------------------------------------------------
        for (long id = warmStartId; id < timedStartId; id++) cancel(id);

        t = System.nanoTime();
        for (long id = timedStartId; id < afterSubmitId; id++) cancel(id);
        double cancelNs = (double)(System.nanoTime() - t) / T;

        // ------------------------------------------------------------------
        // Best-bid lookup timing  (bids.firstEntry())
        //   warm-up: W calls (untimed)
        //   timed  : T calls
        // Accumulate the returned price into priceSink so the JIT cannot
        // treat the calls as dead code.
        // ------------------------------------------------------------------
        long priceSink = 0;
        for (int i = 0; i < W; i++) {
            Map.Entry<Long, ArrayDeque<Order>> e = bids.firstEntry();
            if (e != null) priceSink += e.getKey();
        }
        t = System.nanoTime();
        for (int i = 0; i < T; i++) {
            Map.Entry<Long, ArrayDeque<Order>> e = bids.firstEntry();
            if (e != null) priceSink += e.getKey();
        }
        double peekNs = (double)(System.nanoTime() - t) / T;
        if (priceSink == Long.MIN_VALUE) System.err.print(""); // prevent elimination

        // ------------------------------------------------------------------
        // Write results
        // ------------------------------------------------------------------
        // CSV row (no locale formatting — plain numbers for easy parsing)
        csv.printf("%d,%.2f,%.2f,%.2f,%d%n",
                bookSize, submitNs, cancelNs, peekNs, bytesPerOrder);
        csv.flush();

        // Console row
        System.out.printf("  %,15d  %10.1f  %10.1f  %10.1f  %12d%n",
                bookSize, submitNs, cancelNs, peekNs, bytesPerOrder);
    }

    // -----------------------------------------------------------------------
    // main
    // -----------------------------------------------------------------------
    public static void main(String[] args) throws InterruptedException, IOException {
        double gb     = args.length > 0 ? Double.parseDouble(args[0]) : 1.5;
        long   target = (long)(gb * 1024L * 1024L * 1024L);

        startMonitor();   // daemon thread: prints CPU, heap, RAM, threads, GC every second

        LobsterStream    s   = new LobsterStream();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        System.out.printf("=== LobsterStream Scale Benchmark ===%n");
        System.out.printf("Heap target : %.2f GB%n", gb);
        System.out.printf("Probe ops   : %,d warm-up + %,d timed (per operation type)%n%n",
                PROBE_WARM, PROBE_TIMED);

        // Console table header
        System.out.printf("  %-15s  %10s  %10s  %10s  %12s%n",
                "Resting orders", "Submit ns", "Cancel ns", "BestBid ns", "Bytes/order");
        System.out.println("  " + "-".repeat(65));

        long events = 0;
        long t0     = System.nanoTime();
        int  cpIdx  = 0;   // index into CHECKPOINTS[]

        try (PrintWriter csv = new PrintWriter(new FileWriter("scaleC.csv"))) {

            // CSV header
            csv.println("Resting orders,Submit (ns/op),Cancel (ns/op)," +
                        "Best-bid lookup (ns/op),Bytes per resting order");


            // ----------------------------------------------------------------
            // Main event loop — generates events entirely on the fly.
            // Nothing is stored between iterations; each event object is
            // created inside step(), used, and immediately eligible for GC.
            // ----------------------------------------------------------------
            while (usedBytes() < target) {
                s.step(rng);
                events++;

                // Periodic progress banner (every ~16 M events)
                if ((events & 0xFFFFFF) == 0) {
                    double secs = (System.nanoTime() - t0) / 1e9;
                    System.out.printf("  [%,.1fM events | %.1f s | %,d MB heap | %,d orders]%n",
                            events / 1e6, secs,
                            usedBytes() / 1_048_576L, s.byId.size());
                }

                // Fire a probe every time the book crosses the next checkpoint
                if (cpIdx < CHECKPOINTS.length && s.byId.size() >= CHECKPOINTS[cpIdx]) {
                    s.probe(csv);
                    cpIdx++;
                    // If all checkpoints are done, no need to keep running
                    if (cpIdx >= CHECKPOINTS.length) break;
                }
            }

            // If heap target was hit before all checkpoints, probe the final state
            if (cpIdx < CHECKPOINTS.length && s.byId.size() >= 1_000) {
                System.out.printf("  (heap target reached; probing final book state)%n");
                s.probe(csv);
            }

        } // PrintWriter closed here — all CSV data flushed

        double secs = (System.nanoTime() - t0) / 1e9;
        System.out.printf("%n  Heap used : %,d MB  |  Resting orders : %,d%n",
                usedBytes() / 1_048_576L, s.byId.size());
        System.out.printf("  Events    : %,d  in %.1f s  (%.1f M events/s)%n",
                events, secs, events / 1e6 / secs);
        System.out.println("  Written   : scaleC.csv");
    }
}
