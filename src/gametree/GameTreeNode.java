package gametree;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import utils.Probability;

/**
 * This class represents an abstract GameTreeNode which stores game positions 
 * of a game tree along with the structure of the tree itself through 
 * {@link #children(MetricKeeper)} and {@link #parent()} relations.
 * <p>
 * The GameTree logic is kept separate from the {@link IGamePosition} logic 
 * to allow any kind of architecture to be more easily applied to any kind 
 * of game without having the two sides of code interact directly.
 * <p>
 * A {@link GameTreeNode} implementation thus provides exact implementations for 
 * data storage, retrieval, and management. Implementation details may 
 * affect the efficiency and memory usage of algorithms using them. In 
 * principle, any algorithm could work on any implementation of {@link GameTreeNode}, 
 * but some algorithms may be specifically designed with a particular implementation 
 * in mind.
 * 
 * @param <N> The implementing type of {@link GameTreeNode} that extends this class.
 * @param <P> The type of {@link IGamePosition} objects stored in this GameTreeNode, 
 * represents the game which is being represented by this game tree.
 * 
 * @author Pascal Rodrigo Anema
 * @version 1.0
 */
public abstract class GameTreeNode<N extends GameTreeNode<N,P>, P extends IGamePosition<P>> implements Serializable {
	
	/**
	 * Change whenever major changes to class structure are made.
	 * @see 5.6.1 from <a href=https://docs.oracle.com/javase/6/docs/platform/serialization/spec/version.html#6678>docs.oracle.com</a>
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * Creates a GameTreeNode in the same game tree as the given 
	 * parent node, as its child node, where the given position 
	 * is the current game state. Or creates a new root node for the 
	 * game tree of the given position if {@code parent} is {@code null}.
	 * @param parent the parent node of this node or {@code null} if this is a root node
	 * @param position the current game state at this node
	 */
	protected GameTreeNode(N parent, P position, MetricKeeper... metrics) {
		this.position = position;
		this.parent = parent;
		depth = parent == null ? 0 : parent.depth() + 1;
		attachMetrics(metrics);
		if (parent != null && parent.hasAttachedMetrics())
			attachMetrics(parent.getAttachedMetrics());
	}
	
	/**
	 * The game {@link #position()} this node represents.
	 */
	private P position;
	/**
	 * The {@link GameTreeNode} object of the {@link #parent()} to this node.
	 */
	private N parent;
	/** 
	 * The {@link depth()} of this node. This is {@code 0} at the root.
	 */
	private long depth;
	
	/**
	 * The game position {@link IGamePosition} this node represents. This is the current 
	 * game position as reached by moves after the position represented by the root node 
	 * of this tree through all ancestor nodes to this one.
	 * <p>
	 * If this node is the root node, {@link #position()} represents the current state 
	 * of the game.
	 * <p>
	 * However, it is possible that the {@link GameTreeNode} implementation determines 
	 * to prune this object for memory conservation purposes after all necessary information 
	 * has been obtained from this game position. In which case, {@link #position()} will 
	 * return {@code null}.
	 * @return The {@link IGamePosition} object representing the game position at this node, 
	 * or {@code null} if this node has been pruned for memory conservation.
	 */
	public P position() {
		return position;
	}
	/**
	 * Sets {@link #position()} to {@code null}
	 */
	protected void clearPosition() {
		position = null;
	}
	/**
	 * The {@link GameTreeNode} object of the {@link #parent()} of this node.
	 * the node representing the game state previous to {@link #position}.
	 * <p>
	 * If this node is the root node of the tree, the {@link #parent} 
	 * will be {@code null}.
	 * @return The parent {@link GameTreeNode} of this node.
	 */
	public N parent() {
		return parent;
	}
	/**
	 * @return {@code 0} if this is the root, otherwise the number of ancestors until 
	 * reaching the root.
	 */
	public long depth() {
		return depth;
	}
	
	/**
	 * @return {@code true} if this node is a root (its parent is {@code null}) or {@code false} otherwise
	 */
	public boolean isRoot() {
		return parent == null;
	}
	
	protected void setParent(N newParent) {
		parent = newParent;
	}
	
	/**
	 * This is {@link IGamePosition#maximising()} from the {@link #position()} of this node.
     * @return {@code true} if the current player-to-move is maximising the score,
     * or {@code false} if it is minimising the score.
	 */
	public abstract boolean maximising();
	
	/**
	 * The children of this node are its direct descendants. During the search, these nodes' 
	 * values may be updated with information gathered during the search. At first, the children 
	 * of a node are exactly the same nodes as those returned from {@link IGamePosition#next()}, 
	 * but may be modified later to aid the search. The returned {@code children} collection may 
	 * be empty, but should never be {@code null}.
	 * 
	 * @param metrics One or more {@link MetricKeeper} objects to keep track of node evaluations and expansions 
	 * made by this {@link GameTreeNode} through this method. Or zero if there is no need 
	 * to keep track of these metrics.
	 * @return The collection of game tree nodes directly reachable by moves from the current player to move 
	 * in the game position this game tree node represents.
	 */
	public List<N> children(MetricKeeper... metrics) {
		var saved = savedChildren();
		if (saved.isPresent()) return saved.get();
		return _children(combineMetricList(metrics));
	}
	
