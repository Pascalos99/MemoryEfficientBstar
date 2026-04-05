package gametree;

import java.util.Collection;

/**
 * This interface represents a game position in any game which can be modelled as an adversarial or non-adversarial game.
 * <p>
 * Either one player takes action in each move with a series of moves leading to a final state or score, OR, one player 
 * aims to maximise a score against another player aiming to minimise that score.
 * <p>
 * The 'minimising player' can be more 
 * than one player, but applying this model to multi-player games assumes a strategy akin to Paranoid Search. This 
 * is when the maximising player <i>p</i> assumes all other players are cooperating to minimise <i>p</i>'s score.
 * 
 * @author Pascal Anema
 * @version 1.0
 */
public interface IGamePosition<P extends IGamePosition<P>> {

	/**
	 * @return The collection of game positions directly reachable by moves from the current player to move 
	 * in this game position.
	 */
	Collection<P> next();
	
	/**
	 * The upper-bound value is the upper bound on the game-theoretical mini-max value of this game position.
     * <p>
     * For the maximising player this is the best value achievable under perfect play.
     * <p>
     * For the minimising player this is the worst-case value achievable under perfect play.
     * @return The upper-bound value for this game position
     */
	double upperbound();
	
	/**
	 * The lower-bound value is the lower bound on the game-theoretical mini-max value of this game position.
     * <p>
     * For the maximising player this is the minimum value that can be guaranteed under perfect play.
     * <p>
     * For the minimising player this is the best value achievable under perfect play.
     * @return The lower-bound value for this game position
     */
	double lowerbound();
	
	/**
     * @return {@code true} if the current player-to-move is maximising the score,
     * or {@code false} if it is minimising the score.
     */
	boolean maximising();
	
	/**
	 * This method should be implemented with a hash that still remains different from similar states 
	 * if a mask is applied to the hash. Commonly, a transposition table implementation may only use 
	 * the first 20 to 26 bits of the hash to use as an index, so the hash should try to minimise 
	 * collisions regardless of how many bits are included for the index.
	 * @return a 64-bit zobrist hash or other kind of game-state hash that represents this state.
	 */
	long hash();
	
}
