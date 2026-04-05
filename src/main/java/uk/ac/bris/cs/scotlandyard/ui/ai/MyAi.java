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

//	private Integer distanceToDetective(GameSetup setup, List<Integer> detectives, int location) {
//        Set<Integer> detectiveLocations = new HashSet<>(detectives);
//
//		ArrayList<Integer> visited = new ArrayList<Integer>();
//		ArrayList<Integer> queue = new ArrayList<Integer>();
//
//		visited.add(location);
//		queue.add(location);
//
//
//		int length = 0;
//
//		while (!queue.isEmpty()) {
//			int currentNode = queue.get(0);
//			queue.remove(0);
//			length++;
//			if (detectiveLocations.contains(currentNode)) {
//				return length;
//			}
//			for (int node : setup.graph.adjacentNodes(currentNode)) {
//				if (!visited.contains(node)) {
//					queue.add(node);
//					visited.add(node);
//				}
//			}
//		}
//		return 0;
//	}

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
		return Integer.MAX_VALUE; // no path
	}

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		int currentMax = 0;
		Move bestMove=null;

		ImmutableSet<Piece> pieces = board.getPlayers();
		ImmutableSet<Piece> immutableDetectives = pieces.stream().filter(piece -> piece.isDetective()).collect(ImmutableSet.toImmutableSet());
		//makes the optional into a stream of one item of data and adds that to the list
		List<Integer> detectives = immutableDetectives.stream().map(piece -> board.getDetectiveLocation((Piece.Detective) piece)).flatMap(opt->opt.stream()).toList();

		var moves = board.getAvailableMoves().asList();

		// checks which type of move it is and gets the destination from it
		for (Move move : moves) {
			int finalDestination = move.accept(new Move.Visitor<Integer>() {
				@Override
				public Integer visit(Move.SingleMove singleMove) {
					// Accesses the public final field in SingleMove
					return singleMove.destination;
				}

				@Override
				public Integer visit(Move.DoubleMove doubleMove) {
					// Accesses the public final field in DoubleMove for the FINAL stop
					return doubleMove.destination2;
				}
			});
			//checking new best value and hence best move
			int newDistance=distanceToDetective(board.getSetup(), detectives, finalDestination);
			if (newDistance>currentMax){currentMax=newDistance; bestMove=move;}
		}

		return bestMove;
	}
}
