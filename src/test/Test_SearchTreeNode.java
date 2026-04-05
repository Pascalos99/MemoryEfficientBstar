package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import gametree.MetricKeeper;
import gametree.SearchTreeNode;
import gametree.SearchTreeNode.Settings;

class Test_SearchTreeNode {
	
	@Test
	void testNormalBehaviour() {
		var root = new SimplePosition(0, 100);
		var c0 = root.addAdversarial(10, 90);
		var c1 = root.addAdversarial(20, 70);
		var c2 = root.addAdversarial(-10, 60);
		var c0c0 = c0.addAdversarial(20, 90);
		var c0c1 = c0.addAdversarial(30, 65);
		// testing expansion metrics being recorded correctly
		MetricKeeper m = new MetricKeeper();
		assertEquals(0, m.expansions());
		assertEquals(0, m.evaluations());
		var tree = SearchTreeNode.getTree(root);
		var ts = tree.children(m);
		var t0 = ts.get(0);
		var t1 = ts.get(1);
		var t2 = ts.get(2);
		assertEquals(1, m.expansions());
		assertEquals(0, m.evaluations());
		var t0s = t0.children(m);
		var t0t0 = t0s.get(0);
		var t0t1 = t0s.get(1);
		assertEquals(2, m.expansions());
		assertEquals(0, m.evaluations());
		// testing depth being computed
		assertEquals(0, tree.depth());
		assertTrue(ts.stream().map(SearchTreeNode::depth).allMatch(l -> l == 1));
		assertTrue(t0s.stream().map(SearchTreeNode::depth).allMatch(l -> l == 2));
		// testing maximising flag being kept correctly
		assertTrue(tree.maximising());
		assertTrue(ts.stream().map(SearchTreeNode::maximising).allMatch(b -> !b));
		assertTrue(t0s.stream().map(SearchTreeNode::maximising).allMatch(b -> b));
		// do evaluations get recorded correctly?
		assertEquals(100, tree.upperbound(m));
		assertEquals(2, m.evaluations());
		assertEquals(0, tree.lowerbound(m));
		assertEquals(2, m.evaluations());
		// test adjustBounds()
		assertTrue(tree.adjustBounds(m));
		/* we know that... `root` has three children, each of which need to get evaluated in order to 
		 * adjust the root's bounds - leading to 6 evaluations (+2 that were there already = 8) */
		assertEquals(2, m.expansions());
		assertEquals(8, m.evaluations());
		/* the new values of 'root' should be ... max[10,20,-10] and max[90,70,60], which gives [20,90] */
		assertEquals(20, tree.lowerbound(m));
		assertEquals(90, tree.upperbound(m));
		assertEquals(1, tree.depthOfLower(m));
		assertEquals(1, tree.depthOfUpper(m));
		// no evaluations or expansions should be needed (values stay the same as they were)
		assertEquals(2, m.expansions());
		assertEquals(8, m.evaluations());
		// let's see if all metricKeepers get updated when something changes
		MetricKeeper m2 = new MetricKeeper();
		assertTrue(t0.adjustBounds(m, m2));
		assertEquals(2, m.expansions());
		assertEquals(0, m2.expansions());
		assertEquals(12, m.evaluations());
		assertEquals(4, m2.evaluations());
		// this should not update the metricKeepers
		assertTrue(tree.adjustBounds(m, m2));
		assertEquals(2, m.expansions());
		assertEquals(0, m2.expansions());
		assertEquals(12, m.evaluations());
		assertEquals(4, m2.evaluations());
		// now let's see if the values of the tree are accurately altered:
		/* the new values of 't0' should be ... min[20,30] and min[90,65], which gives [20,65] */
		assertEquals(20, t0.lowerbound(m));
		assertEquals(65, t0.upperbound(m));
		assertEquals(1, t0.depthOfLower(m));
		assertEquals(1, t0.depthOfUpper(m));
		/* the new values of `root` should be ... max[20,20,-10] and max[65,70,60], which gives [20,70] */
		assertEquals(20, tree.lowerbound(m));
		assertEquals(70, tree.upperbound(m));
		assertEquals(2, tree.depthOfLower(m)); // this value comes from t0, so depth is 2 (t0 has altered bounds)
		assertEquals(1, tree.depthOfUpper(m)); // this value comes from t1, so depth is 1
		// test that no more modifications are possible (as the tree is complete)
		assertFalse(tree.adjustBounds(m, m2));
		assertFalse(t0.adjustBounds(m, m2));
		assertFalse(t1.adjustBounds(m, m2));
		assertFalse(t2.adjustBounds(m, m2));
		assertFalse(t0t0.adjustBounds(m, m2));
		assertFalse(t0t1.adjustBounds(m, m2));
		// test if positions are accurately kept:
		assertEquals(root, tree.position());
		assertEquals(c0, t0.position());
		assertEquals(c1, t1.position());
		assertEquals(c2, t2.position());
		assertEquals(c0c0, t0t0.position());
		assertEquals(c0c1, t0t1.position());
	}
	
