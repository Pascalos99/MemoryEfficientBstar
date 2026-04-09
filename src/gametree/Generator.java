package gametree;

import java.util.Random;

import gametree.VariantAGP.NodeInfo;

import static gametree.ArtificialGamePosition.*;

/**
 * A generator for customising the tree structure of artificial game trees made with the {@link VariantAGP} class.
 * @param <T> The output type, this is what is being generated
 * @param <S> The input type, this is what parameters are provided to this generator
 */
@FunctionalInterface
public interface Generator<T, S> {

	/**
	 * Generate the output value of type {@code T} with the random number generator {@code r} and the
	 * input {@code params} of type {@code S}.
	 * @param r The random number generator to use, this should be the only source of randomness in any implementation of this method.
	 * @param params The input parameteres identifying all information available to the generator. This is the only information the generator has to base its output on.
	 * @return A generated value, which may be constant, based on the {@code params} and deterministically, pseudo-randomly, seeded only by the provided randomiser {@code r}.
	 */
	public T generate(Random r, S params);
	
	/**
	 * The generator type specifically used for generating the variable branching factor
	 * at each node of a {@link VariantAGP} artificial game tree.
	 */
	@FunctionalInterface
	public static interface Width {
		/**
		 * Generates a random branching factor for one node given the provided parameters.
		 * <p>
		 * If it is desired to produce fewer than the minimum allowed number of children, to terminate the tree at this node,
		 * then it is advised to instead program this into the {@link Bounds} generator. The correct termination of nodes 
		 * should occur when their lower and upper bounds are equal, and thus should not occur when this is not the case. The {@linkplain Width}
		 * generator is only ever called on nodes which are not already terminal nodes, so they should have at least 1 child.
		 * @param r The random number generator to use, this should be the only source of randomness in any implementation of this method.
		 * @param min_allowed The minimum allowed output, this is the minimum branching factor, typically 1. The output should not be below this value.
		 * @param max_allowed The maximum allowed output, this is the maximum branching factor. The output should not be above this value, or risk unpredictable seed overlapping behaviour.
		 * @param current_depth The depth of the current node, for which this generator returns the number of children to generate.
		 * @return The number of children to generate for this node.
		 */
		public int generate(Random r, int min_allowed, int max_allowed, long current_depth);
		
		/**
		 * A default generator which always returns the maximum branching factor.
		 */
		public static Generator.Width FIXED = (r,min,max,d) -> max;
	}
	
	/**
	 * The generator type specifically used for generating the bounds of the children
	 * generated as the children of each node of a {@link VariantAGP} artificial game tree.
	 * <p>
	 * The output value is an array of {@code long} integers. However, there are no restrictions on the length of this 
	 * array, besides the minimum of size {@code 1}. The lower and upper bound of the resulting child node are computed
	 * by taking the minimum and maximum of the values in the output array respectively.
	 * <p>
	 * By returning an array of size {@code 1}, the resulting child is a terminal node with the only provided value in
	 * the array as its point value.
	 */
	@FunctionalInterface
	public static interface Bounds extends Generator<long[], NodeInfo> {}
	
	/**
	 * Generates nodes with the same distribution as {@link ArtificialGamePosition} with the supplied
	 * {@code num_alts} variable. Bounds are the minimum and maximum of a list of {@code num_alts} random variables within the parent's
	 * bounds or beyond it (with a growth factor greater than 1).
	 * @param num_alts See {@link ArtificialGamePosition.Settings#num_alts}.
	 * @return The described generator.
	 */
	public static Bounds DEFAULT(int num_alts) {
		if (num_alts < 2)
			throw new IllegalArgumentException("Distribution parameter k must be 2 or greater");
		return (r, n) -> {
			long[] values = new long[num_alts + (n.forceRelevance() ? 1 : 0)];
			if (n.forceRelevance())
				values[num_alts] = r.nextLong(n.relevantL(), n.relevantU());
			for (int i=0; i < num_alts; i++)
				values[i] = r.nextLong(n.lowerbound(), n.upperbound());
			return values;
		};
	}
	