	/**
	 * Method to implement functionality of {@link #children(MetricKeeper...)}.
	 */
	protected abstract List<N> _children(MetricKeeper... metrics);
	
	/**
	 * @return An Optional containing all children nodes saved in the memory of this {@link GameTreeNode} or an empty Optional if none are saved in memory. 
	 * An Optional containing an empty List signifies a terminal node with no children.
	 */
	public abstract Optional<List<N>> savedChildren();

	/**
	 * The upper-bound value is the upper bound on the game-theoretical mini-max value of this game {@link #position()}.
     * <p>
     * For the maximising player this is the best value achievable under perfect play.
     * <p>
     * For the minimising player this is the worst-case value achievable under perfect play.
     * <p>
     * This value is initially set as the value returned by {@link IGamePosition#upperbound()} 
     * and later updated to reflect a tighter upper-bound for the game-theoretical value of this node.
	 * @param metrics One or more {@link MetricKeeper} objects to keep track of node evaluations and expansions 
	 * made by this {@link GameTreeNode} through this method. Or zero if there is no need 
	 * to keep track of these metrics.
	 * @return The upper-bound value for this game position
	 */
	public double upperbound(MetricKeeper... metrics) {
		if (hasSavedBounds()) return _upperbound();
		return _upperbound(combineMetricList(metrics));
	}
	
	/**
	 * Method to implement functionality of {@link #upperbound(MetricKeeper...)}.
	 */
	protected abstract double _upperbound(MetricKeeper... metrics);
	
	/**
	 * The lower-bound value is the lower bound on the game-theoretical mini-max value of this game position.
     * <p>
     * For the maximising player this is the minimum value that can be guaranteed under perfect play.
     * <p>
     * For the minimising player this is the best value achievable under perfect play.
     * <p>
     * This value is initially set as the value returned by {@link IGamePosition#lowerbound()} 
     * and later updated to reflect a tighter lower-bound for the game-theoretical value of this node.
	 * @param metrics One or more {@link MetricKeeper} objects to keep track of node evaluations and expansions 
	 * made by this {@link GameTreeNode} through this method. Or zero if there is no need 
	 * to keep track of these metrics.
	 * @return The lower-bound value for this game position
	 */
	public double lowerbound(MetricKeeper... metrics) {
		if (hasSavedBounds()) return _lowerbound();
		return _lowerbound(combineMetricList(metrics));
	}
	
	/**
	 * Method to implement functionality of {@link #lowerbound(MetricKeeper...)}.
	 */
	protected abstract double _lowerbound(MetricKeeper... metrics);
	
	
	public abstract boolean hasSavedBounds();
	/**
	 * Adjusts the bounds of this node according to the values of the {@code children} nodes as given by 
	 * {@link #children(MetricKeeper...)}. Maximising nodes will adjust their bounds to be the maximum of 
	 * the lower- and upper-bound values of their children, whereas minimising nodes will instead use the 
	 * minimum of the lower- and upper-bound values of their children.
	 * @param metrics One or more {@link MetricKeeper} objects to keep track of node evaluations and expansions 
	 * made by this {@link GameTreeNode} through this method. Or zero if there is no need 
	 * to keep track of these metrics.
	 * @return {@code true} if the bounds of this node have been altered, {@code false} otherwise
	 */
	public boolean adjustBounds(MetricKeeper... metrics) {
		return adjustBounds(true, metrics);
	}
	
	public boolean adjustBounds(boolean useExpansions, MetricKeeper... metrics) {
		MetricKeeper[] M = combineMetricList(metrics);
		if (useExpansions)
			return _adjustBounds(_children(M), M);
		else {
			Optional<List<N>> hasChildren = savedChildren();
			if (hasChildren.isPresent()) return _adjustBounds(hasChildren.get(), M);
		}
		return false;
	}
	
	public boolean adjustBounds(List<N> children, MetricKeeper... metrics) {
		return _adjustBounds(children, combineMetricList(metrics));
	}
	
	/**
	 * Method to implement functionality of {@link #adjustBounds(List<N>, MetricKeeper...)}.
	 */
	protected abstract boolean _adjustBounds(List<N> children, MetricKeeper... metrics);
	
	/**
	 * Depth-of-upperbound is the depth from which the upper bound of this node has been backed up. 
	 * This denotes the depth of search from which the bounds of a node have been obtained. It can act 
	 * as a metric to determine the effort expended at a certain node, but is not entirely reliable for 
	 * this purpose.
	 * @param metrics One or more {@link MetricKeeper} objects to keep track of node evaluations and expansions 
	 * made by this {@link GameTreeNode} through this method. Or zero if there is no need 
	 * to keep track of these metrics.
	 * @return The depth from which the {@link #upperbound(MetricKeeper)} has been backed-up. 
	 * Or {@code 0} if it has not yet been updated from the initial {@link IGamePosition#upperbound()} 
	 * value.
	 */
	public long depthOfUpper(MetricKeeper... metrics) {
		if (hasSavedBounds()) return _depthOfUpper();
		return _depthOfUpper(combineMetricList(metrics));
	}
	
	/**
	 * Method to implement functionality of {@link #depthOfUpper(MetricKeeper...)}.
	 */
	protected abstract long _depthOfUpper(MetricKeeper... metrics);
	
