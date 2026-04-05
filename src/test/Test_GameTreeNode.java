package test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import gametree.GameTreeNode;
import gametree.SearchTreeNode;

class Test_GameTreeNode {
	
	/**
	 * Tests expected output from {@link GameTreeNode#findBest2(java.util.Collection, boolean, boolean, gametree.MetricKeeper...)}. 
	 * This test looks for normal outputs from a single set of three children with no ties.
	 */
	@Test
	void findBest2_test1() {
		var root = new SimplePosition(30, 90);
		var c0 = root.addAdversarial(10, 80);
		var c1 = root.addAdversarial(20, 90);
		var c2 = root.addAdversarial(30, 70);
		var tree = new SimpleNode<>(root);
		
		// maximising
		var rMaxUpp = GameTreeNode.mostOptimistic2(tree.children(), true);
		var rMaxLow = GameTreeNode.leastPessimistic2(tree.children(), true);
		// minimising
		var rMinLow = GameTreeNode.mostOptimistic2(tree.children(), false);
		var rMinUpp = GameTreeNode.leastPessimistic2(tree.children(), false);
		
		assertEquals(c1, rMaxUpp.best().position());
		assertEquals( 1, rMaxUpp.bestindex());
		assertEquals(c0, rMaxUpp.secondbest().position());
		assertEquals( 0, rMaxUpp.best2index());
		
		assertEquals(c2, rMaxLow.best().position());
		assertEquals( 2, rMaxLow.bestindex());
		assertEquals(c1, rMaxLow.secondbest().position());
		assertEquals( 1, rMaxLow.best2index());
		
		assertEquals(c0, rMinLow.best().position());
		assertEquals( 0, rMinLow.bestindex());
		assertEquals(c1, rMinLow.secondbest().position());
		assertEquals( 1, rMinLow.best2index());
		
		assertEquals(c2, rMinUpp.best().position());
		assertEquals( 2, rMinUpp.bestindex());
		assertEquals(c0, rMinUpp.secondbest().position());
		assertEquals( 0, rMinUpp.best2index());
	}
	
	/**
	 * Tests expected output from {@link GameTreeNode#findBest2(java.util.Collection, boolean, boolean, gametree.MetricKeeper...)}. 
	 * This test looks for normal outputs from a single set of four children with ties.
	 */
	@Test
	void findBest2_test2() {
		var root = new SimplePosition(50,90);
		var c0 = root.addAdversarial(30, 90);
		var c1 = root.addAdversarial(50, 90);
		var c2 = root.addAdversarial(50, 70);
		var c3 = root.addAdversarial(30, 70);
		var tree = new SimpleNode<>(root);
		
		// maximising
		var rMaxUpp = GameTreeNode.mostOptimistic2(tree.children(), true);
		var rMaxLow = GameTreeNode.leastPessimistic2(tree.children(), true);
		// minimising
		var rMinLow = GameTreeNode.mostOptimistic2(tree.children(), false);
		var rMinUpp = GameTreeNode.leastPessimistic2(tree.children(), false);
		
		assertEquals(c1, rMaxUpp.best().position());
		assertEquals( 1, rMaxUpp.bestindex());
		assertEquals(c0, rMaxUpp.secondbest().position());
		assertEquals( 0, rMaxUpp.best2index());
		
		assertEquals(c1, rMaxLow.best().position());
		assertEquals( 1, rMaxLow.bestindex());
		assertEquals(c2, rMaxLow.secondbest().position());
		assertEquals( 2, rMaxLow.best2index());
		
		assertEquals(c3, rMinLow.best().position());
		assertEquals( 3, rMinLow.bestindex());
		assertEquals(c0, rMinLow.secondbest().position());
		assertEquals( 0, rMinLow.best2index());
		
		assertEquals(c3, rMinUpp.best().position());
		assertEquals( 3, rMinUpp.bestindex());
		assertEquals(c2, rMinUpp.secondbest().position());
		assertEquals( 2, rMinUpp.best2index());
	}
	
	/**
	 * Tests expected output from {@link GameTreeNode#findBest2(java.util.Collection, boolean, boolean, gametree.MetricKeeper...)}. 
	 * This test looks for the expected behaviour when a node is included with {@link Double#MAX_VALUE} in their bounds.
	 */
	@Test
	void findBest2_test3() {
		var root = new SimplePosition(0,Double.MAX_VALUE);
		var c0 = root.addAdversarial(-Double.MAX_VALUE, Double.MAX_VALUE);
		var c1 = root.addAdversarial(0, 0);
		var tree = new SimpleNode<>(root);
		// maximising
		var rMaxUpp = GameTreeNode.mostOptimistic2(tree.children(), true);
		var rMaxLow = GameTreeNode.leastPessimistic2(tree.children(), true);
		// minimising
		var rMinLow = GameTreeNode.mostOptimistic2(tree.children(), false);
		var rMinUpp = GameTreeNode.leastPessimistic2(tree.children(), false);
		
		
		assertEquals(c0, rMaxUpp.best().position());
		assertEquals( 0, rMaxUpp.bestindex());
		assertEquals(c1, rMaxUpp.secondbest().position());
		assertEquals( 1, rMaxUpp.best2index());
		
		assertEquals(c1, rMaxLow.best().position());
		assertEquals( 1, rMaxLow.bestindex());
		assertEquals(c0, rMaxLow.secondbest().position());
		assertEquals( 0, rMaxLow.best2index());
		
		assertEquals(c0, rMinLow.best().position());
		assertEquals( 0, rMinLow.bestindex());
		assertEquals(c1, rMinLow.secondbest().position());
		assertEquals( 1, rMinLow.best2index());
		
		assertEquals(c1, rMinUpp.best().position());
		assertEquals( 1, rMinUpp.bestindex());
		assertEquals(c0, rMinUpp.secondbest().position());
		assertEquals( 0, rMinUpp.best2index());
	}
	
