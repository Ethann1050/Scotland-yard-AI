package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.Nonnull;
import io.atlassian.fugue.Pair;
import org.checkerframework.checker.units.qual.A;
import uk.ac.bris.cs.scotlandyard.model.*;

public class MyAi implements Ai {

	@Nonnull @Override public String name() { return "Name me!"; }

	private Integer distanceToDetective(GameSetup setup, List<Player> detectives, int location) {
		Set<Integer> detectiveLocations = new HashSet<>();
		for (Player p : detectives) { detectiveLocations.add(p.location()); }

		ArrayList<Integer> visited = new ArrayList<Integer>();
		ArrayList<Integer> queue = new ArrayList<Integer>();

		visited.add(location);
		queue.add(location);

		int length = 0;

		while (queue.isEmpty()) {
			int currentNode = queue.get(0);
			queue.remove(0);
			length++;
			if (detectiveLocations.contains(currentNode)) {
				return length;
			}
			for (int node : setup.graph.adjacentNodes(currentNode)) {
				if (!visited.contains(node)) {
					queue.add(node);
				}
			}
		}
		return 0;
	}

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		HashMap<Move, Integer> scores = new HashMap<>();

		var moves = board.getAvailableMoves().asList();

		for (Move move : moves) {
//			scores.put(move, distanceToDetective(board.getSetup(), board));
		}
		return moves.get(new Random().nextInt(moves.size()));
	}
}
