/**
 * MyCollections.java
 *
 * Two custom data-structure implementations built from scratch using only a
 * plain Object[] (no java.util.* collections used internally):
 *
 *   MyArrayList<E>   — dynamic array that doubles capacity when full
 *   MyHashMap<K,V>   — separate-chaining hash map that rehashes when
 *                      load factor exceeds 0.75
 *
 * A small self-test main() validates both classes and prints a summary.
 */
public class MyCollections {

    // =========================================================================
    //  MyArrayList<E>
    // =========================================================================

    /**
     * A generic resizable list backed by a plain {@code Object[]} array.
     *
     * <p>Growth policy: when the backing array is full, it is replaced by a new
     * array of exactly <em>double</em> the current capacity (minimum capacity 1
     * so an empty list can accept its first element without a special case).
     *
     * <p>Complexity summary:
     * <pre>
     *   add(E)         — amortised O(1)   (O(n) only on doubling resize)
     *   get(int)       — O(1)
     *   contains(Object) — O(n)  (must scan the array)
     * </pre>
     */
    static class MyArrayList<E> {

        // ---- internal state -------------------------------------------------

        /** The backing store. Elements occupy indices [0, size). */
        private Object[] data;

        /** Number of elements currently held. */
        private int size;

        // ---- constructors ---------------------------------------------------

        /** Creates an empty list with the given initial capacity. */
        MyArrayList(int initialCapacity) {
            if (initialCapacity < 1) initialCapacity = 1;
            data = new Object[initialCapacity];
            size = 0;
        }

        /** Creates an empty list with the default initial capacity (16). */
        MyArrayList() {
            this(16);
        }

        /**
         * Copy constructor — bulk-copies {@code src} via {@code System.arraycopy}.
         * O(n) with a small constant; much faster than element-by-element copying.
         */
        @SuppressWarnings("unchecked")
        MyArrayList(MyArrayList<E> src) {
            data = new Object[Math.max(src.size, 1)];
            System.arraycopy(src.data, 0, data, 0, src.size);
            size = src.size;
        }

        // ---- public API -----------------------------------------------------

        /**
         * Appends {@code element} to the end of the list.
         * Doubles the backing array first if it is already full.
         *
         * @param element the element to add (may be {@code null})
         */
        void add(E element) {
            ensureCapacity();
            data[size++] = element;
        }

        /**
         * Inserts {@code element} at position {@code index}, shifting all
         * elements at {@code index} and above one position to the right.
         * This is an O(n) operation.
         *
         * @param index   the insertion position (0 = front)
         * @param element the element to insert
         * @throws IndexOutOfBoundsException if {@code index < 0 || index > size}
         */
        void add(int index, E element) {
            if (index < 0 || index > size)
                throw new IndexOutOfBoundsException(
                        "Index " + index + " out of bounds for size " + size);
            ensureCapacity();
            // Shift elements [index, size) one position to the right
            System.arraycopy(data, index, data, index + 1, size - index);
            data[index] = element;
            size++;
        }

        /**
         * Returns the element at position {@code index}.
         *
         * @param index zero-based position
         * @return the element stored there
         * @throws IndexOutOfBoundsException if {@code index < 0 || index >= size}
         */
        @SuppressWarnings("unchecked")
        E get(int index) {
            checkIndex(index);
            return (E) data[index];
        }

        /**
         * Returns {@code true} if the list contains an element equal to
         * {@code o} (using {@code equals}, or {@code ==} for {@code null}).
         *
         * @param o the object to search for
         * @return {@code true} if found
         */
        boolean contains(Object o) {
            for (int i = 0; i < size; i++) {
                if (o == null ? data[i] == null : o.equals(data[i])) return true;
            }
            return false;
        }

        /** Returns the number of elements in the list. */
        int size() { return size; }

