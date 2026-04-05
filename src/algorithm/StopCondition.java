package algorithm;

import java.util.Objects;

import gametree.GameTreeNode;
import gametree.MetricKeeper;

/**
 * A stop condition for B* search. Mostly used by B*-squared to limit the extent of second-level searches.
 */
@FunctionalInterface
public interface StopCondition {

	boolean stopSearching(GameTreeNode<?,?> node, MetricKeeper... metrics);
	
	default StopCondition and(StopCondition other) {
		Objects.requireNonNull(other);
		return (n, m) -> stopSearching(n, m) && other.stopSearching(n, m);
	}
	
	default StopCondition or(StopCondition other) {
		Objects.requireNonNull(other);
		return (n, m) -> stopSearching(n, m) || other.stopSearching(n, m);
	}
	
	default StopCondition not() {
		return (n, m) -> !stopSearching(n, m);
	}
	
	//%% DEFAULT STOP CONDITIONS %%//
	
	public static final StopCondition NONE = (n, m) -> false;
	
	public static final StopCondition SEPARATION = (n, m) -> n.isRoot() && GameTreeNode.separation(n, m);
	
}
