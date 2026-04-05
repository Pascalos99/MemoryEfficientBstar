package test;

import java.util.List;
import java.util.Optional;

import gametree.GameTreeNode;
import gametree.IGamePosition;
import gametree.MetricKeeper;

import static gametree.MetricKeeper.*;

/**
 * Implementation of {@link GameTreeNode} that only acts as a facade for the underlying 
 * tree expressed by the {@code P} implementation of {@link IGamePosition}. No values are 
 * saved beyond the direct parental line of ancestors of each node and the depth of each node.
 * @param <P> some implementation of {@link IGamePosition} which corresponds to the game being 
 * searched by this tree.
 */
public class SimpleNode<P extends IGamePosition<P>> extends GameTreeNode<SimpleNode<P>, P> {

	/**
	 * Change whenever major changes to class structure are made.
	 * @see 5.6.1 from <a href=https://docs.oracle.com/javase/6/docs/platform/serialization/spec/version.html#6678>docs.oracle.com</a>
	 */
	private static final long serialVersionUID = 1L;
	
	public SimpleNode(P position) {
		this(null,position);
	}
	public SimpleNode(SimpleNode<P> parent, P position) {
		super(parent, position);
		if (position == null) throw new NullPointerException("SimpleNode cannot be initialized with `null` game position");
	}

	@Override
	public List<SimpleNode<P>> _children(MetricKeeper... metrics) {
		incrementExpansions(metrics);
		return position().next().stream().map(p -> new SimpleNode<P>(this, p)).toList();
	}
	
	@Override
	public Optional<List<SimpleNode<P>>> savedChildren() {
		return Optional.empty();
	}

	@Override
	public double _upperbound(MetricKeeper... metrics) {
		incrementEvaluations(metrics);
		return position().upperbound();
	}

	@Override
	public double _lowerbound(MetricKeeper... metrics) {
		incrementEvaluations(metrics);
		return position().lowerbound();
	}
	
	@Override
	public boolean hasSavedBounds() {
		return false;
	}
	
	@Override
	public boolean maximising() {
		return position().maximising();
	}

	@Override
	public boolean _adjustBounds(List<SimpleNode<P>> children, MetricKeeper... metrics) {
		return false;
	}

	@Override
	public long _depthOfUpper(MetricKeeper... metrics) {
		return 0;
	}

	@Override
	public long _depthOfLower(MetricKeeper... metrics) {
		return 0;
	}
	
	@Override
	public String toString() {
		return "node(%s,d=%d)".formatted(position(),depth());
	}
}