	@Test
	void isRelevant_test1() {
		var root = new SimplePosition(0, 100);
		root.addAdversarial(0, 0); // irrelevant
		root.addAdversarial(0, 100); // relevant
		root.addAdversarial(0, 10); // relevant
		root.addAdversarial(0, 0); // irrelevant
		root.addAdversarial(-5, 10); // relevant
		root.addAdversarial(-10, 0); // irrelevant
		root.addAdversarial(-10, -5); // irrelevant
		var tree = new SimpleNode<>(root);
		var ts = tree.children();
		assertFalse(ts.get(0).isRelevant(), "first irrelevant point-value incorrectly declared relevant");
		assertTrue(ts.get(1).isRelevant());
		assertTrue(ts.get(2).isRelevant());
		assertFalse(ts.get(3).isRelevant(), "second irrelevant point-value incorrectly declared relevant");
		assertTrue(ts.get(4).isRelevant());
		assertFalse(ts.get(5).isRelevant());
		assertFalse(ts.get(6).isRelevant());
		var searchtree = SearchTreeNode.getTree(root, 3, 1);
		assertTrue(searchtree.evaluate());
		assertFalse(searchtree.adjustBounds());
		searchtree.adjustBounds();
		System.out.println(searchtree.printTree(2, true));
		
		// these nodes would change the parent bounds, but they should be counted as relevant still:
		root.addAdversarial(100,100); // relevant
		root.addAdversarial(50, 60); // relevant
		root.addAdversarial(50, 100); // relevant
		root.addAdversarial(90, 130); // relevant
		root.addAdversarial(100, 150); // relevant
		var ts2 = tree.children();
		assertTrue(ts2.get(7).isRelevant());
		assertTrue(ts2.get(8).isRelevant());
		assertTrue(ts2.get(9).isRelevant());
		assertTrue(ts2.get(10).isRelevant());
		assertTrue(ts2.get(11).isRelevant());
	}
	
	@Test
	void isRelevant_test2() {
		var root = new SimplePosition(0, 100);
		root.addAdversarial(0, 0); // relevant
		root.addAdversarial(-50, 100); // relevant
		root.addAdversarial(-20, 50); // relevant
		root.addAdversarial(-20, 0); // irrelevant
		root.addAdversarial(-30, -5); // irrelevant
		root.addAdversarial(0, 0); // irrelevant
		var tree = new SimpleNode<>(root);
		var ts = tree.children();
		assertTrue(ts.get(0).isRelevant(), "first (0,0) incorrectly declared irrelevant");
		assertTrue(ts.get(1).isRelevant());
		assertTrue(ts.get(2).isRelevant());
		assertFalse(ts.get(3).isRelevant());
		assertFalse(ts.get(4).isRelevant());
		assertFalse(ts.get(5).isRelevant(), "second (0,0) incorrectly declared relevant");
		var searchtree = SearchTreeNode.getTree(root, 3, 1);
		assertTrue(searchtree.evaluate());
		assertFalse(searchtree.adjustBounds());
		searchtree.adjustBounds();
		System.out.println(searchtree.printTree(2, true));
	}
	
	@Test
	void isRelevant_test3() {
		// same tests as above, but for minimising instead
		var root = new SimplePosition(false,0,100);
		root.addAdversarial(0, 100); // relevant
		root.addAdversarial(100, 100); // irrelevant
		root.addAdversarial(40,140); // relevant
		root.addAdversarial(80, 100);  // relevant
		root.addAdversarial(100, 100); // irrelevant
		root.addAdversarial(100, 140); // irrelevant
		root.addAdversarial(120, 200); // irrelevant
		var tree = new SimpleNode<>(root);
		var ts = tree.children();
		assertTrue(ts.get(0).isRelevant());
		assertFalse(ts.get(1).isRelevant(), "first irrelevant point-value incorrectly declared relevant");
		assertTrue(ts.get(2).isRelevant());
		assertTrue(ts.get(3).isRelevant());
		assertFalse(ts.get(4).isRelevant(), "second irrelevant point-value incorrectly declared relevant");
		assertFalse(ts.get(5).isRelevant());
		assertFalse(ts.get(6).isRelevant());
		
		var root2 = new SimplePosition(false,0,100);
		root2.addAdversarial(100, 100); // relevant
		root2.addAdversarial(0, 120); // relevant
		root2.addAdversarial(40, 200); // relevant
		root2.addAdversarial(100, 100); // irrelevant
		root2.addAdversarial(100, 120); // irrelevant
		root2.addAdversarial(130, 500); // irrelevant
		var tree2 = new SimpleNode<>(root2);
		var ts2 = tree2.children();
		assertTrue(ts2.get(0).isRelevant());
		assertTrue(ts2.get(1).isRelevant());
		assertTrue(ts2.get(2).isRelevant());
		assertFalse(ts2.get(3).isRelevant());
		assertFalse(ts2.get(4).isRelevant());
		assertFalse(ts2.get(5).isRelevant());
	}
}
