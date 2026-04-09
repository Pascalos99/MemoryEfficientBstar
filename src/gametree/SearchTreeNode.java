package gametree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static gametree.MetricKeeper.*;

/**
 * Implementation of {@link GameTreeNode} that stores all nodes directly in memory. This 
 * search tree is editable through search only (by expanding nodes and adjusting their 
 * bounds), so the resulting tree always represents the game given by the {@link IGamePosition} 
 * implementation {@code P} correctly.
 * @param <P> some implementation of {@link IGamePosition} which corresponds to the game being 
 * searched by this tree.
 */
public class SearchTreeNode<P extends IGamePosition<P>> extends GameTreeNode<SearchTreeNode<P>, P> implements Serializable {

	/**
	 * Change whenever major changes to class structure are made.
	 * @see 5.6.1 from <a href=https://docs.oracle.com/javase/6/docs/platform/serialization/spec/version.html#6678>docs.oracle.com</a>
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * A class that stores the settings for a {@link SearchTreeNode} tree. All nodes 
	 * in the tree hold a reference to the same {@link Settings} object that was used to 
	 * initialise the tree. The {@code Settings} object also holds temporary information 
	 * such as whether the tree has been frozen (to prevent expansions) or whether pruning 
	 * is enabled (to reduce memory usage).
	 */
	public static class Settings implements Serializable {
		
		/**
		 * Change whenever major changes to class structure are made.
		 * @see 5.6.1 from <a href=https://docs.oracle.com/javase/6/docs/platform/serialization/spec/version.html#6678>docs.oracle.com</a>
		 */
		private static final long serialVersionUID = 1L;
		
		
		public boolean frozen = false;
		public boolean allow_manual_edits = false;
		public boolean prune_positions;
		public long position_pruning_depth;
		public boolean prune_irrelevance;
		public long irrelevance_pruning_depth;
		public boolean prune_irrelevance_light;
		
		public Settings() {
			this(false, 2, true, 2, true);
		}
		public Settings(long minimum_depth_to_prune_positions, long minimum_depth_to_prune_irrelevance) {
			this(true, minimum_depth_to_prune_positions, true, minimum_depth_to_prune_irrelevance, true);
		}
		public Settings(long minimum_depth_to_prune_positions, long minimum_depth_to_prune_irrelevance, boolean incorrect_bounds_protection) {
			this(true, minimum_depth_to_prune_positions, true, minimum_depth_to_prune_irrelevance, incorrect_bounds_protection);
		}
		/**
		 * [NOTE] if {@code 'enable_position_pruning'}, {@code 'enable_irrelevance_pruning'} and {@code 'incorrect_bounds_protection'} are all {@code true}, 
		 *   re-visited irrelevant nodes may trigger a {@code null} error. To prevent this, disable {@code 'enable_position_pruning'} in any trees with a 
		 *   (partially) incorrect evaluation function (bounds that are not 100% accurate).
		 * @param enable_position_pruning to enable the pruning of irrelevant game positions
		 *  (not recommended if the evaluation function has incorrect bounds)
		 * @param minimum_depth_to_prune_positions the minimum depth from which positions will be pruned
		 * @param enable_irrelevance_pruning to enable the pruning of irrelevant nodes
		 * @param minimum_depth_to_prune_irrelevance the minimum depth from which irrelevant nodes will be pruned
		 * @param incorrect_bounds_protection keeps nodes otherwise pruned by irrelevance pruning around, but only removes their children.
		 * Requires the {@link SearchTreeNode#position()} to <b>not</b> be {@code null} if ever revisited by the search.
		 */
		public Settings(boolean enable_position_pruning, long minimum_depth_to_prune_positions, 
				boolean enable_irrelevance_pruning, long minimum_depth_to_prune_irrelevance, boolean incorrect_bounds_protection) {
			prune_positions = enable_position_pruning;
			position_pruning_depth = minimum_depth_to_prune_positions;
			prune_irrelevance = enable_irrelevance_pruning;
			irrelevance_pruning_depth = minimum_depth_to_prune_irrelevance;
			prune_irrelevance_light = incorrect_bounds_protection;
		}
	}
	
	protected Settings settings;
	
	public Settings settings() {
		return settings;
	}
	
	/**
	 * children are kept in memory. This value may be {@code null} if the node has not yet 
	 * been expanded. The array will be empty if the node has been expanded and either (1) 
	 * its children have been pruned during search, or (2) it is a terminal node.
	 */
	private ArrayList<SearchTreeNode<P>> children;
	private boolean expanded, evaluated;
	private boolean maximising;
	private double lowerbound, upperbound;
	private long depthLower, depthUpper;
	
