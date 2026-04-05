package gametree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import algorithm.Table;

import static gametree.MetricKeeper.*;

/**
 * GameTreeNode implementation that does not store children nodes, but does store the depth of bounds and the bounds themselves. 
 * Updates to the bounds are recorded, but the structure of the tree is not. This is particularly useful for 
 * depth-first algorithms. As minimal information is stored, yet most GameTreeNode functionality is supported as usual.
 * Note that {@link #savedChildren()} will always return {@link Optional#empty()} and {@link #children(MetricKeeper...)} will 
 * always perform a node expansion.
 * @param <P> The game position to store in this DepthFirstNode.
 */
public class DepthFirstNode<P extends IGamePosition<P>> extends GameTreeNode<DepthFirstNode<P>, P>  {

	/**
	 * Change whenever major changes to class structure are made.
	 * @see 5.6.1 from <a href=https://docs.oracle.com/javase/6/docs/platform/serialization/spec/version.html#6678>docs.oracle.com</a>
	 */
	private static final long serialVersionUID = 1L;
	
	private double lower, upper;
	private long depthLower, depthUpper;
	private boolean evaluated;
	
	public static <P extends IGamePosition<P>> DepthFirstNode<P> getTree(P root, boolean saveRootChildren, MetricKeeper... metrics) {
		if (saveRootChildren)
			return new RootDFNode<P>(root, metrics);
		return new DepthFirstNode<P>(root, metrics);
	}
	
	private DepthFirstNode(P position, MetricKeeper... metrics) {
		this(null, position, metrics);
	}
	
	protected DepthFirstNode(DepthFirstNode<P> parent, P position, MetricKeeper... metrics) {
		super(parent, position, metrics);
		lower = Double.NEGATIVE_INFINITY;
		upper = Double.POSITIVE_INFINITY;
		depthUpper = depthLower = 0;
		evaluated = false;
	}

	@Override
	public boolean maximising() {
		return position().maximising();
	}

	@Override
	protected List<DepthFirstNode<P>> _children(MetricKeeper... metrics) {
		incrementExpansions(metrics);
		return position().next().stream().map(p -> new DepthFirstNode<P>(this, p, metrics)).toList();
	}

	@Override
	public Optional<List<DepthFirstNode<P>>> savedChildren() {
		return Optional.empty();
	}

	@Override
	protected double _upperbound(MetricKeeper... metrics) {
		evaluate(metrics);
		return upper;
	}

	@Override
	protected double _lowerbound(MetricKeeper... metrics) {
		evaluate(metrics);
		return lower;
	}
	
	/**
	 * Update the bounds of this node with information from a provided transposition table
	 * @param TT The transposition table
	 * @return {@code true} if this node's bounds were updated, {@code false} otherwise.
	 */
	public boolean readFromTT(Table TT) {
		long hash = position().hash();
		if (TT.isPresent(hash)) {
			double L = TT.lower(hash), U = TT.upper(hash);
			if (!evaluated || (U - L < upper - lower) && (U >= lower && L <= upper)) {
				// only update bounds if the stored bounds are better
				lower = L; upper = U;
				TT.countRetrieval();
				evaluated = true;
				return true;
			}
		}
		return false;
	}
	
	private void evaluate(MetricKeeper... metrics) {
		if (!evaluated) {
			lower = position().lowerbound();
			upper = position().upperbound();
			incrementEvaluations(2, metrics);
			evaluated = true;
		}
	}

	@Override
	public boolean hasSavedBounds() {
		return evaluated;
	}

	@Override
	protected boolean _adjustBounds(List<DepthFirstNode<P>> children, MetricKeeper... metrics) {
		if (children.size() <= 0) return false;
		double[] lowerUpper = new double[] {lower, upper};
		long[] depthLowerUpper = new long[] {depthLower, depthUpper};
		boolean res = GameTreeNode.adjustBounds(maximising(), lowerUpper, depthLowerUpper, children, metrics);
		lower = lowerUpper[0];
		upper = lowerUpper[1];
		depthLower = depthLowerUpper[0];
		depthUpper = depthLowerUpper[1];
		evaluated = true;
		return res;
	}

	@Override
	protected long _depthOfUpper(MetricKeeper... metrics) {
		return depthUpper;
	}

	@Override
	protected long _depthOfLower(MetricKeeper... metrics) {
		return depthLower;
	}
	
	/**
	 * An optional variant of the DepthFirstNode which stores the nodes at depth 1 in memory.
	 * All other nodes are still DepthFirstNode instances, so only the first depth is stored, 
	 * the rest is discarded during search.
	 * @param <P> The type of IGamePosition
	 */
	private static class RootDFNode<P extends IGamePosition<P>> extends DepthFirstNode<P> {
		
		/**
		 * Change whenever major changes to class structure are made.
		 * @see 5.6.1 from <a href=https://docs.oracle.com/javase/6/docs/platform/serialization/spec/version.html#6678>docs.oracle.com</a>
		 */
		private static final long serialVersionUID = 1L;
		
		private ArrayList<DepthFirstNode<P>> children;
		private boolean expanded;
		
		public RootDFNode(P position, MetricKeeper... metrics) {
			super(null, position, metrics);
			children = null;
			expanded = false;
		}
		
		@Override
		public List<DepthFirstNode<P>> _children(MetricKeeper... metrics) {
			if (!expanded || children == null) {
				Collection<P> child_positions = position().next();
				children = new ArrayList<>(child_positions.size());
				// the root node stores only depth-first nodes, so no other nodes store a list of children
				for (P position : child_positions) children.add(new DepthFirstNode<P>(this, position, getAttachedMetrics()));
				expanded = true;
				// update the metric-keepers of this node expansion:
				incrementExpansions(metrics);
				adjustNodeCount(children.size(), metrics);
			}
			return children == null? new ArrayList<>() : children;
		}
		
		@Override
		public Optional<List<DepthFirstNode<P>>> savedChildren() {
			if (children == null) return Optional.empty();
			return Optional.of(children);
		}
		
	}
	
	@Override
	public String toString() {
		return String.format(Locale.CANADA, "%s%.1f-%.1f%s<=={%s}", maximising()?"[":"(",lower,upper,maximising()?"]":")",position());
	}

}
