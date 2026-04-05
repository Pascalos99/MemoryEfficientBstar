package test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import gametree.IGamePosition;

/**
 * A basic implementation of {@link IGamePosition} which allows for adding of children to the tree and setting their values. 
 * Modification of the tree beyond the addition of children is not supported. This class is meant for use in tests mainly, 
 * and thus has limited functionality for other purposes.
 */
public class SimplePosition implements IGamePosition<SimplePosition>, Serializable {

	/**
	 * Change whenever major changes to class structure are made.
	 * @see 5.6.1 from <a href=https://docs.oracle.com/javase/6/docs/platform/serialization/spec/version.html#6678>docs.oracle.com</a>
	 */
	private static final long serialVersionUID = 1L;
	
	SimplePosition parent;
	private ArrayList<SimplePosition> children;
	
	final double lowerbound, upperbound;
	final boolean maximising;
	
	public SimplePosition(SimplePosition parent, boolean maximising, double lowerbound, double upperbound) {
		this.maximising = maximising;
		this.lowerbound = lowerbound;
		this.upperbound = upperbound;
		if (!(upperbound >= lowerbound)) throw new IllegalArgumentException(
				"lowerbound ("+lowerbound+") must be smaller than upperbound ("+upperbound+")");
		this.parent = parent;
		this.children = new ArrayList<>();
		if (parent != null) parent.addChild(this);
	}
	public SimplePosition(boolean maximising, double lowerbound, double upperbound) {
		this(null, maximising, lowerbound, upperbound);
	}
	public SimplePosition(double lowerbound, double upperbound) {
		this(null, true, lowerbound, upperbound);
	}
	
	public void addChild(SimplePosition child) {
		if (!children.contains(child)) children.add(child);
		child.parent = this;
	}
	
	public SimplePosition addChild(boolean maximising, double lowerbound, double upperbound) {
		SimplePosition child = new SimplePosition(this, maximising, lowerbound, upperbound);
		addChild(child);
		return child;
	}
	
	public SimplePosition addAdversarial(double lowerbound, double upperbound) {
		return addChild(!maximising, lowerbound, upperbound);
	}
	
	public SimplePosition addNonAdversarial(double lowerbound, double upperbound) {
		return addChild(maximising, lowerbound, upperbound);
	}
	
	public void clearChildren() {
		children.clear();
	}
	
	@Override
	public Collection<SimplePosition> next() {
		return Collections.unmodifiableList(children);
	}

	@Override
	public double upperbound() {
		return upperbound;
	}

	@Override
	public double lowerbound() {
		return lowerbound;
	}

	@Override
	public boolean maximising() {
		return maximising;
	}

	@Override
	public long hash() {
		long h = hashCode();
		return h | (h << 32);
	}
	
	@Override
	public String toString() {
		return String.format(Locale.CANADA, "%s%.1f-%.1f%s", maximising?"[":"(",lowerbound,upperbound,maximising?"]":")");
	}
	
}