	/**
	 * Depth-of-lowerbound is the depth from which the lower bound of this node has been backed up. 
	 * <p>
	 * This denotes the depth of search from which the bounds of a node have been obtained. <br>It can act 
	 * as a metric to determine the effort expended at a certain node, but is not entirely reliable for 
	 * this purpose.
	 * @param metrics One or more {@link MetricKeeper} objects to keep track of node evaluations and expansions 
	 * made by this {@link GameTreeNode} through this method. Or zero if there is no need 
	 * to keep track of these metrics.
	 * @return The depth from which the {@link #lowerbound(MetricKeeper)} has been backed-up. 
	 * Or {@code 0} if it has not yet been updated from the initial {@link IGamePosition#lowerbound()} 
	 * value.
	 */
	public long depthOfLower(MetricKeeper... metrics) {
		if (hasSavedBounds()) return _depthOfLower();
		return _depthOfLower(combineMetricList(metrics));
	}
	
	/**
	 * Method to implement functionality of {@link #depthOfLower(MetricKeeper...)}.
	 */
	protected abstract long _depthOfLower(MetricKeeper... metrics);
	
	protected long nodeSize() {
		return 1l;
	}
	
	/**
	 * @return The size, in number of nodes, of the sub-tree of saved nodes starting at this node, including this one (minimum of {@code 1})
	 */
	public long countSavedSubTree(boolean countSelf) {
//		var children = savedChildren();
//		if (children.isEmpty() || children.get().size() <= 0) return 1;
//		return 1 + children.get().stream().mapToLong(GameTreeNode::countSavedSubTree).sum();
		long count = 0;
		ArrayList<GameTreeNode<?,?>> stack = new ArrayList<>();
		if (!countSelf) {
			var children = savedChildren();
			if (children.isPresent())
				stack.addAll(children.get());
		} else
			stack.addLast(this);
		while (!stack.isEmpty()) {
			var current = stack.removeLast();
			count += nodeSize();
			
			var children = current.savedChildren();
			if (children.isPresent())
				stack.addAll(children.get());
		}
		return count;
	}
	
	private MetricKeeper[] personal_metrics;
	
	public void attachMetrics(MetricKeeper... m) {
		personal_metrics = combineMetricList(m);
	}
	
	public MetricKeeper[] getAttachedMetrics() {
		return personal_metrics == null ? new MetricKeeper[0] : personal_metrics;
	}
	
	public boolean hasAttachedMetrics() {
		return personal_metrics != null && personal_metrics.length > 0;
	}
	
	protected MetricKeeper[] combineMetricList(MetricKeeper... metrics) {
		return MetricKeeper.combineArrays(personal_metrics, metrics);
	}
	
	/**
	 * Uses the parental line of succession of this node to retroactively add {@link MetricKeeper} objects 
	 * from this node's ancestors to this node's personal list of {@link MetricKeeper} objects.
	 * @return {@code true} if the metrics of this node have been altered, and {@code false} otherwise.
	 */
	public boolean updateMetricsParental() {
		if (parent == null || !parent.hasAttachedMetrics()) return false;
		parent.updateMetricsParental();
		int old_length = personal_metrics == null ? 0 : personal_metrics.length;
		attachMetrics(parent.getAttachedMetrics());
		return old_length != personal_metrics.length;
	}
	
	public void updateMetrics() {
		if (personal_metrics == null) return;
		var hasChildren = savedChildren();
		if (hasChildren.isEmpty()) return;
		var children = hasChildren.get();
		if (children.size() <= 0 ) return;
		for (var child : children) {
			child.attachMetrics(personal_metrics);
			child.updateMetrics();
		}
	}
	
	// PROBABILITY CALCULATIONS
	
	/**
	 * Computes the Cumulative Distribution Function (CDF) {@code P(X ≤ t)} of this node, assuming that the distribution is a 
	 *  discrete one. The underlying sub-tree will not be further expanded, rather only evaluated based
	 *  on its current state, and at most up to the given maximum depth.
	 * @param maxDepth the maximum depth to which evaluations will be performed to compute this. NO EXPANSIONS WILL BE MADE.
	 * @param metrics {@link MetricKeeper} objects to keep track of node evaluations made by this method.
	 * @return The CDF of this node's distribution, given this distribution is assumed to be discrete.
	 */
	public double[] CDF(int maxDepth, MetricKeeper... metrics) {
		double[] res = CDF_or_survival(this, maxDepth, maximising(), metrics);
		return maximising() ? res : Probability.toCDF(res);
	}
	
	/**
	 * Computes the Survival Function {@code P(X ≥ x)} of this node, assuming that the distribution is a 
	 *  discrete one. The underlying sub-tree will not be further expanded, rather only evaluated based
	 *  on its current state, and at most up to the given maximum depth.
	 * @param maxDepth the maximum depth to which evaluations will be performed to compute this. NO EXPANSIONS WILL BE MADE.
	 * @param metrics {@link MetricKeeper} objects to keep track of node evaluations made by this method.
	 * @return The Survival Function of this node's distribution, given this distribution is assumed to be discrete.
	 */
	public double[] survivalFunction(int maxDepth, MetricKeeper... metrics) {
		double[] res = CDF_or_survival(this, maxDepth, maximising(), metrics);
		return maximising() ? Probability.toSurvival(res) : res;
	}
	
