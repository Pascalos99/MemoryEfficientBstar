package gametree;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Creates a tree of position type Q from a source tree with position type P with the help of a P-to-Q transformer.
 * @param <N> Type of the source tree
 * @param <P> Type of the source position
 * @param <Q> Type of the target position
 */
public class ResultTreeNode<N extends GameTreeNode<N, P>, P extends IGamePosition<P>, Q extends IGamePosition<Q>> extends GameTreeNode<ResultTreeNode<N,P,Q>, Q> {
	
	/**
	 * Change whenever major changes to class structure are made.
	 * @see 5.6.1 from <a href=https://docs.oracle.com/javase/6/docs/platform/serialization/spec/version.html#6678>docs.oracle.com</a>
	 */
	private static final long serialVersionUID = 1L;
	
	public final N original;
	public final Function<P, Q> transformer;
	
	public ResultTreeNode(ResultTreeNode<N,P,Q> parent, N node, Function<P, Q> transformer) {
		super(parent, node.position() == null ? null : transformer.apply(node.position()));
		original = node;
		this.transformer = transformer;
	}
	public ResultTreeNode(N tree, Function<P, Q> transformer) {
		this(null, tree, transformer);
	}

	@Override
	public boolean maximising() {
		return original.maximising();
	}

	@Override
	protected List<ResultTreeNode<N, P, Q>> _children(MetricKeeper... metrics) {
		return original.children(metrics).stream().map(n -> new ResultTreeNode<N,P,Q>(this, n, transformer)).toList();
	}
	
	@Override
	public Optional<List<ResultTreeNode<N, P, Q>>> savedChildren() {
		var ogSaved = original.savedChildren();
		if (ogSaved.isEmpty()) return Optional.empty();
		return Optional.of(ogSaved.get().stream().map(n -> new ResultTreeNode<N,P,Q>(this, n, transformer)).toList());
	}

	@Override
	protected double _upperbound(MetricKeeper... metrics) {
		return original.upperbound(metrics);
	}

	@Override
	protected double _lowerbound(MetricKeeper... metrics) {
		return original.lowerbound(metrics);
	}
	
	@Override
	public boolean hasSavedBounds() {
		return original.hasSavedBounds();
	}

	@Override
	public boolean adjustBounds(boolean useExpansions, MetricKeeper... metrics) {
		return original.adjustBounds(useExpansions, metrics);
	}
	
	@Override
	protected boolean _adjustBounds(List<ResultTreeNode<N,P,Q>> children, MetricKeeper... metrics) {
		return original.adjustBounds(children.stream().map(c -> c.original).toList(), metrics);
	}

	@Override
	protected long _depthOfUpper(MetricKeeper... metrics) {
		return original.depthOfUpper(metrics);
	}

	@Override
	protected long _depthOfLower(MetricKeeper... metrics) {
		return original.depthOfLower(metrics);
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ResultTreeNode)) return false;
		try {
		@SuppressWarnings("unchecked")
		var O = (ResultTreeNode<N,P,Q>) o;
			return (O.original == null && original == null) || O.original.equals(original);
		} catch (Exception e) {
			return false;
		}
	}
	
	@Override
	public String toString() {
		return "{"+original.toString()+"}";
	}
}
