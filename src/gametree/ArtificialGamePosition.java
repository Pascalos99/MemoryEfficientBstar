package gametree;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Represents a game position in an artificial game tree. This class models both adversarial 
 * and non-adversarial game trees with a fixed branching factor and integer valued nodes.
 * Each node is uniquely identified by its {@link #name}, which is 
 * derived from its position in the tree.
 * 
 * <p>
 * The tree is characterised by a {@link Settings} object, which defines parameters such as 
 * the branching factor ({@link Settings#width}), growth factor ({@link Settings#growth_factor}), and initial 
 * range ({@link Settings#initial_range}). Nodes are generated deterministically based on these 
 * settings and a pseudo-random number generator seed, ensuring reproducibility of the tree structure.
 * 
 * <p>
 * This class is designed for use in scenarios where deterministic and reproducible game trees 
 * are required, such as in testing game tree search algorithms or simulations.
 * 
 * @author Pascal Anema
 * @version 1.0
 */
public class ArtificialGamePosition implements IGamePosition<ArtificialGamePosition>, Serializable {
	
	/**
	 * Change whenever major changes to class structure are made.
	 * @see 5.6.1 from <a href=https://docs.oracle.com/javase/6/docs/platform/serialization/spec/version.html#6678>docs.oracle.com</a>
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * A class that stores the settings for a {@link ArtificialGamePosition} tree. All nodes 
	 * in the tree hold a reference to the same {@link Settings} object that was used to 
	 * initialise the tree.
	 */
	public static class Settings implements Serializable {
		
		/**
		 * Change whenever major changes to class structure are made.
		 * @see 5.6.1 from <a href=https://docs.oracle.com/javase/6/docs/platform/serialization/spec/version.html#6678>docs.oracle.com</a>
		 */
		private static final long serialVersionUID = 1L;
		
		/**
		 * Whether or not this tree is an adversarial game tree. If {@code true}, nodes alternate between 
		 * maximising and minimising nodes (starting at maximising). If {@code false}, all nodes are 
		 * maximising nodes. Adversarial game trees depict games with two opposing players. Non-adversarial 
		 * game trees depict games with only one player (or multiple co-operating players).
		 */
		public final boolean adversarial;
		/**
		 * The pseudo-random number generator seed of the first node in the tree (<b>S₀</b>). Dictates the 
		 * unique generation of the entire tree. A tree with the same settings, including the 
		 * same initial seed, will always have the same structure no matter the order of 
		 * expansion/exploration.
		 */
		public final long initial_seed;
		/**
		 * Parameter <b>b</b>, the branching factor or width of the tree, {@code >=2}, denotes 
		 * the number of children nodes that each non-terminal node in the game-tree has.
		 */
		public final int width;
		/**
		 * Initial range <b>R</b> at the root node of this tree. The root node will start with lower- and upper-bounds 
		 * {@code 0} and {@code initial_range}. Has to be greater than {@code 0} for the tree to be non-trivial.
		 */
		public final long initial_range;
		/**
		 * Parameter <b>k</b>, number of values, {@code >=2}, denotes the number of values {@code k} that are 
		 * generated to build the upper and lower bounds of each child node. To generate children 
		 * bounds, we randomly sample {@code k} values from a uniform distribution between some
		 * outer bound determined by the parent and the growth factor. The upper bound is taken to 
		 * be the maximum of these {@code k} values, whereas the lower bound is the minimum.
		 */
		public final int num_alts;
		/**
		 * Parameter <b>g</b>, growth factor, always {@code > 0}, typically {@code >= 1}, dictates how much child 
		 * bounds may maximally diverge from their parents' bounds in a single generation, as a factor 
		 * of the parent's range: {@code |Upper - Lower|}. For {@code g=1}, all children values are bound by  
		 * the optimistic and pessimistic values of their parents. For {@code g>1} children may have 
		 * bounds greater than their parents. For {@code g < 1} children bounds are always smaller 
		 * than their parents' bounds.
		 */
		public final double growth_factor;
		/**
		 * Parameter <b>r</b>, relevance chance, only relevant when the growth factor {@code g} 
		 * is greater than 1. Enforces that, for a fraction {@code r} of child nodes of each parent, 
		 * the child bound overlaps with the parent bound. Such that either the upper or 
		 * lower bound is forced to be generated within the parent bound, depending on whether 
		 * the node is minimising or maximising respectively.
		 */
		public final double force_relevance_chance;
		/**
		 * Not a tree generation parameter, but an implementation-specific one. If {@code true}, only 
		 * the last 64 bits of the names of nodes are stored. This has no effect on the regular functions 
		 * of the tree, nor on its distribution. But it disables random access for this tree, as well as losing
		 * information of node heritage after only a short number of ancestors (depending on the branching factor
		 * {@link #width}). The benefit is significantly reduced memory usage, especially for very deep trees. 
		 * If {@code false}, nodes are stored as usual.
		 */
		public final boolean memory_saver;
		
		/**
		 * Initialises the artificial game tree settings with the provided parameter values.
		 * @param adversarial				{@link #adversarial}, denotes whether this tree is an adversarial tree or not
		 * @param initial_seed 				{@link #initial_seed}, denotes a unique tree generation sequence
		 * @param width 					{@link #width}, has to be greater than or equal to 2
		 * @param initial_range				{@link #initial_range}, has to be greater than 0
		 * @param num_alts 					{@link #num_alts}, has to be greater than or equal to 2
		 * @param growth_factor 			{@link #growth_factor}, has to be greater than 0
		 * @param force_relevance_chance 	{@link #force_relevance_chance}, has to be greater than or equal to 0 and smaller than or equal to 1
		 * @param memory_saver				{@link #memory_saver}, denotes whether the implementation-specific memory saver is used
		 */
		public Settings(boolean adversarial, long initial_seed, int width, long initial_range, int num_alts, double growth_factor, double force_relevance_chance, boolean memory_saver) {
			
			if (width < 2)
				throw new IllegalArgumentException("Tree width must be 2 or greater");
			if (initial_range <= 0)
				throw new IllegalArgumentException("Initial tree range cannot be 0 or negative");
			if (num_alts < 2)
				throw new IllegalArgumentException("Distribution parameter k must be 2 or greater");
			if (growth_factor <= 0)
				throw new IllegalArgumentException("Growth factor cannot be 0 or negative");
			if (growth_factor > 1 && (force_relevance_chance < 0 || force_relevance_chance > 1))
				throw new IllegalArgumentException("Force-relevance chance must be between 0 (inclusive) and 1 (inclusive) if the growth-factor is greater than 1");
			
			this.adversarial			= adversarial;
			this.initial_seed 			= initial_seed;
			this.width 					= width;
			this.initial_range 			= initial_range;
			this.num_alts 				= num_alts;
			this.growth_factor 			= growth_factor;
			this.force_relevance_chance = force_relevance_chance;
			this.memory_saver 			= memory_saver;
		}
		/**
		 * Initialises the artificial game tree settings with the provided parameter values.
		 * <p>
		 * This constructor uses a default value of {@code true} for the {@link #memory_saver} parameter.
		 * @param adversarial
		 * @param initial_seed
		 * @param width
		 * @param initial_range
		 * @param num_alts
		 * @param growth_factor
		 * @param force_relevance_chance
		 */
		public Settings(boolean adversarial, long initial_seed, int width, long initial_range, int num_alts, double growth_factor, double force_relevance_chance) {
			this(adversarial, initial_seed, width, initial_range, num_alts, growth_factor, force_relevance_chance, true);
		}
		
		/**
		 * Creates a new {@link ArtificialGamePosition} object representing a unique tree defined by the parameters set in this {@link Settings} object.
		 * @return A new artificial game tree based on the parameters of this object.
		 */
		public ArtificialGamePosition getTree() {
			return new ArtificialGamePosition(this);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append((adversarial? "2":"1")+"player|");
			sb.append("R="+initial_range+"|");
			sb.append("w="+width+"|");
			sb.append("k="+num_alts+"|");
			sb.append(String.format(Locale.CANADA, "g=%.2f|",growth_factor));
			sb.append(String.format(Locale.CANADA, "r=%.2f|",force_relevance_chance));
			sb.append(initial_seed);
			return sb.toString();
		}
	}
	/**
	 * The {@link Settings} object which characterises the tree this node is from.
	 */
	public final Settings settings;
	/**
	 * Name of this node which denotes its position in the tree. Nodes are named after their 
	 * parent node and their position relative to their sibling nodes. The root node is always named {@code 0}.
	 * <p>
	 * For example, a node named {@code 100} in a tree 
	 * with {@link Settings#width} {@code == 5} has children named {@code 501, 502, 503, 504,} and {@code 505}.
	 */
	public final BigInteger name;
	/**
	 * The last 64 bits of {@link #name}.
	 */
	private final long name64;
	/**
	 * {@code true} if this node is a maximising node. {@code false} if this node is a minimising node.
	 */
	private final boolean maximising;
	/**
	 * {@code true} if the bounds for this node have already been computed. {@code false} if not. 
	 * getters for {@link #upper}, {@link #lower}, and {@link #depth} will call the 
	 * {@link #setBounds()} method if {@link #bounds_set} is {@code false}.
	 */
	private boolean bounds_set;
	/**
	 * The lower-bound value of this node as per the description of {@link IGamePosition#lowerbound()}.
	 */
	private long lower;
	/**
	 * The upper-bound value of this node as per the description of {@link IGamePosition#upperbound()}.
	 */
	private long upper;
	/**
	 * The depth of this node, where the root node is depth {@code 0} and the children of a node have a relative depth of {@code +1}.
	 */
	private long depth;
	
	/**
	 * Gives the root node of the tree characterised by the given {@link Settings} object.
	 * @param settings the {@link Settings} object which characterises the tree
	 */
	public ArtificialGamePosition(Settings settings) {
		// The root node has a range of [0, R]
		// The root node is a maximising node
		this(settings, BigInteger.ZERO, 0l, 0l, 0l, settings.initial_range, true, true);
	}
	
	/**
	 * Gives the root node of the tree characterised by the given parameters.
	 * @param adversarial				{@link Settings#adversarial}, denotes whether this tree is an adversarial tree or not
	 * @param initial_seed 				{@link Settings#initial_seed}, denotes a unique tree generation sequence
	 * @param width 					{@link Settings#width}, has to be greater than or equal to 2
	 * @param initial_range				{@link Settings#initial_range}, has to be greater than 0
	 * @param num_alts 					{@link Settings#num_alts}, has to be greater than or equal to 2
	 * @param growth_factor 			{@link Settings#growth_factor}, has to be greater than 0
	 * @param force_relevance_chance 	{@link Settings#force_relevance_chance}, has to be greater than or equal to 0 and smaller than or equal to 1
	 */
	public ArtificialGamePosition(boolean adversarial, long initial_seed, int width, long initial_range, int num_alts, double growth_factor, double force_relevance_chance) {
		this(new Settings(adversarial, initial_seed, width, initial_range, num_alts, growth_factor, force_relevance_chance));
	}
	
	private ArtificialGamePosition(Settings settings, BigInteger name, long name64, long depth, long lower, long upper, boolean maximising, boolean bounds_set) {
		
		if (settings == null)
			throw new NullPointerException("Settings may not be null");
		if (!settings.memory_saver && name.signum() < 0)
			throw new IllegalArgumentException("Node name should be 0 or positive");
		if (depth < 0)
			throw new IllegalArgumentException("Depth should be 0 or positive");
		if (lower > upper)
			throw new IllegalArgumentException("Node lower bound should be lower than or equal to node upper bound");
		
		this.settings = settings;
		this.name = name;
		this.name64 = name64;
		this.depth = depth;
		this.lower = lower;
		this.upper = upper;
		this.maximising = maximising;
		this.bounds_set = bounds_set;
	}

	@Override
	public List<ArtificialGamePosition> next() {
		// recursively set the bounds of the parent node if they are not present yet:
		if (!bounds_set) setBounds();
		// terminal nodes with a range of 0 have no offspring:
		if (lower == upper) {
			return List.of();
		}
		// set the seed of this parent node. Is always the same, no matter in what order
		// the tree has been expanded. Different trees with different initial seeds will
		// use different seeds at nodes with the same name:
		long seed = name64 + (name64 + 1) * settings.initial_seed;
		Random r = new Random(seed);
		r.setSeed(r.nextLong());
		// this flag is `true` at the end if any child has both end-points within the parent's range.
		// If this is `false` at the end, we need to adjust at least one child to ensure the parent's
		// range does not widen, and only tightens - as per the requirements for the evaluation function.
		boolean any_child_within_parent_bound = false;
		// settings.width is the branching factor, and how many children this node should have:
		ArrayList<ArtificialGamePosition> children = new ArrayList<>(settings.width);
		BigInteger base = settings.memory_saver ? null : name.multiply(BigInteger.valueOf(settings.width));
		long base64 = name64 * settings.width;
		for (int i=0; i < settings.width; i++) {
			long L = lower, U = safeSum(upper,1);
			// apply growth factor:
			if (settings.growth_factor != 1) {
				if (maximising)
					// when maximising, node ranges cannot exceed their parent range above 'upper'.
					// this is because such child nodes may cause the parent's range to widen, 
					// which is not permitted in the definition of the evaluation function for B*.
					// But further lowering the 'lower' part of child nodes is permitted, as long
					// as *some* child of the parent node has a 'lower' value >= that of the parent
					L = safeSum(L, -getDelta(r, lower, upper));
				else
					// similarly to above, minimising parents can have children with greater 'upper'
					// values than their parents, but not with lower 'lower' values. The delta for
					// the growth factor of this tree is therefore applied in the 'upper' direction
					// for maximising children of minimising parent nodes.
					U = safeSum(U, getDelta(r, lower, upper));
			}
			long[] values;
			// apply force relevance chance:
			if (	settings.growth_factor > 1 && 
					settings.force_relevance_chance > 0 && 
					settings.force_relevance_chance > r.nextDouble()
				) {
				values = new long[settings.num_alts + 1];
				// a value within [lower, upper] will force the range of this child
				// to be relevant for the sub-tree at the parent node
				values[settings.num_alts] = r.nextLong(lower, safeSum(upper,1));
			} else
				// not adding any additional values to the array, to keep the distribution as described
				values = new long[settings.num_alts];
			
			// generate num_alts values for the default distribution:
			for (int j=0; j < settings.num_alts; j++)
				values[j] = L>=U? L : r.nextLong(L, U);
			
			// these are the generated lower and upper bounds for this child
			long child_lower = minV(values), child_upper = maxV(values);
			
			
			// TEMPORARY
			// apply Palay improvement:
//			if (safeSum(child_upper, -child_lower) <= 50) {
//				var middle = safeSum(child_lower, safeSum(child_upper, -child_lower)/2);
//				child_lower = child_upper = middle;
//			}
			// END TEMP
			
			// adjusting the flag to test if any child is fully within parent bounds
			any_child_within_parent_bound |= 
					child_lower >= lower && child_lower <= upper &&
					child_upper >= lower && child_upper <= upper;
			
			
			BigInteger childName = settings.memory_saver ? null : base.add(BigInteger.valueOf(i + 1));
			long childName64 = base64 + (i + 1);
			children.add(new ArtificialGamePosition(
					settings,
					childName,
					childName64,
					depth+1,
					child_lower, 
					child_upper, 
					// child is maximising if the tree is non-adversarial OR the parent is minimising:
					!settings.adversarial || !maximising,
					// "bounds are set" is now true:
					true
				));
		}
		// if no child has its bounds entirely within the parent, a random child is selected
		// to be generated fully within the parent bounds.
		// This is done to ensure parent bounds get tighter as the tree is further explored.
		if (!any_child_within_parent_bound) {
			int ci = r.nextInt(settings.width);
			long[] values = new long[settings.num_alts];
			for (int j=0; j < values.length; j++)
				values[j] = r.nextLong(lower, safeSum(upper,1));
			children.get(ci).lower = minV(values);
			children.get(ci).upper = maxV(values);
		}
		
		return children;
	}

	@Override
	public double upperbound() {
		if (!bounds_set) setBounds();
		return upper;
	}

	@Override
	public double lowerbound() {
		if (!bounds_set) setBounds();
		return lower;
	}

	@Override
	public boolean maximising() {
		return maximising;
	}

	@Override
	public long hash() {
		// TODO implement more variations for hashing
		long seed = name64 + (name64 + 1) * settings.initial_seed;
		return new Random(seed).nextLong();
		// this implementation returns the 64-bit seed used to initialise the values of the 
		//  children of this node. some other implementations could instead introduce artificial 
		//  key collisions or other complications that might effect transposition table effectiveness.
	}
	
	/**
	 * @return The parent of this node. This is required for random access of 
	 * {@link ArtificialGamePosition} objects.
	 */
	public ArtificialGamePosition parent() {
		// name == 0 refers to the ROOT node, so we return null as it has no parent.
		if (name != null && name.signum() == 0) return null;
		BigInteger p = parentname(); // this will throw an exception if settings.memory_saver is 'true'
		long parentName64 = p.longValue();
		return new ArtificialGamePosition(settings, p, parentName64, 0, 0, 0,
				// parent is maximising if the tree is non-adversarial OR the child is minimising:
				!settings.adversarial || !maximising,
				// bounds are not yet set:
				false);
	}
	
	/**
	 * @return The name of the parent of this node.
	 */
	private BigInteger parentname() {
		if (settings.memory_saver)
			throw new UnsupportedOperationException("Random access is not compatible with memory saver, disable memory saver and try again.");
		// according to the formula: "parent_name = (child_name - 1) / width"
		// derived from the formula: "child_name = parent_name * width + i + 1:"
		return name.subtract(BigInteger.ONE).divide(BigInteger.valueOf(settings.width));
	}
	
	/**
	 * Recursively calls {@link #children()} to compute the {@link #lower}, 
	 * {@link #upper}, and {@link #depth} 
	 * values for this node. Recursive computation occurs only if this node is obtained 
	 * through the random access constructor {@link #ArtificialGameTreeNode(Settings, BigInteger)}. 
	 * In that case, this method has to back-up to the root to compute the values for this node. 
	 * In all other cases, this method does nothing but ensure that the values have been computed.
	 */
	private void setBounds() {
		if (bounds_set) return;
		// "if this node is the ROOT node":
		if (name != null && name.signum() == 0) {
		    // then the bounds are set to [0, R]
			upper = settings.initial_range;
			lower = 0;
			bounds_set = true;
			return;
		}
		// The following recursive call is only relevant when using RANDOM-ACCESS to search the tree.
		//  -- while searching the tree like normal (generating children starting from root and so on) this will not be executed.
		ArtificialGamePosition parent = parent(); // this will throw an exception if settings.memory_saver is 'true'
		List<ArtificialGamePosition> siblings = parent.next();
		if (siblings.size() <= 0) {
			// this occurs only when we randomly access a node which does not actually exist:
			// * the child of a terminal node
			// in this case, we just assume that the node 'exists', and has the same values as its parent
			// by extension, this node is also a terminal node.
			lower = parent.lower;
			upper = parent.upper;
			depth = parent.depth + 1;
		} else {
			// otherwise: grab this node's generated values from the parent's children.
			// This will also generate a lot of values we are not using.
			// That is why this is a very inefficient way to generate a whole tree, ..
			// But is works for random access if that is desired.
			ArtificialGamePosition copy = siblings.stream().filter(s -> s.name.equals(name)).findAny().orElseThrow(
				() -> new IllegalStateException("Node not found in parent children through random access."));
			lower = copy.lower;
			upper = copy.upper;
			depth = copy.depth;
		}
		bounds_set = true;
	}
	
	/**
	 * Delta = {@code (g - 1) * (U - L)}
	 * <p>
	 * This method computes the delta and adds a randomly generated value of {@code 0} 
	 * or {@code 1} to the result to give an average that aligns with the growth factor {@link Settings#growth_factor}.
	 * @param r the pseudorandom number generator to use
	 * @param lower the lower bound of the parent node
	 * @param upper the upper bound of the parent node
	 * @return the delta for children of this node
	 */
	private long getDelta(Random r, long lower, long upper) {
		double deltaD = (settings.growth_factor - 1) * safeSum(upper, -lower);
		long delta = (long) Math.floor(deltaD);
		delta = safeSum(delta, r.nextDouble() < (deltaD - delta) ? 1 : 0);
		return delta;
	}
	
	/**
	 * Finds the minimum value of an array
	 * @param values the array of values to search
	 * @return the minimum value of the provided {@code values} array.
	 */
	public static long minV(long[] values) {
		long minv = values[0];
		for (int i=1; i < values.length; i++) minv = Long.min(values[i], minv);
		return minv;
	}
	
	/**
	 * Finds the maximum value of an array
	 * @param values the array of values to search
	 * @return the maximum value of the provided {@code values} array.
	 */
	public static long maxV(long[] values) {
		long maxv = values[0];
		for (int i=1; i < values.length; i++) maxv = Long.max(values[i], maxv);
		return maxv;
	}
	
	/**
	 * Sums two values such that the result does not 'overflow' from positive to negative or vice versa.
	 * @param x some value
	 * @param y another value
	 * @return {@code x + y} unless overflow or underflow occurs, in which case 
	 *  {@link Long#MAX_VALUE} or {@link Long#MIN_VALUE} respectively.
	 */
	public static long safeSum(long x, long y) {
		long z = x + y;
		if (y > 0 && z < x) return Long.MAX_VALUE;
		if (y < 0 && z > x) return Long.MIN_VALUE;
		return z;
	}
	
	@Override
	public String toString() {
		return String.format(Locale.CANADA, "%s%d-%d%s|n=%s",
				maximising?"[":"(",lower,upper,maximising?"]":")",settings.memory_saver ? name64 : name);
	}

}
