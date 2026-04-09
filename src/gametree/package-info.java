
/**
 * Various classes for storing and generating game trees for the use by algorithms and tree visualisation.
 * <p>
 * This package contains <b>game tree</b> classes (such as search trees and depth-first trees) and <b>game position</b> classes (such as artificial game trees).
 * Game trees store the progress of the search of a game, whereas game positions represent the tree of the game itself, which generates its underlying structure.
 * <p>
 * The underlying structure of this package relies on the {@link GameTreeNode} <b>abstract</b> (for game trees) and the {@link IGamePosition} <b>interface</b> (for game positions).
 * <br>The main implementations used in this thesis are:
 * <ul>
 * <li>The {@link SearchTreeNode} (game tree) class for storing best-first trees for algorithms.</li>
 * <li>The {@link DepthFirstNode} (game tree) class for storing depth-first trees for algorithms.</li>
 * <li>The {@link ArtificialGamePosition} (game position) class for generating artificial game trees, the main domain we test algorithms on.</li>
 * <li>The {@link MetricKeeper} (utility) class used by algorithms to keep track of their computational cost during the search.</li>
 * </ul>
 * Additionally, we provide a more generalised class for generating artificial game trees with {@link VariantAGP}. This implementation
 * uses {@link Generator} interfaces to replace the branching factor and bounds generation aspects of artificial game trees with 
 * flexible function implementations.
 */
package gametree;