        /** Returns a human-readable representation of the list contents. */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < size; i++) {
                if (i > 0) sb.append(", ");
                sb.append(data[i]);
            }
            return sb.append(']').toString();
        }

        // ---- private helpers ------------------------------------------------

        /**
         * Doubles the backing array's capacity if {@code size == data.length}.
         * After this call it is guaranteed that {@code size < data.length}.
         */
        private void ensureCapacity() {
            if (size == data.length) {
                // Double the capacity — the core growth policy.
                int newCapacity = data.length * 2;
                Object[] bigger = new Object[newCapacity];
                System.arraycopy(data, 0, bigger, 0, size);
                data = bigger;
            }
        }

        private void checkIndex(int index) {
            if (index < 0 || index >= size)
                throw new IndexOutOfBoundsException(
                        "Index " + index + " out of bounds for size " + size);
        }
    }


    // =========================================================================
    //  MyHashMap<K,V>
    // =========================================================================

    /**
     * A generic hash map that uses <em>separate chaining</em> for collision
     * resolution.  Each bucket in the internal array is the head of a singly
     * linked list of {@link Node} objects.
     *
     * <p>Resize policy: whenever the number of entries exceeds
     * {@code buckets.length × LOAD_FACTOR} the bucket array is replaced with
     * one of exactly <em>double</em> the size and every entry is rehashed into
     * the new table.
     *
     * <p>Complexity summary (average case with a good hash):
     * <pre>
     *   put(K,V)          — O(1) amortised
     *   get(Object)       — O(1) average
     *   containsKey(Object) — O(1) average
     * </pre>
     * Worst case for all three is O(n) when all keys collide to the same bucket.
     */
    static class MyHashMap<K, V> {

        // ---- constants ------------------------------------------------------

        /** The load-factor threshold that triggers a resize. */
        private static final double LOAD_FACTOR = 0.75;

        /** Default number of buckets when none is specified. */
        private static final int DEFAULT_CAPACITY = 16;

        // ---- singly-linked node for separate chaining -----------------------

        /**
         * One key-value pair in the chain.  Each node holds the full hash so
         * we can rehash during a resize without recomputing it.
         */
        private static class Node<K, V> {
            final int   hash;   // cached hashCode of the key
            final K     key;
            V           value;  // mutable so put() can update an existing key
            Node<K, V>  next;   // next node in the same bucket chain

            Node(int hash, K key, V value, Node<K, V> next) {
                this.hash  = hash;
                this.key   = key;
                this.value = value;
                this.next  = next;
            }
        }

        // ---- internal state -------------------------------------------------

        /** The bucket array.  Each element is the head of a chain (or null). */
        @SuppressWarnings("unchecked")
        private Node<K, V>[] buckets = new Node[DEFAULT_CAPACITY];

        /** Total number of key-value mappings currently stored. */
        private int size;

        // ---- constructors ---------------------------------------------------

        /** Creates an empty map with the default initial capacity (16). */
        MyHashMap() {}

        /**
         * Creates an empty map whose bucket array is the smallest power of two
         * that is ≥ {@code initialCapacity}.  Rounding to a power of two is
         * required because {@link #index} uses a bitwise AND, which only gives
         * a uniform distribution when the capacity is a power of two.
         */
        @SuppressWarnings("unchecked")
        MyHashMap(int initialCapacity) {
            if (initialCapacity < 1) initialCapacity = 1;
            // Round up to next power of two so hash & (capacity-1) distributes uniformly
            int cap = 1;
            while (cap < initialCapacity) cap <<= 1;
            buckets = new Node[cap];
        }

        // ---- public API -----------------------------------------------------

        /**
         * Associates {@code value} with {@code key}.  If the key already
         * exists its value is replaced and the old value is returned; otherwise
         * {@code null} is returned.
         *
         * <p>If the load factor exceeds {@link #LOAD_FACTOR} <em>after</em>
         * the insertion, the bucket array is doubled and all entries rehashed.
         *
         * @param key   the key (may be {@code null})
         * @param value the value to associate
         * @return the previous value, or {@code null} if none
         */
        V put(K key, V value) {
            int hash       = hash(key);
            int bucketIdx  = index(hash, buckets.length);
            Node<K, V> cur = buckets[bucketIdx];

            // Walk the chain — update if key already present.
            while (cur != null) {
                if (cur.hash == hash && keysEqual(cur.key, key)) {
                    V old = cur.value;
                    cur.value = value;
                    return old;         // updated in-place; size does not change
                }
                cur = cur.next;
            }

            // Key is new — prepend a fresh node at the head of the chain
            // (O(1); order within a bucket is not guaranteed anyway).
            buckets[bucketIdx] = new Node<>(hash, key, value, buckets[bucketIdx]);
            size++;

            // Resize if load factor exceeded.
            if (size > buckets.length * LOAD_FACTOR) resize();

            return null;
        }

        /**
         * Returns the value associated with {@code key}, or {@code null} if
         * the key is not present.
         *
         * @param key the key to look up
         * @return the mapped value, or {@code null}
         */
        V get(Object key) {
            Node<K, V> node = findNode(key);
            return node == null ? null : node.value;
        }

        /**
         * Returns {@code true} if this map contains a mapping for {@code key}.
         *
         * @param key the key to test
         * @return {@code true} if the key is present
         */
        boolean containsKey(Object key) {
            return findNode(key) != null;
        }

        /** Returns the number of key-value mappings. */
        int size() { return size; }

        /** Returns a human-readable representation (bucket index : chain). */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Node<K, V> head : buckets) {
                for (Node<K, V> n = head; n != null; n = n.next) {
                    if (!first) sb.append(", ");
                    sb.append(n.key).append('=').append(n.value);
                    first = false;
                }
            }
            return sb.append('}').toString();
        }

        // ---- private helpers ------------------------------------------------

        /**
         * Walks the chain at the appropriate bucket and returns the node whose
         * key equals {@code key}, or {@code null} if not found.
         */
        private Node<K, V> findNode(Object key) {
            int hash      = hash(key);
            int idx       = index(hash, buckets.length);
            Node<K, V> n  = buckets[idx];
            while (n != null) {
                if (n.hash == hash && keysEqual(n.key, key)) return n;
                n = n.next;
            }
            return null;
        }

        /**
         * Doubles the bucket array and rehashes every existing entry.
         *
         * <p>Each node is <em>re-linked</em> (not re-allocated) into the new
         * bucket array, so resize is O(capacity + n) time and O(1) extra space
         * beyond the new array itself.
         */
        @SuppressWarnings("unchecked")
        private void resize() {
            int newCap = buckets.length * 2;
            Node<K, V>[] newBuckets = new Node[newCap];

            // Rehash every node from the old table into the new table.
            for (Node<K, V> head : buckets) {
                Node<K, V> cur = head;
                while (cur != null) {
                    Node<K, V> next = cur.next;          // save before relinking
                    int newIdx      = index(cur.hash, newCap);
                    cur.next        = newBuckets[newIdx]; // prepend to new chain
                    newBuckets[newIdx] = cur;
                    cur = next;
                }
            }
            buckets = newBuckets;
        }

        /**
         * Spreads the higher bits of the hash down into the lower bits.
         * This is the same mixing step used by {@code java.util.HashMap} and
         * reduces clustering when keys have poor {@code hashCode()} methods.
         *
         * @param key the key (may be {@code null})
         * @return a mixed 32-bit hash
         */
        private static int hash(Object key) {
            if (key == null) return 0;
            int h = key.hashCode();
            return h ^ (h >>> 16);   // mix upper and lower 16 bits
        }

        /**
         * Maps a (mixed) hash to a bucket index in [0, capacity).
         * Using a bitwise AND instead of {@code %} only works when
         * {@code capacity} is a power of two; our doubling policy guarantees
         * this as long as the initial capacity is also a power of two.
         */
        private static int index(int hash, int capacity) {
            return hash & (capacity - 1);
        }

        /** Null-safe key equality check. */
        private static boolean keysEqual(Object a, Object b) {
            return a == null ? b == null : a.equals(b);
        }
    }


    // =========================================================================
    //  Self-test  main()
    // =========================================================================

    public static void main(String[] args) {
        testMyArrayList();
        testMyHashMap();
    }

    // ---- MyArrayList tests --------------------------------------------------

    private static void testMyArrayList() {
        System.out.println("=== MyArrayList tests ===");

        // --- 1. Basic add / get / size ----------------------------------------
        MyArrayList<Integer> list = new MyArrayList<>(2);   // tiny capacity so resize fires quickly
        for (int i = 0; i < 10; i++) list.add(i);

        assert list.size() == 10 : "size should be 10";
        for (int i = 0; i < 10; i++)
            assert list.get(i) == i : "get(" + i + ") should return " + i;

        System.out.println("  add / get / size  : PASS  " + list);

        // --- 2. Doubling resize -----------------------------------------------
        // Start with capacity 1, add 64 elements — forces 6 doublings.
        MyArrayList<Integer> small = new MyArrayList<>(1);
        for (int i = 0; i < 64; i++) small.add(i);
        assert small.size() == 64 : "size should be 64 after 6 doublings";
        assert small.get(63) == 63 : "last element should be 63";
        System.out.println("  doubling resize   : PASS  (size=" + small.size() + ")");

        // --- 3. contains ---------------------------------------------------------
        assert  list.contains(5)  : "should contain 5";
        assert !list.contains(99) : "should not contain 99";
        assert !list.contains(null);

        MyArrayList<String> strs = new MyArrayList<>();
        strs.add(null);
        strs.add("hello");
        assert  strs.contains(null)    : "should contain null";
        assert  strs.contains("hello") : "should contain 'hello'";
        assert !strs.contains("world") : "should not contain 'world'";
        System.out.println("  contains          : PASS");

        // --- 4. IndexOutOfBoundsException ----------------------------------------
        boolean threw = false;
        try { list.get(-1); } catch (IndexOutOfBoundsException e) { threw = true; }
        assert threw : "negative index should throw";

        threw = false;
        try { list.get(10); } catch (IndexOutOfBoundsException e) { threw = true; }
        assert threw : "index == size should throw";
        System.out.println("  bounds checking   : PASS");

        System.out.println();
    }

    // ---- MyHashMap tests ----------------------------------------------------

    private static void testMyHashMap() {
        System.out.println("=== MyHashMap tests ===");

        // --- 1. Basic put / get / containsKey ------------------------------------
        MyHashMap<String, Integer> map = new MyHashMap<>();
        map.put("one",   1);
        map.put("two",   2);
        map.put("three", 3);

        assert map.size() == 3        : "size should be 3";
        assert map.get("one")   == 1  : "one->1";
        assert map.get("two")   == 2  : "two->2";
        assert map.get("three") == 3  : "three->3";
        assert map.get("four")  == null : "absent key should return null";
        assert  map.containsKey("two")  : "should contain 'two'";
        assert !map.containsKey("four") : "should not contain 'four'";
        System.out.println("  put/get/containsKey : PASS  " + map);

        // --- 2. Update existing key ----------------------------------------------
        Integer old = map.put("two", 22);
        assert old == 2            : "put should return old value 2";
        assert map.get("two") == 22 : "value should be updated to 22";
        assert map.size() == 3     : "size unchanged after update";
        System.out.println("  update existing key : PASS  (two=" + map.get("two") + ")");

        // --- 3. Resize at load factor 0.75 ---------------------------------------
        // Use capacity=4: resize fires after 3rd insertion (3 > 4×0.75 = 3.0... actually after 4th).
        // Let's verify with 100 entries — all must survive a resize.
        MyHashMap<Integer, Integer> big = new MyHashMap<>(4);
        for (int i = 0; i < 100; i++) big.put(i, i * 10);
        assert big.size() == 100 : "size should be 100";
        for (int i = 0; i < 100; i++) {
            assert big.containsKey(i)      : "missing key " + i;
            assert big.get(i) == i * 10    : "wrong value for key " + i;
        }
        System.out.println("  resize (100 entries): PASS  (size=" + big.size() + ")");

        // --- 4. null key ---------------------------------------------------------
        MyHashMap<String, String> nmap = new MyHashMap<>();
        nmap.put(null, "null-value");
        assert  nmap.containsKey(null)     : "null key should be present";
        assert "null-value".equals(nmap.get(null)) : "null key should map to 'null-value'";
        nmap.put(null, "updated");
        assert "updated".equals(nmap.get(null)) : "null key value should update";
        assert nmap.size() == 1            : "size should still be 1";
        System.out.println("  null key            : PASS");

        // --- 5. Absent key returns null -----------------------------------------
        assert big.get(200)         == null : "absent key should return null";
        assert !big.containsKey(200)        : "absent key should not be found";
        System.out.println("  absent key          : PASS");

        System.out.println();
        System.out.println("All assertions passed.");
    }
}

