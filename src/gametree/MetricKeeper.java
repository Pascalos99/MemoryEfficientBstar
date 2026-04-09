package gametree;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * This class is intended for use with algorithm testing by keeping 
 * track of metrics used to evaluate different algorithms on game tree 
 * search problems.
 * <p>
 * Three metrics are kept by this class:<br>
 * <ul>
 * <li>Number of Evaluations</li>
 * <li>Number of Expansions</li>
 * <li>Number of Nodes (kept in memory)</li>
 * </ul>
 * These metrics are kept separate from the {@link GameTreeNode} logic 
 * because they are independent from the structure of the tree and depend 
 * not only on the algorithm used, but also on the {@link GameTreeNode} 
 * implementation. Therefore, during exploration of the game tree, calls 
 * of {@link GameTreeNode#children(MetricKeeper...)}, {@link GameTreeNode#lowerbound(MetricKeeper...)}, 
 * and {@link GameTreeNode#upperbound(MetricKeeper...)} may increment the 
 * counters for the provided {@link MetricKeeper} object. The totals for 
 * these metrics can be obtained at any time.
 * <p>
 * The metrics are stored in {@code volatile} fields such that multi-threaded 
 * operation still obtains expected results from the {@link MetricKeeper}.
 * <p>
 * It is good practice for {@link GameTreeNode} implementations to call 
 * {@link #incrementEvaluations()} whenever either {@link IGamePosition#upperbound()} 
 * or {@link IGamePosition#lowerbound()} are called. This is to keep maximum accuracy 
 * in the described metric. Some implementations of {@link IGamePosition} may 
 * compute both bounds at the same time and simply store this metric for when it 
 * is requested. In those cases, the final {@link #evaluations()} metric may be 
 * divided by {@code 2} to account for this. It is also recommended, for this reason, 
 * that {@link GameTreeNode} implementations only ask the same {@link IGamePosition} object 
 * for each of these bounds once. 
 * 
 * @author Pascal Anema
 * @version 1.0
 */
public class MetricKeeper {
	
	public final String name;
	
    private volatile long evaluations;
    private volatile long expansions;
    private volatile long nodes;
    private volatile long maxNodes;
	
	/**
	 * Initialises this object with the specified initial values.
	 * @param numEvaluations initial value for the number of evaluations measured.
	 * @param numExpansions initial value for the number of expansions measured.
	 * @param numNodes initial value for the number of nodes measured.
	 * @param maxNumNodes initial value for the maximum number of nodes measured.
	 */
	public MetricKeeper(String metricName, long numEvaluations, long numExpansions, long numNodes, long maxNumNodes) {
		name = metricName == null ? "" : metricName;
		evaluations = Math.max(numEvaluations, 0);
		expansions  = Math.max(numExpansions, 0);
		nodes = Math.max(numNodes, 0);
		maxNodes = Math.max(nodes, maxNumNodes);
	}
	/**
	 * Initialises this object with the specified initial values.
	 * @param numEvaluations initial value for the number of evaluations measured.
	 * @param numExpansions initial value for the number of expansions measured.
	 * @param numNodes initial value for the number of nodes measured.
	 */
	public MetricKeeper(String metricName, long numEvaluations, long numExpansions, long numNodes) {
		this(metricName, numEvaluations, numExpansions, numNodes, numNodes);
	}
	/**
	 * Initialises this object with initial values equal to the current values of 
	 * the given {@link MetricKeeper} object {@code m}.
	 * @param m Some MetricKeeper object
	 */
	public MetricKeeper(String metricName, MetricKeeper m) {
		this(metricName, m.evaluations, m.expansions, m.nodes, m.maxNodes);
	}
	public MetricKeeper(MetricKeeper m) {
		this(copyName(m.name), m);
	}
	/**
	 * Initialises this object with initial values of {@code 0} for all metrics.
	 */
	public MetricKeeper(String name) {
		this(name, 0, 0, 0, 0);
	}
	/**
	 * Initialises this object with initial values of {@code 0} for all metrics, and name "Metrics".
	 */
	public MetricKeeper() {
		this("Metrics", 0, 0, 0, 0);
	}
	
	/**
	 * Creates a copy of this {@link MetricKeeper} with the same metric values, but no link to 
	 * this object. Measurements made by {@code this} metric keeper will not be registered by the returned 
	 * object, only past measurements are copied over.
	 * @param name the name of the returned copy
	 * @return A new MetricKeeper with the same metric values as this metric keeper.
	 */
	public MetricKeeper copy(String name) {
		return new MetricKeeper(name, this);
	}
	/**
	 * Creates a copy of this {@link MetricKeeper} with the same metric values, but no link to 
	 * this object. Measurements made by {@code this} metric keeper will not be registered by the returned 
	 * object, only past measurements are copied over.
	 * <p>
	 * The name of the returned copy will be the name of the original plus a counter "(1)" or the increment 
	 * of an existing counter, e.g.: "metric (9)" becomes "metric (10)".
	 * @return A new MetricKeeper with the same metric values as this metric keeper.
	 */
	public MetricKeeper copy() {
		return new MetricKeeper(this);
	}
	
	private static String copyName(String n) {
		if (n == null || n == "") return "(1)";
		Pattern p = Pattern.compile("(.*)\\(([\\d]+)\\)");
		var m = p.matcher(n);
		if (m.find())
			return m.replaceFirst("$1("+(Integer.parseInt(m.group(2))+1)+")");
		return n+" (1)";
	}

    /**
     * Increments the number of node evaluations observed by this {@code MetricKeeper} by {@code value}.
     * <p>
     * A node evaluation occurs every time a node has to be evaluated to compute its 
	 * upper- and/or lower-bound value.
	 * @param value the value to increment evaluations by
     */
    public void incrementEvaluations(long value) {
    		if (value < 0) return;
    			evaluations+=value;
    }
    /**
     * Increments the number of node evaluations observed by this {@code MetricKeeper} by one.
     * <p>
     * A node evaluation occurs every time a node has to be evaluated to compute its 
	 * upper- and/or lower-bound value.
     */
    public void incrementEvaluations() {
    		incrementEvaluations(1);
    }
    /**
     * @return The total number of node evaluations observed by this {@code MetricKeeper}.
     */
    public long evaluations() {
        return evaluations;
    }
    
    /**
     * Increments the number of node expansions observed by this {@code MetricKeeper} by {@code value}.
     * <p>
     * A node expansion occurs every time a node's children have to be computed in order to 
     * expand the tree further from that node.
	 * @param value the value to increment expansions by
     */
    public void incrementExpansions(long value) {
    		if (value < 0) return;
    			expansions+=value;
    }
    /**
     * Increments the number of node expansions observed by this {@code MetricKeeper} by one.
     * <p>
     * A node expansion occurs every time a node's children have to be computed in order to 
     * expand the tree further from that node.
     */
    public void incrementExpansions() {
    		incrementExpansions(1);
    }
    /**
     * @return The total number of node expansions observed by this {@code MetricKeeper}.
     */
    public long expansions() {
        return expansions;
    }
    
    /**
     * Increases the number of nodes kept in memory observed by this {@code MetricKeeper} by {@code value}.
     * <p>
     * {@code value} may be negative.
     * <p>
     * A node is kept in memory if there is a path of memory positions from the root of the tree to it.
	 * @param value the value to increment nodes by
     */
    public void adjustNodeCount(long value) {
        nodes = Math.max(nodes + value, 0);
        maxNodes = Math.max(nodes, maxNodes);
    }
    public void setNodeCount(long value) {
    		nodes = Math.max(0, value);
    		maxNodes = Math.max(nodes, maxNodes);
    }
    /**
     * @return The total number of nodes kept in memory observed by this {@code MetricKeeper}.
     */
    public long nodes() {
        return nodes;
    }
    /**
     * @return The maximum number of nodes kept in memory observed by this {@code MetricKeeper}.
     */
    public long maxObservedNodes() {
    		return maxNodes;
    }
    
    public String describe() {
    		return String.format("%s:\n\tobserved expansions: %d\n\tobserved evaluations: %d\n\tnodes in memory: %d (max: %d)\n", name==null||name==""?"Metrics":name, expansions, evaluations/2, nodes, maxNodes);
    }
    
    //
    
    /**
	 * Updates the given {@link MetricKeeper} objects with the supplied values to increment 
	 * the evaluations and expansions counts by. This will increment the counts of all metrics 
	 * by at least {@code 0}, so negative values are ignored.
	 * @param evaluationsIncrement the value with which to increment the number of evaluations
	 * @param expansionsIncrement the value with which to increment the number of expansions
	 * @param metrics the {@link MetricKeeper} objects to update
	 */
	public static void incrementMetrics(long evaluationsIncrement, long expansionsIncrement, MetricKeeper... metrics) {
		if (evaluationsIncrement <= 0 && expansionsIncrement <= 0) return;
		for (MetricKeeper m : metrics) {
			m.incrementEvaluations(Math.max(0, evaluationsIncrement));
			m.incrementExpansions(Math.max(0, expansionsIncrement));
		}
	}
	/**
	 * Increments the number of evaluations of all {@code MetricKeeper} objects provided by {@code 0} 
	 * or {@code evaluationsIncrement} if it is positive.
	 * @param evaluationsIncrement the value with which to increment the number of evaluations
	 * @param metrics the {@link MetricKeeper} objects to update
	 */
	public static void incrementEvaluations(long evaluationsIncrement, MetricKeeper... metrics) {
		if (evaluationsIncrement <= 0) return;
		for (MetricKeeper m : metrics) m.incrementEvaluations(evaluationsIncrement);
	}
	/**
	 * Increments the number of evaluations of all {@code MetricKeeper} objects provided by {@code 1}.
	 * @param metrics the {@link MetricKeeper} objects to update
	 */
	public static void incrementEvaluations(MetricKeeper... metrics) {
		for (MetricKeeper m : metrics) m.incrementEvaluations();
	}
	/**
	 * Increments the number of expansions of all {@code MetricKeeper} objects provided by {@code 0} 
	 * or {@code expansionsIncrement} if it is positive.
	 * @param expansionsIncrement the value with which to increment the number of expansions
	 * @param metrics the {@link MetricKeeper} objects to update
	 */
	public static void incrementExpansions(long expansionsIncrement, MetricKeeper... metrics) {
		if (expansionsIncrement <= 0) return;
		for (MetricKeeper m : metrics) m.incrementExpansions(expansionsIncrement);
	}
	/**
	 * Increments the number of expansions of all {@code MetricKeeper} objects provided by {@code 1}.
	 * @param metrics the {@link MetricKeeper} objects to update
	 */
	public static void incrementExpansions(MetricKeeper... metrics) {
		for (MetricKeeper m : metrics) m.incrementExpansions();
	}
	/**
	 * Increases the number of nodes kept in memory of all {@code MetricKeeper} objects provided by 
	 * {@code nodesSavedIncrease} which may be negative.
	 * @param nodesSavedIncrease the value with which to increment the number of expansions
	 * @param metrics the {@link MetricKeeper} objects to update
	 */
	public static void adjustNodeCount(long nodesSavedIncrease, MetricKeeper... metrics) {
		for (MetricKeeper m : metrics) m.adjustNodeCount(nodesSavedIncrease);
	}
	
	@SafeVarargs
	public static <T> T[] combineArrays(T[] a, T... b) {
		if (a == null || a.length <= 0) return b;
		if (b.length <= 0) return a;
		int size = 0;
		int[] temp = new int[b.length];
		for (int i=0; i < b.length; i++) {
			contains: {
				for (int j=0; j < a.length; j++)
					if (b[i].equals(a[j]))
						break contains;
				temp[size++] = i;
			}
		}
		int old_size = a.length;
		T[] result = Arrays.copyOf(a, old_size + size);
		for (int i=0; i < size; i++)
			result[old_size + i] = b[temp[i]];
		return result;
	}
	
}
