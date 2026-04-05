package experiment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Supplier;

import algorithm.DfBstar;
import algorithm.SearchAlgorithm.SearchResult;
import algorithm.StrategyFunction;
import algorithm.Table;
import gametree.IGamePosition;

/**
 * This class is part of the preliminary experiments performed on df-B* to find the values for 
 *  sigma and epsilon chosen in the thesis for the core experiments. Which were found to at best
 *  be sigma=0.1 and epsilon=0.3
 */
public class TestDfBstar {
	
	public static double[] SWEEP_VALUES = {0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.15, 0.2, 0.3, 0.4, 0.45};

	public static <P extends IGamePosition<P>> Result sweep(P root, Supplier<StrategyFunction> sf, int TTsize, Duration time_limit) {
		int N = SWEEP_VALUES.length;
		int[][] sizes = new int[N][N];
		double[][] times = new double[N][N];
		long[][] expansions = new long[N][N];
		long[][] evaluations = new long[N][N];
		long[][] maxNodes = new long[N][N];
		long[][] softCols = new long[N][N];
		long[][] hardCols = new long[N][N];
		long[][] TTuses = new long[N][N];
		boolean[][] solved = new boolean[N][N];
		int[][] solution = new int[N][N];
		for (int i=0; i < N; i++) {
			for (int j=0; j < N; j++) {
				double sigma = SWEEP_VALUES[i];
				double epsilon = SWEEP_VALUES[j];
				DfBstar alg = new DfBstar(sf.get(), sigma, epsilon);
				SearchResult<?,?> result;
				{
					Table TT = Table.getTable(TTsize);
					if (TT.keySize() != TTsize)
						System.err.println("Could not make table of specified size ("+TTsize+"), got "+TT.keySize()+" instead.");
					Instant A = Instant.now();
					result = alg.search(root, TT, time_limit);
					times[i][j] = Duration.between(A, Instant.now()).toMillis() / 1000d;
					sizes[i][j] = TT.keySize();
					var TTM = TT.getMetrics();
					softCols[i][j] = TTM.softCollisions();
					hardCols[i][j] = TTM.hardCollisions();
					TTuses[i][j] = TTM.timesRetrieved();
				}
				System.gc();
				var M = result.mainMetrics();
				expansions[i][j] = M.expansions();
				evaluations[i][j] = M.evaluations();
				maxNodes[i][j] = M.maxObservedNodes();
				solved[i][j] = result.complete();
				solution[i][j] = result.bestMoveIndex();
			}
		}
		return new Result(SWEEP_VALUES, sizes, times, expansions, evaluations, maxNodes, softCols, hardCols, TTuses, solved, solution);
	}
	
	public static record Result(
			double[] vals, int[][] sizes,
			double[][] times, long[][] expansions, long[][] evaluations, long[][] maxNodes,
			long[][] softCols, long[][] hardCols, long[][] TTuses,
			boolean[][] solved, int[][] solution)
	{
		public void toFile(String filename) throws IOException {
			toFile(filename, false);
		}
		public void toFile(String filename, boolean append) throws IOException {
			boolean exists = new File(filename).exists();
			FileWriter fw = new FileWriter(filename, append);
			if (!append || !exists) fw.append("sigma,epsilon,size,time,expanded,evaluated,nodes,soft_collisions,hard_collisions,TT_uses,solved,solution\n");
			int N = vals.length;
			for (int i=0; i < N; i++)
				for (int j=0; j < N; j++)
					fw.append(String.format(Locale.CANADA, "%f,%f,%d,%f,%d,%d,%d,%d,%d,%d,%s,%d\n",
							vals[i],
							vals[j],
							sizes[i][j],
							times[i][j],
							expansions[i][j],
							evaluations[i][j],
							maxNodes[i][j],
							softCols[i][j],
							hardCols[i][j],
							TTuses[i][j],
							solved[i][j] ? "yes" : "no",
							solution[i][j]));
			fw.close();
		}
	}
	
}