	public SearchTreeNode(Settings settings, SearchTreeNode<P> parent, P position, MetricKeeper...metrics) {
		super(parent, position, metrics);
		if (position == null) throw new NullPointerException("SearchTreeNode cannot be initialized with `null` game position");
		if (settings == null) throw new NullPointerException("SearchTreeNode cannot be initialized with `null` settings");
		this.settings = settings;
		maximising = position.maximising();
		depthLower = depthUpper = 0;
		expanded = evaluated = false;
		children = null;
		lowerbound = Double.NEGATIVE_INFINITY;
		upperbound = Double.POSITIVE_INFINITY;
	}
	public SearchTreeNode(Settings settings, P position, MetricKeeper... metrics) {
		this(settings, null, position, metrics);
	}
	
	public static <P extends IGamePosition<P>> SearchTreeNode<P> getTree(P start_position, 
			boolean prune_positions, long minimum_depth_to_prune_positions, boolean prune_irrelevance, long minimum_depth_to_prune_irrelevance,
			boolean incorrect_bounds_protection_for_irrelevance_pruning) {
		return new SearchTreeNode<P>(
				new Settings(prune_positions, minimum_depth_to_prune_positions, prune_irrelevance,
						minimum_depth_to_prune_irrelevance, incorrect_bounds_protection_for_irrelevance_pruning),
				start_position);
	}
	public static <P extends IGamePosition<P>> SearchTreeNode<P> getTree(P start_position, 
			boolean prune_positions, long minimum_depth_to_prune_positions, boolean prune_irrelevance, long minimum_depth_to_prune_irrelevance) {
		return getTree(start_position, prune_positions, minimum_depth_to_prune_positions, prune_irrelevance, minimum_depth_to_prune_irrelevance, !prune_positions);
	}
	public static <P extends IGamePosition<P>> SearchTreeNode<P> getTree(P start_position,
			long minimum_depth_to_prune_positions, long minimum_depth_to_prune_irrelevance) {
		return getTree(start_position, true, minimum_depth_to_prune_positions, true, minimum_depth_to_prune_irrelevance, false);
	}
	public static <P extends IGamePosition<P>> SearchTreeNode<P> getTree(P start_position,
			boolean prune_positions, boolean prune_irrelevance) {
		return getTree(start_position, prune_positions, 2, prune_irrelevance, 2, !prune_positions);
	}
	public static <P extends IGamePosition<P>> SearchTreeNode<P> getTree(P start_position) {
		return getTree(start_position, false, false);
	}
	
	/**
	 * Freezes or un-freezes the {@link SearchTreeNode} tree this node is a part of. Frozen 
	 * trees do not allow any node expansions, evaluations, or modifications to take place. 
	 * This is useful for displaying the tree without modifying it.
	 * @param frozen {@code true} to prevent new node expansions or evaluations. 
	 * Or {@code false} to enable new node expansions and evaluations. This setting 
	 * defaults to {@code true} for all new trees.
	 */
	public void setTreeFrozen(boolean frozen) {
		settings.frozen = frozen;
	}
	/** {@link #setTreeFrozen(boolean)} with {@code frozen = true}. Freezes the tree this node 
	 * is a part of - disabling any modifications until {@link #unfreeze()} or {@link #setTreeFrozen(boolean)} are called.
	 */
	public void freeze() {
		setTreeFrozen(true);
	}
	/** {@link #setTreeFrozen(boolean)} with {@code frozen = false}. Unfreezes the tree this node 
	 * is a part of - enabling all modifications until {@link #freeze()} or {@link #setTreeFrozen(boolean)} are called.
	 */
	public void unfreeze() {
		setTreeFrozen(false);
	}
	
	public void setAllowModifications(boolean set) {
		settings.allow_manual_edits = set;
	}
	public boolean allowModifications() {
		return settings.allow_manual_edits;
	}
	
	/**
	 * Enables pruning of game positions {@link #position()} values for nodes after they have been 
	 * expanded and evaluated. This does not retroactively remove game positions, but only enables it from 
	 * this moment on. A position that has been pruned can not be recovered. This only affects the functionality 
	 * of {@link #position()}, which will return {@code null} when a node is pruned, the tree still functions 
	 * normally in every other aspect. This reduces the memory usage of future expansions of the tree by 
	 * not keeping all game positions in memory. 
	 * <p>
	 * This can be disabled again by calling {@link #disablePositionPruning()} on any node of the same tree.
	 * @param minimum_depth_to_prune minimum depth from the root node (where {@code depth = 0}). This refers 
	 * to the root of the entire tree this node is in.
	 */
	public void enablePositionPruning(long minimum_depth_to_prune) {
		settings.position_pruning_depth = minimum_depth_to_prune;
		settings.prune_positions = true;
	}
	
	/**
	 * Disables pruning of game positions {@link #position()} values for nodes after they have been 
	 * expanded and evaluated - which is enabled by calling {@link #enablePositionPruning(long)} 
	 * on any node of the tree. Does not retroactively modify the tree in any way.
	 */
	public void disablePositionPruning() {
		settings.prune_positions = false;
	}
	