	/**
	 * Computes the Probability Mass Function (PMF) {@code P(X = x)} of this node, assuming that the distribution is a 
	 *  discrete one. The underlying sub-tree will not be further expanded, rather only evaluated based
	 *  on its current state, and at most up to the given maximum depth.
	 * @param maxDepth the maximum depth to which evaluations will be performed to compute this. NO EXPANSIONS WILL BE MADE.
	 * @param metrics {@link MetricKeeper} objects to keep track of node evaluations made by this method.
	 * @return The PMF of this node's distribution, given this distribution is assumed to be discrete.
	 */
	public double[] PMF(int maxDepth, MetricKeeper... metrics) {
		return Probability.toPMF(CDF_or_survival(this, maxDepth, maximising(), metrics), maximising());
	}
	
	/**
	 * Computes the CDF or Survival function of this node, with an accuracy specified by the given maximum depth, 
	 * and only using the already expanded sub-tree of this node.
	 * <br>
	 * The CDF is P(X ≤ t), returned when {@code maximising = true}. 
	 * <br>
	 * The Survival Function is P(X ≥ t), returned when {@code maximising = false}.
	 * <p>
	 * These probabilities refer to the distribution of the true mini-max value of the node, 
	 * when it is assumed that leaf nodes have a uniform distribution over their range.
	 * @param maxDepth the maximum depth to which evaluations will be performed to compute this. NO EXPANSIONS WILL BE MADE.
	 * @param maximising whether to compute the CDF or Survival Function of this node
	 * @param metrics {@link MetricKeeper} objects to keep track of node evaluations made by this method.
	 * @return CDF or Survival Function of this node's distribution
	 */
	private static double[] CDF_or_survival(GameTreeNode<?,?> node, int maxDepth, boolean maximising, MetricKeeper... metrics) {
		var hasChildren = node.savedChildren();
		if (maxDepth <= 0 || hasChildren.isEmpty() || hasChildren.get().size() <= 0) {
			if (hasChildren.isPresent() && hasChildren.get().stream().allMatch(c -> c.hasSavedBounds()))
				node.adjustBounds(metrics);
			return Probability.discreteUniform(
					(int) Math.floor(node.lowerbound(metrics)), // lower bound
					(int) Math.ceil(node.upperbound(metrics)),  // upper bound
					maximising); // for maximising: cumulative (CDF), for minimising: subtractive (Survival Function)
		}
		var children = hasChildren.get();
		double[][] CDFs = new double[children.size()][];
		int[] lowerbounds = new int[children.size()];
		for (int i=0; i < children.size(); i++) {
			var child = children.get(i);
			lowerbounds[i] = (int) Math.floor(child.lowerbound(metrics));
			var hasGrandchildren = child.savedChildren();
			if (maxDepth <= 1 || hasGrandchildren.isEmpty() || hasGrandchildren.get().size() <= 0)
				// at terminal nodes, the returned CDF or SDF is symmetric, so to save on operations, we do
				//  not transform the given array and instead ask for the array we need right away.
				CDFs[i] = CDF_or_survival(child, maxDepth - 1, maximising, metrics);
			else {
				// in other cases, we only transform the given array if it is in the wrong format
				//  (CDF instead of SDF or SDF instead of CDF)
				var childCDF = CDF_or_survival(child, maxDepth - 1, child.maximising(), metrics);
				if (child.maximising() != maximising)
					CDFs[i] = maximising ? Probability.toCDF(childCDF) : Probability.toSurvival(childCDF);
				else
					CDFs[i] = childCDF;
			}
		}
		node.adjustBounds(metrics);
		int lower = (int) Math.floor(node.lowerbound(metrics));
		int upper = (int) Math.ceil(node.upperbound(metrics));
		return Probability.minOrMaxDistribution(CDFs, lowerbounds, lower, upper, maximising);
	}
	
