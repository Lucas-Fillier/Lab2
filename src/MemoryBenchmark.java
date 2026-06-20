import java.io.*;
import java.util.*;

/**
 * MemoryBenchmark
 * ===============
 * Measures the real heap footprint (bytes per element) of eight Java
 * collection classes by bracketing each allocation with forced GC cycles
 * and heap-usage snapshots.
 *
 * Compile:  javac MemoryBenchmark.java
 * Run    :  java -Xmx2g -verbose:gc MemoryBenchmark
 *           (or without -verbose:gc — the flag just lets you see GC events)
 *
 * The -Xmx2g flag is recommended: 3 M boxed Integers plus the collection
 * overhead can exceed the default heap for tree-based structures.
 *
 * Output:  console table  +  memoryB.csv
 */
public class MemoryBenchmark {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------
    static final int    N          = 3_000_000;   // elements per collection
    static final Random RNG        = new Random(42);

    // Column widths for the console table
    static final int CW_STRUCT  = 20;
    static final int CW_ELEMS   = 14;
    static final int CW_MB      = 16;
    static final int CW_BPEL    = 18;

    // -----------------------------------------------------------------------
    // Result holder
    // -----------------------------------------------------------------------
    record Row(String structure, int elements, double heapMB, double bytesPerElem) {}

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------
    public static void main(String[] args) throws InterruptedException, IOException {
        System.out.println("=== Collection Memory Footprint Benchmark ===");
        System.out.printf("Elements per collection : %,d%n%n", N);

        List<Row> rows = measureAll();

        printTable(rows);
        writeCSV(rows, "memoryB.csv");
    }

    // -----------------------------------------------------------------------
    // Measure every collection type
    // -----------------------------------------------------------------------
    static List<Row> measureAll() throws InterruptedException {
        List<Row> rows = new ArrayList<>();

        rows.add(measureArrayList());
        rows.add(measureLinkedList());
        rows.add(measureArrayDeque());
        rows.add(measureHashSet());
        rows.add(measureTreeSet());
        rows.add(measureHashMap());
        rows.add(measureTreeMap());
        rows.add(measurePriorityQueue());

        return rows;
    }

    // -----------------------------------------------------------------------
    // Core measurement template
    // -----------------------------------------------------------------------

    /**
     * Runs the exact seven-step protocol described in the specification:
     *  1. System.gc() + sleep(100)  — flush dead objects
     *  2. Snapshot baseline heap usage
     *  3. Allocate the collection and fill with N random Integers
     *  4. System.gc() + sleep(100)  — flush temporary allocation trash
     *  5. Snapshot new heap usage
     *  6. Compute bytes per element
     *  7. Null out the reference so the next measurement starts clean
     *
     * The collection object is created by the caller-supplied {@code Filler}
     * lambda and returned so this method can null it after snapshotting.
     *
     * @param name   label for the table
     * @param filler lambda that builds and returns the filled collection
     */
    static Row measure(String name, Filler filler) throws InterruptedException {
        Runtime rt = Runtime.getRuntime();

        // ── step 1: force GC before baseline ────────────────────────────────
        System.gc();
        Thread.sleep(100);

        // ── step 2: baseline heap ────────────────────────────────────────────
        long before = rt.totalMemory() - rt.freeMemory();

        // ── step 3: build the collection ─────────────────────────────────────
        System.out.printf("  Building %-20s ...", name);
        Object col = filler.fill();
        System.out.println(" done");

        // ── step 4: force GC after allocation ───────────────────────────────
        System.gc();
        Thread.sleep(100);

        // ── step 5: new heap usage ───────────────────────────────────────────
        long after = rt.totalMemory() - rt.freeMemory();

        // ── step 6: calculate bytes per element ──────────────────────────────
        long   usedBytes    = after - before;
        double heapMB       = usedBytes / (1024.0 * 1024.0);
        double bytesPerElem = (double) usedBytes / N;

        // ── step 7: null out so GC can reclaim before next measurement ───────
        col = null;
        System.gc();
        Thread.sleep(100);

        System.out.printf("    heap delta = %+.2f MB  (%.1f bytes/element)%n",
                heapMB, bytesPerElem);
        return new Row(name, N, heapMB, bytesPerElem);
    }

    /** Functional interface: builds a collection filled with N random Integers. */
    @FunctionalInterface
    interface Filler {
        Object fill();
    }

