package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;

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

    public int getPieceLocation(Piece piece) {
        // If it's a detective, we can use the standard board method
        if (piece.isDetective()) return gameState.getDetectiveLocation((Piece.Detective) piece).get();

        // For Mr. X, we have to look at the last entry in the travel log
        // OR use the 'source' of his available moves since it's his turn.
        return gameState.getAvailableMoves().iterator().next().source();
    }
}