	/**
	 * Generates nodes with a fixed range defined as the parent's range minus a random variable.
	 * @param minShrink The minimum amount of shrinking per depth of the tree (in evaluation function value units)
	 * @param maxShrink The maximum amount of shrinking per depth of the tree (in evaluation function value units)
	 * @return The described generator.
	 */
	public static Bounds STEADY_SHRINK(long minShrink, long maxShrink) {
		if (minShrink > maxShrink)
			throw new IllegalArgumentException("min ("+minShrink+") should be lower than max ("+maxShrink+")");
		if (minShrink < 0)
			throw new IllegalArgumentException("negative shrinking is not supported, try raising the growth factor insread.");
		return (r, n) -> {
			long range = safeSum(safeSum(safeSum(n.relevantU(), - n.relevantL()), -1), -r.nextLong(minShrink, maxShrink));
			long lower;
			if (n.forceRelevance())
				lower = r.nextLong(n.relevantL(), safeSum(n.relevantU(),-range));
			else
				lower = r.nextLong(n.lowerbound(), safeSum(n.upperbound(),-range));
			return new long[] {lower, safeSum(lower, range)};
		};
	}
	
	/**
	 * Generates according to the supplied generator, unless the parent's range is lower than or equal to the minRange.
	 * In which case the children will all be generated as terminal nodes, with at least one within the parent's range.
	 * @param minRange The minimum range for non-terminal nodes in the tree.
	 * @param generator The base generator to apply when conditions are not met.
	 * @return The described generator.
	 */
	public static Bounds MINRANGE(long minRange, Bounds generator) {
		return (r,n) -> {
			if (n.relevantU() - n.relevantL() <= minRange)
				if (n.forceRelevance())
					return new long[] {r.nextLong(n.relevantL(), n.relevantU())};
				else
					return new long[] {r.nextLong(n.lowerbound(), n.upperbound())};
			return generator.generate(r, n);
		};
	}
	
	/**
	 * Generates according to the supplied generator, unless the parent's depth is above or equal to the maxDepth.
	 * In which case, the children will all be generated as terminal nodes, with at least one within the parent's range.
	 * @param maxDepth Maximum depth of the tree, for which any nodes beyond it are all terminal nodes.
	 * @param generator The base generator to apply when conditions are not met.
	 * @return The described generator.
	 */
	public static Bounds MAXDEPTH(int maxDepth, Bounds generator) {
		return (r, n) -> {
			if (n.depth() >= maxDepth) {
				if (n.forceRelevance()) return new long[] {r.nextLong(n.relevantL(), n.relevantU())};
				return new long[] {r.nextLong(n.lowerbound(), n.upperbound())};
			}
			return generator.generate(r, n);
		};
	}
	
	/**
	 * Creates a modified generator which does not produce nodes with a range beyond the provided maxRange.
	 * It does so in the simplest possible way, by effectively setting a limit on the effects of the growth factor.
	 * It does not modify how values are generated, only which bounds are provided to the base generator.
	 * @param maxRange The maximum range of nodes of the tree.
	 * @param generator The base generator to modify.
	 * @return The described generator.
	 */
	public static Bounds MAXRANGE(long maxRange, Bounds generator) {
		return (r,n) -> {
			if (n.maximising()) {
				NodeInfo alt = new NodeInfo(n.maximising(), Math.max(n.lowerbound(), n.upperbound() - maxRange - 1),
						n.relevancebound(), n.upperbound(), n.depth(), n.forceRelevance());
				return generator.generate(r, alt);
			}
			NodeInfo alt = new NodeInfo(n.maximising(), n.lowerbound(), n.relevancebound(),
					Math.min(n.upperbound(), n.lowerbound() + maxRange + 1), n.depth(), n.forceRelevance());
			return generator.generate(r, alt);
		};
	}
}
