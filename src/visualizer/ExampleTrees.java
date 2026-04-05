package visualizer;

import test.SimplePosition;

public class ExampleTrees {
	
	public static SimplePosition berliner1979() {
		SimplePosition n0 = new SimplePosition(0, 30);
		var n1 = n0.addAdversarial(15, 30);
		var n2 = n0.addAdversarial(8 , 22);
		var n3 = n0.addAdversarial(10, 19);
		
		var n4 = n1.addAdversarial(15, 26);
		var n5 = n1.addAdversarial(19, 25);
		n1.addAdversarial(22, 30);
		n2.addAdversarial(8 , 15);
		n3.addAdversarial(10, 14);
		
		n4.addAdversarial(22, 26);
		n5.addAdversarial(23, 25);
		return n0;
	}
	
	public static SimplePosition bstarDB2025() {
		SimplePosition n0 = new SimplePosition(35, 70);
		var n1 = n0.addAdversarial(0, 70);
		n0.addAdversarial(10, 65);
		n0.addAdversarial(35, 50);
		
		n1.addAdversarial(0, 90);
		var n5 = n1.addAdversarial(14, 70);
		n1.addAdversarial(40, 80);
		
		n5.addAdversarial(0, 70);
		n5.addAdversarial(14, 40);
		n5.addAdversarial(10, 25);
		return n0;
	}
}
