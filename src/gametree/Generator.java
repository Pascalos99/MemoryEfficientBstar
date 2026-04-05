package gametree;

import java.util.Random;

import gametree.VariantAGP.NodeInfo;

import static gametree.ArtificialGamePosition.*;

@FunctionalInterface
public interface Generator<T, S> {

	public T generate(Random r, S params);
	
	@FunctionalInterface
	public static interface Width {
		public int generate(Random r, int min_allowed, int max_allowed, long current_depth);
		
		public static Generator.Width FIXED = (r,min,max,d) -> max;
	}
	
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