	// UTILITY FUNCTIONS FOR GAME-TREE IMPLEMENTATIONS
	public static final boolean USE_EXPERIMENTAL_BOUNDS_UPDATE = false;
	/**
	 * Adjust the bounds of a node.
	 * @param <N> Type of the GameTreeNode
	 * @param <P> Type of the IGamePosition
	 * @param maximising whether the parent node is maximising or not
	 * @param lowerUpper an array of size 2 containing the lower- and upper-bound of the parent (unchecked)
	 * @param depthLowerUpper an array of size 2 containing the depth of the lower- and upper-bound of the parent (unchecked)
	 * @param children the children of the parent node
	 * @param metrics
	 * @return {@code true} if the bounds were changed, {@code false} otherwise.
	 */
	public static <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> boolean adjustBounds(
			boolean maximising, double[] lowerUpper, long[] depthLowerUpper, List<N> children, MetricKeeper... metrics) {
		return adjustBounds(maximising, lowerUpper, depthLowerUpper, children, USE_EXPERIMENTAL_BOUNDS_UPDATE, metrics);
	}
	/**
	 * Adjust the bounds of a node.
	 * @param <N> Type of the GameTreeNode
	 * @param <P> Type of the IGamePosition
	 * @param maximising whether the parent node is maximising or not
	 * @param lowerUpper an array of size 2 containing the lower- and upper-bound of the parent (unchecked)
	 * @param depthLowerUpper an array of size 2 containing the depth of the lower- and upper-bound of the parent (unchecked)
	 * @param children the children of the parent node
	 * @param experimental whether or not to update the lowerUpper array in-place. This setting 
	 * also makes the node adjustment only update bounds if they improve, modifying Bstar-Squared behaviour.
	 * @param metrics
	 * @return {@code true} if the bounds were changed, {@code false} otherwise.
	 */
	public static <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> boolean adjustBounds(
			boolean maximising, double[] lowerUpper, long[] depthLowerUpper, List<N> children,
			boolean experimental, MetricKeeper... metrics) {
		
		if (children.size() <= 0) return false;
		boolean res = false;
		double lower = maximising? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
		double upper = lower;
		for (var child : children) {
			double child_upper = child.upperbound(metrics);
			double child_lower = child.lowerbound(metrics);
			  // adjust upper-bound:
			if ((maximising && child_upper >= upper) || (!maximising && child_upper <= upper)) {
				// adjust the depth of upper-bound:
				if (child_upper == upper)
					depthLowerUpper[1] = Math.max(depthLowerUpper[1], child.depthOfUpper(metrics) + 1);
				else 
					depthLowerUpper[1] = child.depthOfUpper(metrics) + 1;
				upper = child_upper;
				// TODO: evaluate this, it seems this modification might affect all
				//       versions of B*, including df-B*, regular B*, and B*-squared
				// this is an improvement for earlier pruning in B*-squared:
				if (experimental && !maximising && upper < lowerUpper[1]) {
					lowerUpper[1] = upper; res = true;
				}
			} // adjust lower-bound:
			if ((maximising && child_lower >= lower) || (!maximising && child_lower <= lower)) {
				// adjust the depth of lower-bound:
				if (child_lower == lower)
					depthLowerUpper[0] = Math.max(depthLowerUpper[0], child.depthOfLower(metrics) + 1);
				else
					depthLowerUpper[0] = child.depthOfLower(metrics) + 1;
				lower = child_lower;
				// TODO: evaluate this, it seems this modification might affect all
				//       versions of B*, including df-B*, regular B*, and B*-squared
				// this is an improvement for earlier pruning in B*-squared:
				if (experimental && maximising && lower > lowerUpper[0]) { 
					lowerUpper[0] = lower; res = true;
				}
			}
		}
		res |= lowerUpper[0] != lower || lowerUpper[1] != upper;
		lowerUpper[0] = lower;
		lowerUpper[1] = upper;
		return res;
	}
	
	// UTILITY FUNCTIONS FOR SEARCH ALGORITHMS
	/**
     * A record to hold the two best nodes (and their indices in the original collection).
     *
     * @param best           the best node
     * @param secondbest     the second-best node
     * @param bestIndex      the index of the best node in the input collection
     * @param secondBestIndex the index of the second-best node in the input collection
     */
	public record Result<N extends GameTreeNode<N,P>, P extends IGamePosition<P>>(N best, N secondbest, int bestindex, int best2index) {
		/**
		 * Separation occurs when one node in a set of children is strictly better than its siblings. In this case, the remaining 
		 * children can be discarded from the search. The best child is now the only relevant child among the set of children.
		 * <p>
		 * Note that this only accurately checks for separation if this {@link Result} was obtained through 
		 * {@link GameTreeNode#mostOptimistic2(Collection, boolean, MetricKeeper...)}
		 * @param metrics {@link MetricKeeper} objects to notify of node evaluations made by this check
		 * @return {@code true} if the sub-tree from the parent of the given {@code children} could be pruned 
		 * 						according to the separation rule; {@code false} otherwise.
		 */
		public boolean separation(MetricKeeper... metrics) {
			return 
					// separation when maximising:
					best.lowerbound(metrics) >= secondbest.upperbound(metrics) ||
					// separation when minimising:
					best.upperbound(metrics) <= secondbest.lowerbound(metrics);
		}
	}
	