	@Test
	void testExtraBehaviour() {
		var root = new SimplePosition(0, 100); // depth == 0
		var c0 = root.addAdversarial(20,90); // depth == 1
		var c1 = root.addAdversarial(30,80);
		var c2 = c0.addAdversarial(25, 75); // depth == 2
		var c3 = c0.addAdversarial(20, 80);
		var c4 = c1.addAdversarial(30, 90);
		var c5 = c1.addAdversarial(40, 70);
		var c6 = c2.addAdversarial(30, 60); // depth == 3
		var c7 = c2.addAdversarial(40, 70);
		var c8 = c3.addAdversarial(10, 75);
		var c9 = c3.addAdversarial(25, 60);
		var tree = SearchTreeNode.getTree(root, true, 2, false, 2, true);
		assertEquals(root, tree.position());
		MetricKeeper m = new MetricKeeper();
		assertTrue(tree.adjustBounds(m));
		// adjusting the bounds without evaluating the node first skips an evaluation step
		// this can be useful when the evaluation function is costly.
		assertEquals(30, tree.lowerbound(m));
		assertEquals(90, tree.upperbound(m));
		assertEquals(1, m.expansions());
		// if evaluations are skipped, they don't count for the metrics:
		assertEquals(4, m.evaluations());
		// now, if we ask for the children of the root, no extra expansion is done:
		var ts = tree.children(m);
		assertEquals(1, m.expansions());
		var t0 = ts.get(0);
		var t1 = ts.get(1);
		assertEquals(c0, t0.position());
		assertEquals(c1, t1.position());
		// test freezing capabilities
		tree.setTreeFrozen(true);
		var t_empty = t0.children(m);
		assertEquals(0, t_empty.size());
		assertFalse(t0.adjustBounds(m));
		assertEquals(30, tree.lowerbound(m));
		assertEquals(90, tree.upperbound(m));
		assertEquals(1, m.expansions());
		assertEquals(4, m.evaluations());
		// now to un-freeze
		tree.setTreeFrozen(false);
		var t0s = t0.children(m);
		var t2 = t0s.get(0);
		var t3 = t0s.get(1);
		// check correct adjustments of bounds
		assertTrue(t2.adjustBounds(m));
		assertEquals(40, t2.lowerbound(m));
		assertEquals(70, t2.upperbound(m));
		assertEquals(3, m.expansions());
		assertEquals(8, m.evaluations());
		assertTrue(t3.adjustBounds(m));
		assertEquals(25, t3.lowerbound(m));
		assertEquals(75, t3.upperbound(m));
		assertEquals(4, m.expansions());
		assertEquals(12, m.evaluations());
		assertTrue(t0.adjustBounds(m));
		assertEquals(25, t0.lowerbound(m));
		assertEquals(70, t0.upperbound(m));
		assertEquals(4, m.expansions());
		assertEquals(12, m.evaluations());
		// test correct pruning of positions
		assertNull(t2.position());
		assertNull(t3.position());
		var t2s = t2.children(m);
		var t6 = t2s.get(0);
		var t7 = t2s.get(1);
		var t3s = t3.children(m);
		var t8 = t3s.get(0);
		var t9 = t3s.get(1);
		assertEquals(c6, t6.position());
		assertEquals(c7, t7.position());
		assertEquals(c8, t8.position());
		assertEquals(c9, t9.position());
		t7.freeze();
		assertFalse(t6.adjustBounds(m));
		assertEquals(c6, t6.position());
		t7.disablePositionPruning();
		t0.unfreeze();
		t6.adjustBounds(m);
		assertEquals(c6, t6.position());
		assertEquals(5, m.expansions());
		assertEquals(12, m.evaluations());
		t3.enablePositionPruning(3);
		assertFalse(t7.adjustBounds(m));
		assertNull(t7.position());
		assertEquals(0, t8.children(m).size());
		assertNull(t8.position());
		assertEquals(7, m.expansions());
		assertEquals(12, m.evaluations());
		// test correct non-pruning of positions
		var t1s = t1.children(m);
		assertEquals(8, m.expansions());
		assertEquals(12, m.evaluations());
		var t4 = t1s.get(0);
		var t5 = t1s.get(1);
		assertEquals(30, t1.lowerbound(m));
		assertEquals(80, t1.upperbound(m));
		assertEquals(8, m.expansions());
		assertEquals(12, m.evaluations());
		assertTrue(t1.adjustBounds(m));
		assertEquals(8, m.expansions());
		assertEquals(16, m.evaluations());
		assertEquals(c1, t1.position());
		assertFalse(t4.adjustBounds(m));
		assertFalse(t5.adjustBounds(m));
		t1.freeze();
		assertEquals(c4, t4.position());
		assertEquals(c5, t5.position());
		t1.unfreeze();
		assertTrue(tree.adjustBounds(m));
		assertEquals(30, t1.lowerbound(m));
		assertEquals(70, t1.upperbound(m));
		assertEquals(30, tree.lowerbound(m));
		assertEquals(70, tree.upperbound(m));
		assertEquals(3, t9.depth());
	}
	
	@Test
	void testEdgeCases() {
		var root = new SimplePosition(0, 100);
		var tree = new SearchTreeNode<>(new Settings(), root);
		tree.freeze();
		assertEquals(Double.NaN, tree.upperbound());
		assertEquals(Double.NaN, tree.lowerbound());
		assertFalse(tree.adjustBounds());
		assertThrows(NullPointerException.class, () -> new SearchTreeNode<SimplePosition>(null, root));
		assertThrows(NullPointerException.class, () -> new SearchTreeNode<SimplePosition>(new Settings(), null));
		assertThrows(IllegalArgumentException.class, () -> root.addAdversarial(90, 20));
		assertEquals(0, tree.children().size());
		root.addNonAdversarial(10, 90);
		assertEquals(0, tree.children().size());
		tree.unfreeze();
		assertEquals(1, tree.children().size());
		assertThrows(IllegalArgumentException.class, () -> root.addAdversarial(Double.NaN, 0));
		assertThrows(IllegalArgumentException.class, () -> root.addAdversarial(0, Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> root.addAdversarial(Double.NaN, Double.NaN));
	}
}
