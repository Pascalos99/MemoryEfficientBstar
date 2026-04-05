package test;

import org.junit.jupiter.api.Test;

import algorithm.BstarSquaredSimple;
import gametree.ArtificialGamePosition.Settings;
import gametree.MetricKeeper;

import static algorithm.StrategyFunction.*;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class Test_BstarSquaredSimple {

	
	@Test
	public void test1() {
		var tree = new Settings(true,8000260,6,100,4,1.5,0.5).getTree();
		var algo = new BstarSquaredSimple(P_AX1, P_AX1, P_AX1);
		MetricKeeper m = new MetricKeeper("Total");
		var res = algo.search(tree, Duration.ofSeconds(30), m);
		assertTrue(res.complete());
	}
	
	@Test
	public void test2() {
		var tree = new Settings(true,8000963,6,100,4,1.5,0.5).getTree();
		var algo = new BstarSquaredSimple(B_AR, PROVEBEST, PROVEBEST);
		MetricKeeper m = new MetricKeeper("Total");
		var res = algo.search(tree, Duration.ofSeconds(30), m);
		assertTrue(res.complete());
	}
	
	@Test
	public void test3() {
		var tree = new Settings(true,8000928,6,100,4,1.5,0.5).getTree();
		var algo = new BstarSquaredSimple(P_AX1, P_AX1, P_AX1);
		MetricKeeper m = new MetricKeeper("Total");
		var res = algo.search(tree, Duration.ofSeconds(30), m);
		assertTrue(res.complete());
	}
	
	@Test
	public void test4() {
		var tree = new Settings(true,8000356,6,100,4,1.5,0.5).getTree();
		var algo = new BstarSquaredSimple(P_AX1, ALTERNATE());
		MetricKeeper m = new MetricKeeper("Total");
		var res = algo.search(tree, Duration.ofSeconds(30), m);
		assertTrue(res.complete());
	}
	
	@Test
	public void test6() {
		var tree = new Settings(true,8000628,6,100,4,1.5,0.5).getTree();
		var algo = new BstarSquaredSimple(P_AX1, P_AX1, P_AX1);
		MetricKeeper m = new MetricKeeper("Total");
		var res = algo.search(tree, Duration.ofSeconds(30), m);
		assertTrue(res.complete());
	}
	
	@Test
	public void test7() {
		var tree = new Settings(true,8000347,6,100,4,1.5,0.5).getTree();
		var algo = new BstarSquaredSimple(B_AR, PROVEBEST, PROVEBEST);
		MetricKeeper m = new MetricKeeper("Total");
		var res = algo.search(tree, Duration.ofSeconds(30), m);
		assertTrue(res.complete());
	}
}