	/**
	 * Enables pruning of irrelevant children nodes in the entire tree. No nodes will be retroactively pruned. 
	 * Can be disabled by calling {@link #disableIrrelevancePruning()}.
	 * @param minimum_depth_to_prune the minimum depth at which nodes will be pruned. To preserve node structure at 
	 * the children of the root, set this value to {@code 2} or higher.
	 */
	public void enableIrrelevancePruning(long minimum_depth_to_prune) {
		settings.irrelevance_pruning_depth = minimum_depth_to_prune;
		settings.prune_irrelevance = true;
	}
	
	/**
	 * Disables pruning of irrelevant children nodes in the entire tree. Does not retroactively modify 
	 * the tree in any way. Nodes already pruned can not be restored. 
	 * Can be re-enabled by calling {@link #enableIrrelevancePruning(long)}.
	 */
	public void disableIrrelevancePruning() {
		settings.prune_irrelevance = false;
	}
	
	/**
	 * If set to {@code true}, enables incorrect-bounds protection for the pruning of irrelevant children nodes in the entire tree. 
	 * This ensures that nodes pruned by {@link #enableIrrelevancePruning(long)} are still kept in memory, 
	 * but only their children are removed. This ensures the node can still be revisited if it becomes relevant 
	 * again later (due to the evaluation function having incorrect bounds). It is important to note that this 
	 * may result in a {@code null} error if {@link #enablePositionPruning(long)} is enabled. 
	 * Can be disabled by calling setting to {@code false}. Defaults to {@code false}.
	 * <p>
	 * This does not retroactively modify the tree in any way. 
	 * @param set the value to set to
	 */
	public void enableIncorrectBoundsProtection(boolean set) {
		settings.prune_irrelevance_light = set;
	}

	@Override
	public ArrayList<SearchTreeNode<P>> _children(MetricKeeper... metrics) {
		// "children == null" is only true if this node has not been expanded OR if it has been pruned.
		// The decision was made that, if a node was pruned, but the algorithm requests its children again,
		//  that they should be re-generated, and that is what this "|| children == null" condition accomplishes.
		if (!expanded || children == null) {
			// if the tree has been frozen, no expansions are done, and the `expanded` flag stays `false`:
			if (settings.frozen) return new ArrayList<>();
			// otherwise, expand this node:
			Collection<P> child_positions = position().next();
			children = new ArrayList<>(child_positions.size());
			for (P position : child_positions) children.add(createChild(this, position));
			expanded = true;
			// update the metric-keepers of this node expansion:
			incrementExpansions(metrics);
			adjustNodeCount(children.size(), metrics);
			// prune this position if it has already been evaluated:
			prunePosition();
		}
		return children == null? new ArrayList<>() : children;
	}
	
	protected SearchTreeNode<P> createChild(SearchTreeNode<P> parent, P position) {
		return new SearchTreeNode<P>(parent.settings, parent, position, parent.getAttachedMetrics());
	}
	
	@Override
	public Optional<List<SearchTreeNode<P>>> savedChildren() {
		if (children == null) return Optional.empty();
		return Optional.of(children);
	}

	/**
	 * Prunes the {@link #position()} value of this node if enabled in the {@link #settings}. Only prunes 
	 * nodes at a minimum depth of {@link Settings#position_pruning_depth} that have already been expanded 
	 * and evaluated.
	 */
	public void prunePosition() {
		if (settings.prune_positions && !settings.frozen && expanded && evaluated && depth() >= settings.position_pruning_depth) clearPosition();
	}
	
	/**
	 * Evaluates this node if the tree has not been frozen in the {@link #settings}. Does nothing if the 
	 * node has already been evaluated.
	 * @param metrics An array of metric keepers to keep track of the evaluations made through this method
	 * @return {@code true} if the node has been evaluated. {@code false} if the node is not yet evaluated after this method call 
	 * (only when the tree is frozen and node not yet evaluated).
	 */
	public boolean evaluate(MetricKeeper... metrics) {
		if (!evaluated) {
			// if the tree has been frozen, no evaluations are done, and the `evaluated` flag stays `false`:
			if (settings.frozen) return false;
			// otherwise, evaluate this node:
			upperbound = position().upperbound();
			lowerbound = position().lowerbound();
			evaluated = true;
			// update the metric-keepers of this node evaluation:
			//  - both upper and lower are evaluated, so 2 evaluations are done
			incrementEvaluations(2, metrics);
			// prune this position if it has already been expanded: (not likely to occur)
			prunePosition();
		}
		return true;
	}
	