	/**
	 * Calculate the best 2 nodes from the perspective specified by the {@code maximizing} and {@code by_optimistic} flags.
	 * <p>
	 * If {@code maximizing} is {@code true} and {@code by_optimistic} is {@code true}, nodes with maximum {@code upperbound()} value are selected.
	 * Ties are broken with the highest {@code lowerbound()} value.
	 * <p>
	 * If {@code maximizing} is {@code true} and {@code by_optimistic} is {@code false}, nodes with maximum {@code lowerbound()} value are selected.
	 * Ties are broken with the highest {@code upperbound()} value.
	 * <p>
	 * If {@code maximizing} is {@code false} and {@code by_optimistic} is {@code true}, nodes with minimum {@code lowerbound()} value are selected.
	 * Ties are broken with the lowest {@code upperbound()} value.
	 * <p>
	 * If {@code maximizing} is {@code false} and {@code by_optimistic} is {@code false}, nodes with minimum {@code upperbound()} value are selected.
	 * Ties are broken with the lowest {@code lowerbound()} value.
	 *
	 * @param <N>         the node type extending {@link GameTreeNode}
	 * @param <P>		  the game-position type extending {@link IGamePosition}
	 * @param children    the collection of nodes to evaluate
	 * @param maximising  a flag indicating whether to select from a maximising ({@code true}) or minimising ({@code false}) perspective
	 * @param by_optimistic a flag indicating whether to select the most optimistic ({@code true}) or least pessimistic ({@code false}) nodes
	 * @param metrics	  an array of {@link MetricKeeper} objects to keep track of evaluations and expansions made through this method, can be empty
	 * @return a record containing the best and second-best nodes and their corresponding indices.
	 */
	public static <N extends GameTreeNode<N,P>, P extends IGamePosition<P>> Result<N,P> findBest2(Collection<N> children, boolean maximising, boolean by_optimistic, MetricKeeper... metrics) {
		if (children == null) throw new NullPointerException("children may not be `null`");
		N best = null, secondbest = null;
		int bestIndex = -1, secondBestIndex = -1;
		double bestValue = Double.NEGATIVE_INFINITY, bestTie = Double.NEGATIVE_INFINITY;
		double secondValue = Double.NEGATIVE_INFINITY, secondTie = Double.NEGATIVE_INFINITY;
		int index = 0;
		for (N child : children) {
			// for max-nodes, select the node with the maximum upper bound, ties broken with maximum lower bound
			// for min-nodes, select the node with the minimum lower bound, ties broken with minimum upper bound
			double value = maximising? child.upperbound(metrics) : -child.lowerbound(metrics);
			double tie   = maximising? child.lowerbound(metrics) : -child.upperbound(metrics);
			if (!by_optimistic) {
				// select the best nodes by their best pessimistic values instead.
				// we then select for the maximum lower bound or minimum upper bound
				// and break ties with the maximum upper bound or minimum lower bound
				double t = value;
				value = tie;
				tie = t;
			}
			if (value > bestValue || (value == bestValue && tie >= bestTie)) {
				// Move current best to second-best
				secondValue 	= bestValue;
				secondTie 		= bestTie;
				secondbest 		= best;
				secondBestIndex = bestIndex;
				// Set the new best
				bestValue 		= value;
				bestTie 		= tie;
				best 			= child;
				bestIndex 		= index;
			} else if (value > secondValue || (value == secondValue && tie >= secondTie)) {
				secondValue 	= value;
				secondTie 		= tie;
				secondbest 		= child;
				secondBestIndex = index;
			}
			index++;
		}
		return new Result<N,P>(best, secondbest, bestIndex, secondBestIndex);
	}
	/**
	 * Same as {@link #findBest2(Collection, boolean, boolean, MetricKeeper...)} where {@code by_optimistic} is assumed {@code true}.
	 */
	public static <N extends GameTreeNode<N,P>, P extends IGamePosition<P>> Result<N,P> mostOptimistic2(Collection<N> children, boolean maximising, MetricKeeper... metrics) {
		return findBest2(children, maximising, true, metrics);
	}
	/**
	 * Same as {@link #findBest2(Collection, boolean, boolean, MetricKeeper...)} where {@code by_optimistic} is assumed {@code false}.
	 */
	public static <N extends GameTreeNode<N,P>, P extends IGamePosition<P>> Result<N,P> leastPessimistic2(Collection<N> children, boolean maximising, MetricKeeper... metrics) {
		return findBest2(children, maximising, false, metrics);
	}
	
	/**
	 * @param <N> 			the node type extending {@link GameTreeNode}
	 * @param <P> 			the game-position type extending {@link IGamePosition}
	 * @param children    	the collection of nodes to evaluate
	 * @param maximising  	a flag indicating whether we are observing from a maximising ({@code true}) or minimising ({@code false}) perspective
	 * @param metrics 		an array of {@link MetricKeeper} objects to keep track of evaluations and expansions made through this method, can be empty
	 * @return {@code true} if the sub-tree from the parent of the given {@code children} could be terminated 
	 * 						according to the separation rule; {@code false} otherwise.
	 */
	public static <N extends GameTreeNode<N,P>, P extends IGamePosition<P>> boolean separation(Collection<N> children, boolean maximising, MetricKeeper... metrics) {
		if (children == null) throw new NullPointerException("children may not be `null`");
		if (children.size() <= 1) return true;
		return mostOptimistic2(children, maximising, metrics).separation(metrics);
	}
	
	/**
	 * @param <N> The {@link GameTreeNode} type of the given {@code node}
	 * @param <P> The {@link IGamePosition} type of the given {@code node}
	 * @param n The node at which the sub-tree originates (usually the root node)
	 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations and expansions made through this method, can be empty
	 * @return {@code true} if the sub-tree from the given {@code node} could be terminated 
	 * according to the separation rule; {@code false} otherwise.
	 */
	public static <N extends GameTreeNode<N,P>, P extends IGamePosition<P>> boolean separation(GameTreeNode<?, ?> n, MetricKeeper... metrics) {
		return separation(n.children(metrics), n.maximising(), metrics);
	}
	
