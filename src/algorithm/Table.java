package algorithm;

import java.util.Arrays;

import algorithm.SearchAlgorithm.Limits;

/**
 * A very minimal transposition table implementation for df-B*.
 */
public class Table {
	public static final int BYTES_PER_ENTRY = 24;
	
	private int keysize;
	private boolean risky_bounds_saving;
	private double[] upper, lower;
	private long[] hash;
	
	private long softCollisions, hardCollisions, timesRetrieved, numStored;
	
	/**
	 * Creates a transposition table with a specified size.
	 * @param keySize Determines the size of this table.
	 * Typically good between 18 and 25, depending on available memory.
	 * For practical reasons, this is capped at 30 and can not be less than 0.
	 */
	public Table(int keySize) {
		if (keySize < 1 || keySize > 30)
			throw new IllegalArgumentException("Table requires a size parameter between 1 and 30 (got "+keySize+")");
		risky_bounds_saving = false;
		keysize = keySize;
		int size = sizeOfTable(keysize);
		upper = new double[size];
		lower = new double[size];
		hash = new long[size];
		Arrays.fill(lower, Double.NaN);
		Arrays.fill(upper, Double.NaN);
		// to check whether an entry is empty, we simply check 'lower[key] >= 0 || lower[key] <= 0'
	}
	
	public static Table getTable(Limits space_limit) {
		int tablesize = getMaxSize(0.5);
		if (space_limit.limitMaxNodes())
			tablesize = Math.min(tablesize, Table.keySizeForLimit((int)(double)space_limit.maxNodes()));
		return new Table(tablesize);
	}
	public static Table getTable() {
		return getTable(Limits.NONE);
	}
	public static Table getTable(int keySize) {
		// may create a smaller table than specified, if there is not enough memory available.
		return new Table(Math.min(getMaxSize(0.95), keySize));
	}
	
	private static int getMaxSize(double percentageOfMemory) {
		long allocated = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();       
		long actualFree = Runtime.getRuntime().maxMemory() - allocated;
		int tablesize = Table.keySizeForByteLimit(Math.round(percentageOfMemory * actualFree));
		return tablesize;
	}
	
	public void clear() {
		Arrays.fill(lower, Double.NaN);
		Arrays.fill(upper, Double.NaN);
		softCollisions = 0;
		hardCollisions = 0;
		timesRetrieved = 0;
		numStored = 0;
	}
	
	public int getKey(long hash) {
		return (int) (hash & ((1 << keysize)-1));
	}
	
	/**
	 * @param hash
	 * @return {@code true} if there is a value present with the same hash as the provided hash, {@code false} otherwise
	 */
	public boolean isPresent(long hash) {
		int key = getKey(hash);
		return hasEntry(key) && hash(key) == hash;
	}
	
	/**
	 * @param key
	 * @return {@code true} if there is a value present at the specified key, {@code false} otherwise
	 */
	public boolean hasEntry(int key) {
		return upper[key] >= 0 || lower[key] <= 0;
	}
	
	public double lower(long hash) {
		return lower[getKey(hash)];
	}
	public double lower(int key) {
		return lower[key];
	}
	
	public double upper(long hash) {
		return upper[getKey(hash)];
	}
	public double upper(int key) {
		return upper[key];
	}
	
	public long hash(int key) {
		return hash[key];
	}
	
	public void countRetrieval() {
		timesRetrieved++;
	}

	public Metrics getMetrics() {
		return new Metrics(softCollisions, hardCollisions, timesRetrieved, numStored, keysize, risky_bounds_saving);
	}
	public static record Metrics(long softCollisions, long hardCollisions, long timesRetrieved, long numStored, int keySize, boolean riskySaving) {}
	
	public void putInTable(long hash, double lower, double upper) {
		int key = getKey(hash);
		if (hasEntry(key)) {
			if (this.hash[key] == hash) {
				double old_lower = this.lower[key];
				double old_upper = this.upper[key];
				if (old_upper >= lower && old_lower <= upper) {
					// likely the same node, store the tightest bounds
					if (risky_bounds_saving) {
						// this may result in erroneous behaviour if hard collisions are common
						this.lower[key] = Math.max(old_lower, lower);
						this.upper[key] = Math.min(old_upper, upper);
						return;
					} else if (upper - lower > old_upper - old_lower)
						// do not change the value if the old value was better
						return;
				} else {
					hardCollisions++;
					// there may be more hard collisions,
					//  but this metric will count the most obvious ones.
				}
			} else {
				softCollisions++;
			}
		} else
			numStored++;
		this.lower[key] = lower;
		this.upper[key] = upper;
		this.hash[key] = hash;
	}
	
	/**
	 * Whether to save the tightest bounds among both entries when a collision leads to
	 *  the same node. If {@code false}, we instead just save the entry with the smallest
	 *  range.
	 * @param set_value defaults to {@code false}
	 */
	public void setExperimentalBoundsSaving(boolean set_value) {
		risky_bounds_saving = set_value;
	}
	
	public int keySize() {
		return keysize;
	}
	
	public static int sizeOfTable(int keySize) {
		return 1 << keySize;
	}
	public static int byteSizeOfTable(int keySize) {
		return sizeOfTable(keySize) * BYTES_PER_ENTRY;
	}
	public static int keySizeForLimit(int entryLimit) {
		return log2fast(entryLimit);
	}
	public static int keySizeForByteLimit(long byteLimit) {
		return log2fast((int)Math.min(byteLimit / BYTES_PER_ENTRY, Integer.MAX_VALUE));
	}
	/**
	 * @param x a positive number
	 * @return The integer logarithm of x (rounded down), or {@code 0} if {@code x < 1}
	 */
	public static int log2fast(int x) {
	    for (int i=0; i < 30; i++) {
	        if (x <= 1) return i;
	        x >>= 1;
	    }
	    return 30;
	}
	
}