	public boolean setBounds(double lowerbound, double upperbound) {
		if (!settings.allow_manual_edits) throw new RuntimeException("Not allowed to edit node bounds, enable edits first!");
		boolean res = lowerbound != this.lowerbound || upperbound != this.upperbound;
		this.lowerbound = lowerbound;
		this.upperbound = upperbound;
		evaluated = true;
		return res;
	}
	
	public void removeChildren(boolean markAsPruned) {
		if (!settings.allow_manual_edits) return;
		children = null;
		expanded = markAsPruned;
	}
	
	public void setChildren(Collection<SearchTreeNode<P>> newChildren) {
		for (var child : newChildren) {
			child.setParent(this);
			child.settings = settings;
		}
		children = new ArrayList<>(newChildren);
		expanded = true;
		evaluated = true;
	}
	
	public boolean isEvaluated() {
		return evaluated;
	}
	
	@Override
	public double _upperbound(MetricKeeper... metrics) {
		// `evaluate` returns `false` if the tree is frozen, in that case we cannot give a value
		//  - the result is thus Not-A-Number:
		if (!evaluate(metrics)) return Double.NaN;
		return upperbound;
	}

	@Override
	public double _lowerbound(MetricKeeper... metrics) {
		// `evaluate` returns `false` if the tree is frozen, in that case we cannot give a value
		//  - the result is thus Not-A-Number:
		if (!evaluate(metrics)) return Double.NaN;
		return lowerbound;
	}
	
	@Override
	public boolean hasSavedBounds() {
		return evaluated;
	}
	
	@Override
	public boolean maximising() {
		return maximising;
	}

	@Override
	public boolean _adjustBounds(List<SearchTreeNode<P>> children, MetricKeeper... metrics) {
		// if the tree is frozen or there are no children, no modifications to the bounds are made, nothing has changed:
		if (settings.frozen || children.size() <= 0) return false;
		// otherwise, continue as normal:
		double[] lowerUpper = new double[] {lowerbound, upperbound};
		long[] depthLowerUpper = new long[] {depthLower, depthUpper};
		boolean res = GameTreeNode.adjustBounds(maximising(), lowerUpper, depthLowerUpper, children, metrics);
		lowerbound = lowerUpper[0];
		upperbound = lowerUpper[1];
		depthLower = depthLowerUpper[0];
		depthUpper = depthLowerUpper[1];
		evaluated = true;
		prunePosition();
		pruneIrrelevantChildren(metrics);
		return res;
	}
	
	public boolean pruneIrrelevantChildren(MetricKeeper[] m) {
		if (children == null || children.size()==0 || settings.frozen || !settings.prune_irrelevance) return false;
		boolean res = children.stream().anyMatch(c -> !c.isRelevant(m) && !c.isPruned());
		res |= children.removeIf(n -> {
			if (!n.isRelevant(m)) {
				// a node is irrelevant if (a) it falls OUTSIDE the adjusted parent bounds
				//  AND (b) does NOT define any of the parent's bounds
				if (n.depth() < settings.irrelevance_pruning_depth) {
					// children which are irrelevant but may not be removed as per the SearchTree settings.
					n.prune_irrelevant_subtree(m);
					return false;
				}
				if (!settings.prune_irrelevance_light) {
					adjustNodeCount(-n.countSavedSubTree(), m);
					return true;
				} else {
					adjustNodeCount(1 - n.countSavedSubTree(), m);
					n.children = null;
					n.expanded = true;
					return false;
				}
			}
			return false;
		});
		return res;
	}
	
	/**
	 * Prunes the sub-tree of this node starting from the minimum depth pruning is allowed at, 
	 * according to the {@link #settings} of this tree.
	 * @param m An array of metric keepers to keep track of node memory changes made by this method.
	 */
	private void prune_irrelevant_subtree(MetricKeeper[] m) {
		if (children == null || children.size()==0 || settings.frozen || !settings.prune_irrelevance) return;
		
		if (depth()+1 >= settings.irrelevance_pruning_depth) {
			adjustNodeCount(1 - countSavedSubTree(), m);
			children = null;
			expanded = true;
		} else for (var child : children)
			child.prune_irrelevant_subtree(m);
	}
	
	public boolean isPruned() {
		return expanded && children == null;
	}
	
	@Override
	public long _depthOfUpper(MetricKeeper... metrics) {
		return depthUpper;
	}

	@Override
	public long _depthOfLower(MetricKeeper... metrics) {
		return depthLower;
	}
	
	@Override
	public void attachMetrics(MetricKeeper... metrics) {
		super.attachMetrics(metrics);
		if (children != null && children.size() > 0)
			for (var child : children) child.attachMetrics(metrics);
	}
	
	@Override
	public String toString() {
		return String.format(Locale.CANADA, "%s%.1f-%.1f%s<=={%s}", maximising?"[":"(",lowerbound,upperbound,maximising?"]":")",position());
	}

}
