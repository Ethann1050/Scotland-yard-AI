package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Nonnull;
import io.atlassian.fugue.Pair;
import org.checkerframework.checker.units.qual.A;
import uk.ac.bris.cs.scotlandyard.model.*;

import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Transport.FERRY;

public class MyAi implements Ai {

	@Nonnull @Override public String name() { return "Name me!"; }

	private Integer breadthFirstSearch(GameSetup game, Predicate<Integer> target, int startingLocation) {

		List<Integer> spotsToSearch = new ArrayList<>();
		spotsToSearch.add(startingLocation);

		List<Integer> spotsAlreadyChecked = new ArrayList<>();
		spotsAlreadyChecked.add(startingLocation);

		int movesCount = 0;

		while (!spotsToSearch.isEmpty()) {
			List<Integer> nextLevelOfSpots = new ArrayList<>();

			for (int currentSpot : spotsToSearch) {

				//if a detective is at this spot we found the closest one
				if (target.test(currentSpot)) {
					return movesCount;
				}

				//look at all the paths connected to this spot
				for (int connectedSpot : game.graph.adjacentNodes(currentSpot)) {

					//if no checked this connected spot yet, add it to the list for the next "move"
					if (!spotsAlreadyChecked.contains(connectedSpot)) {
						spotsAlreadyChecked.add(connectedSpot);
						nextLevelOfSpots.add(connectedSpot);
					}
				}
			}

			//finished everything one count away move to next count away
			spotsToSearch = nextLevelOfSpots;
			movesCount = movesCount + 1;
		}

		//if we searched the whole map and found no detectives
		return -1;
	}

	//bfs search for nearest detective from the current potential node being tested
	private Integer distanceToDetective(GameSetup game, List<Integer> detectives, int myLocation) {
		return breadthFirstSearch(game, node -> detectives.contains(node), myLocation);

	}

	//gets the destination from a move
	private List<Integer> getDestinations(Move move) {
		return move.accept(new Move.Visitor<>() {
			@Override
			public List<Integer> visit(Move.SingleMove move) {
				return List.of(move.destination);
			}

			@Override
			public List<Integer> visit(Move.DoubleMove move) {
				return List.of(move.destination1, move.destination2);
			}
		});
	}
	//gets the ticket from a move
	private List<ScotlandYard.Ticket> getTicketUsed(Move move) {
		return move.accept(new Move.Visitor<List<ScotlandYard.Ticket>>() {
			@Override
			public List<ScotlandYard.Ticket> visit(Move.SingleMove move) {
				return List.of(move.ticket);
			}

			@Override
			public List<ScotlandYard.Ticket> visit(Move.DoubleMove move) {
				return List.of(move.ticket1, move.ticket2);
			}
		});
	}

//	evaluates heuristics
	private float reward(Board board, Move move, List<Integer> detectives) {
		float penalty = 0;

		List<Integer> destinations = getDestinations(move);
		for (int destination : destinations) {
			if (detectives.contains(destination)){
				return -1000.0f;
			}
		}

//		gets the last destination regardless of single or double move.
		int finalDestination = destinations.get(destinations.size() - 1);

		float detectiveDistance = distanceToDetective(board.getSetup(), detectives, finalDestination);

		int escapeRoutes = board.getSetup().graph.adjacentNodes(finalDestination).size();

		if (move instanceof Move.DoubleMove) {
			if (detectiveDistance >= 3) {
				penalty += 15.0f; // Only double-move if detectives are close
			} else {
				penalty += 5.0f;
			}
		}

		for (ScotlandYard.Ticket ticket : getTicketUsed(move)) {
			if (ticket.equals(ScotlandYard.Ticket.UNDERGROUND)) {
				penalty += 2.0f;
			} else if (ticket.equals(ScotlandYard.Ticket.BUS)) {
				penalty += 1.0f;
			} else if (ticket.equals(ScotlandYard.Ticket.SECRET)) {
				penalty += 3.0f;
			}
		}

		if (detectiveDistance <= 2) {
			penalty += 100.0f;
		}

		float finalReward;

		if (detectiveDistance <= 3) {
			finalReward = (detectiveDistance * 4.0f) + (escapeRoutes * 0.3f) - penalty;
		} else {
			finalReward = (detectiveDistance) + (escapeRoutes * 2.0f) - penalty;
		}

		return finalReward;
    }


	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		ImmutableSet<Piece> pieces = board.getPlayers();
		ImmutableSet<Piece> immutableDetectives = pieces.stream().filter(piece->piece.isDetective()).collect(ImmutableSet.toImmutableSet());
		List<Integer> detectives = immutableDetectives.stream().map(piece -> board.getDetectiveLocation((Piece.Detective) piece)).flatMap(optional->optional.stream()).toList();

		var moves = board.getAvailableMoves().asList();
		Move bestMove = moves.get(0);
		float maxScore = -Float.MAX_VALUE;

		for (Move move : moves) {
			float score = reward(board, move, detectives);
			if (score > maxScore) {
				maxScore = score;
				bestMove = move;
			}
		}

		return bestMove;
	}
}