    // -----------------------------------------------------------------------
    // Per-structure measurement methods
    // -----------------------------------------------------------------------

    static Row measureArrayList() throws InterruptedException {
        return measure("ArrayList", () -> {
            ArrayList<Integer> list = new ArrayList<>(N);
            for (int i = 0; i < N; i++) list.add(RNG.nextInt());
            return list;
        });
    }

    static Row measureLinkedList() throws InterruptedException {
        return measure("LinkedList", () -> {
            LinkedList<Integer> list = new LinkedList<>();
            for (int i = 0; i < N; i++) list.add(RNG.nextInt());
            return list;
        });
    }

    static Row measureArrayDeque() throws InterruptedException {
        return measure("ArrayDeque", () -> {
            ArrayDeque<Integer> deque = new ArrayDeque<>(N);
            for (int i = 0; i < N; i++) deque.addLast(RNG.nextInt());
            return deque;
        });
    }

    static Row measureHashSet() throws InterruptedException {
        return measure("HashSet", () -> {
            // Pre-size to avoid rehashing skewing the measurement
            HashSet<Integer> set = new HashSet<>((int)(N / 0.75) + 1, 0.75f);
            for (int i = 0; i < N; i++) set.add(RNG.nextInt());
            return set;
        });
    }

    static Row measureTreeSet() throws InterruptedException {
        return measure("TreeSet", () -> {
            TreeSet<Integer> set = new TreeSet<>();
            for (int i = 0; i < N; i++) set.add(RNG.nextInt());
            return set;
        });
    }

    static Row measureHashMap() throws InterruptedException {
        return measure("HashMap", () -> {
            HashMap<Integer, Integer> map = new HashMap<>((int)(N / 0.75) + 1, 0.75f);
            for (int i = 0; i < N; i++) map.put(RNG.nextInt(), RNG.nextInt());
            return map;
        });
    }

    static Row measureTreeMap() throws InterruptedException {
        return measure("TreeMap", () -> {
            TreeMap<Integer, Integer> map = new TreeMap<>();
            for (int i = 0; i < N; i++) map.put(RNG.nextInt(), RNG.nextInt());
            return map;
        });
    }

    static Row measurePriorityQueue() throws InterruptedException {
        return measure("PriorityQueue", () -> {
            PriorityQueue<Integer> pq = new PriorityQueue<>(N);
            for (int i = 0; i < N; i++) pq.offer(RNG.nextInt());
            return pq;
        });
    }

    // -----------------------------------------------------------------------
    // Output
    // -----------------------------------------------------------------------

    static void printTable(List<Row> rows) {
        String header = String.format(
                "%-" + CW_STRUCT + "s %" + CW_ELEMS + "s %" + CW_MB + "s %" + CW_BPEL + "s",
                "Structure", "Elements held", "Heap used (MB)", "Bytes per element");
        String sep = "-".repeat(header.length());

        System.out.println();
        System.out.println(header);
        System.out.println(sep);

        for (Row r : rows) {
            System.out.printf(
                    "%-" + CW_STRUCT + "s %," + CW_ELEMS + "d %" + CW_MB + ".2f %" + CW_BPEL + ".1f%n",
                    r.structure(), r.elements(), r.heapMB(), r.bytesPerElem());
        }

        System.out.println(sep);
        System.out.println();
        System.out.println("Notes:");
        System.out.println("  * HashMap / HashSet bytes-per-element include both key and value Integer");
        System.out.println("    objects plus the Entry node and the backing array slot.");
        System.out.println("  * LinkedList overhead comes from the doubly-linked Node objects");
        System.out.println("    (prev + next + item references per node).");
        System.out.println("  * TreeSet/TreeMap overhead comes from the red-black-tree Entry nodes");
        System.out.println("    (left + right + parent + color field per node).");
        System.out.println("  * ArrayList and ArrayDeque store only object references in their");
        System.out.println("    backing array; per-element cost is dominated by the boxed Integer.");
        System.out.println("  * System.gc() is advisory; results may vary across JVM runs.");
    }

    static void writeCSV(List<Row> rows, String filename) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            // Header — exactly as specified
            pw.println("Structure,Elements held,Heap used (MB),Bytes per element");

            for (Row r : rows) {
                pw.printf("%s,%d,%.2f,%.1f%n",
                        r.structure(), r.elements(), r.heapMB(), r.bytesPerElem());
            }
        }
        System.out.println("CSV written -> " + filename);
    }
}

