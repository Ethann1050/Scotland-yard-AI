package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Nonnull;
import io.atlassian.fugue.Pair;
import org.checkerframework.checker.units.qual.A;
import uk.ac.bris.cs.scotlandyard.model.*;

public class MyAi implements Ai {

	@Nonnull @Override public String name() { return "Name me!"; }

	private Integer distanceToDetective(GameSetup setup, List<Integer> detectives, int start) {
		Set<Integer> targets = new HashSet<>(detectives);

		Queue<Integer> queue = new LinkedList<>();
		Map<Integer, Integer> distance = new HashMap<>();

		queue.add(start);
		distance.put(start, 0);

		while (!queue.isEmpty()) {
			int current = queue.poll();

			if (targets.contains(current)) {
				return distance.get(current);
			}

			for (int next : setup.graph.adjacentNodes(current)) {
				if (!distance.containsKey(next)) {
					distance.put(next, distance.get(current) + 1);
					queue.add(next);
				}
			}
		}
		return Integer.MAX_VALUE;
	}

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
				return -1.0f;
			}
		}

//		gets the last destination regardless of single or double move.
		int finalDestination = destinations.get(destinations.size() - 1);

		float detectiveDistance = distanceToDetective(board.getSetup(), detectives, finalDestination);

		int escapeRoutes = board.getSetup().graph.adjacentNodes(finalDestination).size();

		if (move instanceof Move.DoubleMove) {
			if (detectiveDistance >= 3) {
				penalty += 15.0f; // Only double-move if detectives are close
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

		float finalReward = (detectiveDistance * 2.0f) + (escapeRoutes * 0.3f) - penalty;

		if (detectiveDistance <= 2) {
			finalReward *= 0.001f;
		}

		return finalReward;
    }


	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		ImmutableSet<Piece> pieces = board.getPlayers();
		ImmutableSet<Piece> immutableDetectives = pieces.stream().filter(Piece::isDetective).collect(ImmutableSet.toImmutableSet());
		List<Integer> detectives = immutableDetectives.stream().map(piece -> board.getDetectiveLocation((Piece.Detective) piece)).flatMap(Optional::stream).toList();

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
