package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

public class VirtualState {
    public final Board.GameState gameState;

    VirtualState(Board.GameState gameState) {
        this.gameState = gameState;
    }

    public VirtualState advance(Move move) {
        return new VirtualState(gameState.advance(move));
    }

    public ImmutableSet<Move> getAvailableMoves() {
        return gameState.getAvailableMoves();
    }
}