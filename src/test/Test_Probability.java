package test;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import algorithm.SearchAlgorithm;
import gametree.SearchTreeNode;
import utils.Probability;

public class Test_Probability {

//	@Test
	public void test1() {
		var n0 = new SimplePosition(true, 3, 10);
		var n1 = n0.addAdversarial(1, 10); // --> (1, 5)
		n0.addAdversarial(3, 10);
		n0.addAdversarial(2, 9);
		n1.addAdversarial(3, 10);
		n1.addAdversarial(1, 7);
		n1.addAdversarial(4, 5);
		var tree = SearchTreeNode.getTree(n0);
		var ts = tree.children();
		SearchAlgorithm.expand_tree_simple(tree, 3);
		System.out.println(ts);
		System.out.println(Arrays.toString(ts.get(0).survivalFunction(1)));
		System.out.println(Arrays.toString(ts.get(1).survivalFunction(1)));
		System.out.println(Arrays.toString(ts.get(2).survivalFunction(1)));
		double[] cdf = tree.CDF(3);
		double[] sdf = Probability.toSurvival(cdf);
		double[] cdf2 = Probability.toCDF(sdf);
		double[] pmf = Probability.cdfToPMF(cdf);
		System.out.println(Arrays.toString(cdf));
		System.out.println(Arrays.toString(sdf));
		System.out.println(Arrays.toString(cdf2));
		for (int i = -1; i <= cdf.length + 1; i++)
			System.out.printf("%.3f ", Probability.lesserOrEqual(cdf, 0, i));
		System.out.println();
		for (int i = -1; i <= sdf.length + 1; i++)
			System.out.printf("%.3f ", Probability.lesserOrEqualFromSurvival(sdf, 0, i));
		System.out.println();
		for (int i = -1; i <= cdf.length + 1; i++)
			System.out.printf("%.3f ", Probability.greaterOrEqualFromCDF(cdf, 0, i));
		System.out.println();
		for (int i = -1; i <= sdf.length + 1; i++)
			System.out.printf("%.3f ", Probability.greaterOrEqual(sdf, 0, i));
		System.out.println("\nProbability Mass Function:");
		for (int i=-1; i <= pmf.length + 1; i++)
			System.out.printf("%.3f ", Probability.equalTo(pmf, 0, i));
		System.out.println();
		for (int i=-1; i <= pmf.length + 1; i++)
			System.out.printf("%.3f ", Probability.equalToFromCDF(cdf, 0, i));
		System.out.println();
		for (int i=-1; i <= pmf.length + 1; i++)
			System.out.printf("%.3f ", Probability.equalToFromSurvival(sdf, 0, i));
		System.out.println();
	}
	
//	@Test
	public void test2() {
		double[] unknown = Probability.discreteUniform(0, 1, true);
		double[] disproven = Probability.discreteUniform(0, 0, true);
//		double[] proven = Probability.discreteUniform(1, 1, true);
		double[][] _test1 = new double[][] {disproven, disproven, disproven, unknown};
		double[][] _test2 = new double[][] {unknown, unknown, unknown, unknown, unknown, unknown};
		int[] lbs1 = new int[_test1.length];
		int[] lbs2 = new int[_test2.length];
		double[] test1 = Probability.minOrMaxDistribution(_test1, lbs1, 0, 1, true);
		double[] test2 = Probability.minOrMaxDistribution(_test2, lbs2, 0, 1, true);
		double[] pmf1 = Probability.toPMF(test1, true);
		double[] pmf2 = Probability.toPMF(test2, true);
		for (int i=-1; i <= pmf1.length; i++)
			System.out.printf("%.3f ", Probability.equalTo(pmf1, 0, i));
		System.out.println();
		for (int i=-1; i <= pmf1.length; i++)
			System.out.printf("%.3f ", Probability.equalTo(pmf2, 0, i));
		System.out.println();
	}
	
	@Test
	@SuppressWarnings("unused")
	public void test3() {
		// depth 0
		var p00 = new SimplePosition(0,1);
		// depth 1
		var p01 = p00.addNonAdversarial(0, 1);
		var p02 = p00.addAdversarial(0, 1);
		var p03 = p00.addAdversarial(0, 1);
		// depth 2
		var p04 = p01.addAdversarial(0, 1);
		var p05 = p01.addAdversarial(0, 1);
		var p06 = p02.addAdversarial(0, 1);
		var p07 = p02.addAdversarial(0, 1);
		var p08 = p03.addAdversarial(0, 1);
		var p09 = p03.addAdversarial(0, 1);
		// depth 3
		var p10 = p04.addAdversarial(0, 0);
		var p11 = p04.addAdversarial(0, 1);
		var p12 = p05.addAdversarial(0, 1);
		var p13 = p05.addAdversarial(0, 1);
		var p14 = p06.addAdversarial(0, 1);
		var p15 = p06.addAdversarial(0, 1);
		var p16 = p08.addAdversarial(0, 1);
		var p17 = p08.addAdversarial(0, 1);
		var p18 = p08.addAdversarial(0, 1);
		var p19 = p09.addAdversarial(0, 1);
		var p20 = p09.addAdversarial(0, 1);
		
		var tree = SearchTreeNode.getTree(p00);
		SearchAlgorithm.expand_tree_simple(tree, 3);
		System.out.println(tree.printTree(3, false, true));
		System.out.println(Arrays.toString(Probability.cdfToPMF(tree.CDF(3))));
		
		Object[] leaves = tree.getAllLeafnodes(5, n -> n.lowerbound() != n.upperbound()).toArray();
		Random r = new Random();
		double total = 0;
		int N = 1000000;
		for (int i=0; i < N; i++) {
			for (int l=0; l < leaves.length; l++) {
				@SuppressWarnings("unchecked")
				var node = (SearchTreeNode<SimplePosition>) leaves[l];
				node.position().clearChildren();
				if (r.nextBoolean())
					node.position().addAdversarial(0,0);
				else
					node.position().addAdversarial(1,1);
			}
			var tree2 = SearchTreeNode.getTree(p00);
			SearchAlgorithm.expand_tree_simple(tree2, 4);
			total += tree2.lowerbound();
		}
		System.out.println(total / (double) N);
	}
	
}