	/**
	 * This method gives a comparator for {@link GameTreeNode} objects from the perspective of a parent node. Differently from 
	 * natural ordering, this comparator will sort best values first. For example, for an {@code optimistic && maximising} perspective this means 
	 * sorting by highest {@code upperbound} value first.
	 * @param maximising whether to select from a maximising perspective ({@code true}) or from a minimising perspective ({@code false})
	 * @param by_optimistic whether to select based on the optimistic value ({@code true}) or the pessimistic value ({@code false}). 
	 * From a {@code maximising} perspective, the {@code upperbound} of a node is the optimistic perspective, whereas the {@code lowerbound} 
	 * is the pessimistic perspective. From a {@code minimising} perspective, the {@code lowerbound} of a node is the optimistic perspective, 
	 * whereas the {@code upperbound} is the pessimistic perspective.
	 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations and expansions made through this comparator, can be empty
	 * @return A comparator which sorts a list of nodes by the best nodes first, from the specified perspective.
	 */
	public static Comparator<GameTreeNode<?,?>> getComparator(final boolean maximising, final boolean by_optimistic, MetricKeeper... metrics) {
		return (a, b) -> {
			if ( maximising &&  by_optimistic) return -Double.compare(a.upperbound(metrics), b.upperbound(metrics));
			if ( maximising && !by_optimistic) return -Double.compare(a.lowerbound(metrics), b.lowerbound(metrics));
			if (!maximising &&  by_optimistic) return  Double.compare(a.lowerbound(metrics), b.lowerbound(metrics));
			if (!maximising && !by_optimistic) return  Double.compare(a.upperbound(metrics), b.upperbound(metrics));
			return 0;
		};
	}
	
//	public boolean isRelevantOld(MetricKeeper... metrics) {
//		if (parent() == null) return true;
//		var p = parent();
//		MetricKeeper[] m = metrics;
//		return !(( p.maximising() && upperbound(m) <= p.lowerbound(m) && lowerbound(m) != p.lowerbound(m)) ||
//				 (!p.maximising() && lowerbound(m) >= p.upperbound(m) && upperbound(m) != p.upperbound(m)) ||
//				 (p.lowerbound(m) == p.upperbound(m)));
//	}
	
	public static boolean quickRelevant(boolean pMaximising, double pLower, double pUpper, double cLower, double cUpper) {
		if (pLower == pUpper) return false;
		if ((pMaximising && cUpper <= pLower && cLower < pLower)
		|| (!pMaximising && cLower >= pUpper && cUpper > pUpper)) return false;
		// the edge cases where c is a terminal node which lies on the pessimistic bound of the parent 
		//   are ignored here, and all count as relevant, because this is mostly used for self-check,
		//   where c being terminal is already enough to stop the search.
		return true;
	}
	
	public boolean isRelevant(MetricKeeper... metrics) {
		var p = parent();
		if (p == null) return true;
		boolean isMax = p.maximising();
		double pLower = p.lowerbound(metrics), pUpper = p.upperbound(metrics);
		double lower = lowerbound(metrics), upper = upperbound(metrics);
		// for point-value parents, no children are relevant
		if (pLower == pUpper) return false;
		// if bounds fall entirely outside of parent bounds, they are irrelevant
		if ((isMax && upper <= pLower && lower < pLower)
		|| (!isMax && lower >= pUpper && upper > pUpper)) return false;
		// if bounds are a point-value on the edge of the parent's lower bound, they could be relevant
		if ((isMax && lower == pLower && upper == pLower)
		|| (!isMax && lower == pUpper && upper == pUpper)) {
			boolean found_this = false;
			for (var sibling : p.children(metrics)) {
				boolean sibling_is_this = sibling == this || sibling.equals(this) ||
						(sibling.position() != null && sibling.position().equals(position()));
				found_this |= sibling_is_this;
				if (sibling_is_this) continue;
				// if sibling is strictly better than this node, it is irrelevant
				if (isMax && sibling.lowerbound(metrics) >= upper)
					if (!found_this || sibling.upperbound(metrics) > upper) return false;
				if (!isMax && sibling.upperbound(metrics) <= lower)
					if (!found_this || sibling.lowerbound(metrics) < lower) return false;
			}
		}
		// all other nodes are relevant
		return true;
	}
	
	public double remainingSolveEffort(MetricKeeper... metrics) {
		var children = children(metrics);
		var mostOpt2 = GameTreeNode.mostOptimistic2(children,maximising());
		var leasPes2 = GameTreeNode.leastPessimistic2(children,maximising());
		if (leasPes2.best() == null || mostOpt2.secondbest() == null) return Double.POSITIVE_INFINITY;
		double lb = maximising() ? leasPes2.best().lowerbound(metrics) : mostOpt2.secondbest().lowerbound(metrics);
		double ub = maximising() ? mostOpt2.secondbest().upperbound(metrics) : leasPes2.best().upperbound(metrics);
		double result = 0;
		for (var child : children) {
			double c_lb = child.lowerbound(metrics);
			double c_ub = child.upperbound(metrics);
			if (c_lb >= ub || c_ub <= lb) continue;
			if (c_lb < lb) c_lb = lb;
			result += Math.min(c_ub, ub) - Math.max(c_lb, lb);
		}
		return result;
	}
	
	public void updateTree(MetricKeeper... metrics) {
		var hasChildren = savedChildren();
		if (hasChildren.isEmpty()) return;
		var children = hasChildren.get();
		if (children.size() <= 0) return;
		for (var child : children) child.updateTree(metrics);
		adjustBounds(metrics);
	}
	
	@SuppressWarnings("unchecked")
	public Stream<N> getSavedLeafnodes(int maxDepth, Predicate<N> include) {
		if (maxDepth <= 0)
			return List.of((N) this).stream();
		var hasChildren = savedChildren();
		if (hasChildren.isEmpty() || hasChildren.get().size() <= 0)
			return List.of((N) this).stream();
		return hasChildren.get().stream().flatMap(n -> n.getSavedLeafnodes(maxDepth-1, include)).filter(include);
	}
	
	@SuppressWarnings("unchecked")
	public Stream<N> getAllLeafnodes(int maxDepth, Predicate<N> include, MetricKeeper... metrics) {
		if (maxDepth <= 0)
			return List.of((N) this).stream();
		var children = children(metrics);
		if (children == null || children.size() <= 0)
			return List.of((N) this).stream();
		return children.stream().flatMap(n -> n.getAllLeafnodes(maxDepth-1, include, metrics)).filter(include);
	}
	
	// VISUALIZATION
	
	public String printTree(int depth, MetricKeeper... metrics) {
		return printTree(depth, false, metrics);
	}
	/**
	 * Visually represents the structure of the sub-tree starting from this node as a {@link String}.
	 * @param depth The maximum depth to which the tree should be displaced (starting from the current node)
	 * @param toString Whether to print the identity of each node (rather than just its bounds)
	 * @return A {@link String} representation of the sub-tree of this node, up to the specified depth
	 */
	public String printTree(int depth, boolean toString, MetricKeeper... metrics) {
		return printTree(depth, toString, false, metrics);
	}
	/**
	 * Visually represents the structure of the sub-tree starting from this node as a {@link String}.
	 * @param depth The maximum depth to which the tree should be displaced (starting from the current node)
	 * @param toString Whether to print the identity of each node (rather than just its bounds)
	 * @param printDist Whether to show the distribution function of each node (computed up to the maximum depth specified)
	 * @return A {@link String} representation of the sub-tree of this node, up to the specified depth
	 */
	public String printTree(int depth, boolean toString, boolean printDist, MetricKeeper... metrics) {
		long sdepth = depth();
		StringBuilder sb = new StringBuilder();
		ArrayList<GameTreeNode<N,P>> stack = new ArrayList<>();
		HashSet<GameTreeNode<N,P>> visited = new HashSet<>();
		stack.addLast(this);
		visited.add(this);
		
		Result<N,P> best2Opt = null, best2Pes = null;
		boolean superbest = false;
		
		while (!stack.isEmpty()) {
			var c = stack.removeLast();
			
			var ancestors = new GameTreeNode<?,?>[(int)(double)(c.depth() - sdepth)];
			int k = ancestors.length - 1;
			for (var C = c; true; C = C.parent()) {
				if (C != c) ancestors[k--] = C;
				if (C == this) break;
			}
			for (int i=0; i < ancestors.length; i++) {
				var anc = ancestors[i];
				if (anc == c.parent()) { sb.append("> "); continue; }
				var cousins = anc.savedChildren().get();
				boolean missed_siblings = false;
				for (var cousin : cousins) {
					if (cousin == anc) continue;
					if (stack.contains(cousin) || !visited.contains(cousin)) {
						missed_siblings = true;
						break;
					}
				}
				if (missed_siblings)
					sb.append("| ");
				else
					sb.append("  ");
			}
			sb.append(c.isRelevant(metrics) && c.upperbound(metrics) != c.lowerbound(metrics) ? "o" : "x");
			sb.append(String.format("%s%d,%d%s", c.maximising()?"[":"(", Math.round(c.lowerbound(metrics)), Math.round(c.upperbound(metrics)), c.maximising()?"]":")"));
			if (toString) sb.append(c.toString());
			if (printDist) {
				int remainingDepth = (int)(double)(depth + sdepth - c.depth());
				double[] dist = Probability.cdfToPMF(c.CDF(remainingDepth));
				sb.append("{");
				for (int i=0; i < dist.length; i++)
					sb.append((i==0?"":" ")+String.format("%.3f", dist[i]));
				sb.append("}");
			}
			if (c != this) {
				boolean opt1st = c == best2Opt.best();
				boolean opt2nd = c == best2Opt.secondbest();
				boolean pes1st = c == best2Pes.best();
				if (superbest) {
					if (opt1st) sb.append(" ***");
				} else {
					if (opt1st) sb.append(" +++");
					if (pes1st && !opt2nd) sb.append(" ---");
				}
				if (opt2nd) sb.append(" xxx");
			}
			sb.append("\n");
			
			var hasChildren = c.savedChildren();
			if (hasChildren.isEmpty()) continue;
			var children = hasChildren.get();
			for (int i=children.size()-1; i >= 0; i--) {
				var child = children.get(i);
				if (child.depth() - sdepth <= depth && !visited.contains(child)) {
					stack.addLast(child);
					visited.add(child);
				}
			}
			if (c == this) {
				best2Opt = mostOptimistic2(children, maximising(), metrics);
				best2Pes = leastPessimistic2(children, maximising(), metrics);
				superbest = best2Opt.best() == best2Pes.best();
			}
		}
		sb.append("total of "+visited.size()+" nodes");
		return sb.toString();
	}
	
	// SERIALIZATION
	
	public void toFile(String fileName) throws IOException {
		var oos = new ObjectOutputStream(
				new BufferedOutputStream(
						new FileOutputStream(fileName, false)) );
		oos.writeObject(this);
		oos.close();
	}
	
	public static GameTreeNode<?,?> fromFile(String fileName) throws IOException, ClassNotFoundException {
		try (var ois = new ObjectInputStream(
				new BufferedInputStream(
						new FileInputStream(fileName)))) {
			Object res = ois.readObject();
			return (GameTreeNode<?,?>) res;
		}
	}
	
}
