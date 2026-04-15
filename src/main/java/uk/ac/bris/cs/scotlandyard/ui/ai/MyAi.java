package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Nonnull;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

public class MyAi implements Ai {

	@Nonnull @Override public String name() { return "Name me!"; }

	private Integer breadthFirstSearch(GameSetup game, Board board, Predicate<Integer> target, int startingLocation) {

		Set<Integer> spotsToSearch = new HashSet<>();
		spotsToSearch.add(startingLocation);

		Set<Integer> spotsAlreadyChecked = new HashSet<>();
		spotsAlreadyChecked.add(startingLocation);

		int movesCount = 0;

		while (!spotsToSearch.isEmpty()) {
			//he's quite safe anyway at this point it's computationally expensive to search more
			if (movesCount==5){return movesCount;}

			Set<Integer> nextLevelOfSpots = new HashSet<>();

			for (int currentSpot : spotsToSearch) {

				//if a detective is at this spot we found the closest one
				if (target.test(currentSpot)) {
					return movesCount;
				}

				//look at all the paths connected to this spot
				for (int connectedSpot : game.graph.adjacentNodes(currentSpot)) {

					//if no checked this connected spot yet, add it to the list for the next "move"
					if (!spotsAlreadyChecked.contains(connectedSpot)) {
						if (canAnyDetectiveUseNode(game, board, currentSpot, connectedSpot)) {
							spotsAlreadyChecked.add(connectedSpot);
							nextLevelOfSpots.add(connectedSpot);
						}
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

	private boolean isCaught(VirtualState state) {
		return !state.gameState.getWinner().isEmpty();
	}

//	This is just a greedy algorithm
	private List<VirtualState> getDetectiveResponses(VirtualState state) {
	Board.GameState currentState = state.gameState;

	for (Piece player : currentState.getPlayers().asList()) {
		if (player.isDetective()) {
			// 1. CRITICAL: Check if any available move for this detective results in a win
			Move winningMove = null;
			for (Move move : currentState.getAvailableMoves()) {
				if (move.commencedBy().equals(player)) {
					// If this specific move ends the game, take it immediately!
					if (!currentState.advance(move).getWinner().isEmpty()) {
						winningMove = move;
						break;
					}
				}
			}

			if (winningMove != null) {
				currentState = currentState.advance(winningMove);
				// If a detective wins, we can stop simulating other detectives
				return List.of(new VirtualState(currentState));
			}

			// 2. If no winning move, use your existing greedy BFS logic
			Move bestMove = null;
			int minDistance = Integer.MAX_VALUE;
			int mrXLocation = state.getPieceLocation(Piece.MrX.MRX);

			for (Move move : currentState.getAvailableMoves()) {
				if (move.commencedBy().equals(player)) {
					int destination = getDestinations(move).get(getDestinations(move).size() - 1);
					int dist = breadthFirstSearch(currentState.getSetup(), currentState, n -> n == mrXLocation, destination);
					if (dist < minDistance) {
						minDistance = dist;
						bestMove = move;
					}
				}
			}
			if (bestMove != null) currentState = currentState.advance(bestMove);
		}
	}
	return List.of(new VirtualState(currentState));
}

	private float alphaBeta(VirtualState state, int depth, float alpha, float beta, boolean isMrX,int maxdepth){
		//base case
		if (depth==0 || isCaught(state)){
			return evaluateReward(state);
		}

		if (isMrX){
			float max= -Float.MAX_VALUE;
			//go through MrX moves
			for (Move move: state.getAvailableMoves()){
				//double moves are skipped wat lower depth to optimise as double moves only really matter for fast escape for near detectives;
				if (depth <maxdepth && move instanceof Move.DoubleMove) continue;

				VirtualState next = state.advance(move);

				float eval = alphaBeta(next,depth-1,alpha,beta,false, maxdepth);

				max=Math.max(max,eval);

				alpha= Math.max(alpha, eval);

				if (beta<= alpha){break;}
			}
			return max;
		}

		float min= Float.MAX_VALUE;

		for (VirtualState nextResponse : getDetectiveResponses(state)){
			float eval = alphaBeta(nextResponse, depth -1, alpha, beta, true, maxdepth);
			min=Math.min(min,eval);

			beta=Math.min(beta, eval);

			if (beta<=alpha){break;}
		}
		return min;
	}

	private boolean canAnyDetectiveUseNode(GameSetup game, Board board, Integer current, Integer connected){
		var requiredTransports = game.graph.edgeValueOrDefault(current, connected, ImmutableSet.of());
		// get all detectives
		List<Piece> detectives = board.getPlayers().stream()
				.filter(p-> p.isDetective())
				.toList();

		for (Piece det : detectives) {
			for (ScotlandYard.Transport t : requiredTransports) {
				//check if the detective has the specific ticket (Taxi, Bus, or Underground)
				//gets the count for each ticket type
				int count = board.getPlayerTickets(det)
						.map(tickets -> tickets.getCount(t.requiredTicket()))
						.orElse(0);
				//detective could use that edge
				if (count > 0) return true;
			}
		}
		return false;

	}

	//bfs search for nearest detective from the current potential node being tested
	private Integer distanceToDetective(GameSetup game,Board board, List<Piece> detectives, int myLocation) {
		return breadthFirstSearch(game, board, node->detectives.contains(node),myLocation);

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

//	evaluates heuristics
	private float evaluateReward(VirtualState state) {
		float penalty = 0;
		float finalReward;
		Board.GameState boardState=state.gameState;

		ImmutableSet<Piece> winner = boardState.getWinner();
		if (winner.contains(Piece.MrX.MRX)) return 10000.0f; //mrX Won
		if (!winner.isEmpty()) return -10000.0f;

		List<Piece> detectivePieces=boardState.getPlayers().stream().filter(p->p.isDetective()).toList();
		int mrXlocation= state.getPieceLocation(Piece.MrX.MRX);

		float detectiveDistance = distanceToDetective(boardState.getSetup(),boardState, detectivePieces, mrXlocation);

		int escapeRoutes = boardState.getSetup().graph.adjacentNodes(mrXlocation).size();

		//if on a hub
		boolean isAtHub = false;
		for (int neighbor : boardState.getSetup().graph.adjacentNodes(mrXlocation)) {
			var transports = boardState.getSetup().graph.edgeValueOrDefault(mrXlocation, neighbor, ImmutableSet.of());

			if (transports.contains(ScotlandYard.Transport.UNDERGROUND) || transports.contains(ScotlandYard.Transport.BUS)) {
				isAtHub = true;
				break;
			}
		}


		//prevent dead ends
		if (escapeRoutes <=2){penalty +=1000;}
		//prevent near misses with detectives
		if (detectiveDistance <= 2) {penalty += 2000.0f;}
		if (detectiveDistance <= 1) {penalty += 9000.0f;}


		//ticket weighting
		if (detectiveDistance <= 3) {
			finalReward = (detectiveDistance * 1000.0f) + (escapeRoutes * 15.0f) - penalty;
		} else {
			finalReward = (detectiveDistance * 100.0f) + (escapeRoutes * 10.0f) - penalty;
		}

		if (isAtHub) {
			finalReward += 100.0f; //reward staying near fast-travel points
		}
		//weight secret and double moves more
		int secrets= boardState.getPlayerTickets(Piece.MrX.MRX).map(t->t.getCount(ScotlandYard.Ticket.SECRET)).orElse(0);
		int doubles= boardState.getPlayerTickets(Piece.MrX.MRX).map(t->t.getCount(ScotlandYard.Ticket.DOUBLE)).orElse(0);

		int taxi= boardState.getPlayerTickets(Piece.MrX.MRX).map(t->t.getCount(ScotlandYard.Ticket.TAXI)).orElse(0);
		int bus= boardState.getPlayerTickets(Piece.MrX.MRX).map(t->t.getCount(ScotlandYard.Ticket.BUS)).orElse(0);
		int underground= boardState.getPlayerTickets(Piece.MrX.MRX).map(t->t.getCount(ScotlandYard.Ticket.UNDERGROUND)).orElse(0);

		finalReward= finalReward + secrets* 20.0f + doubles*50.0f + underground*10.0f + bus*5.0f + taxi;

		return finalReward;
    }

	private ImmutableMap<ScotlandYard.Ticket, Integer> getTicketMap(Board board, Piece piece) {
		Board.TicketBoard ticketBoard = board.getPlayerTickets(piece)
				.orElseThrow(() -> new IllegalArgumentException("Player not found"));

		Map<ScotlandYard.Ticket, Integer> map = new HashMap<>();
		for (ScotlandYard.Ticket t : ScotlandYard.Ticket.values()) {
			map.put(t, ticketBoard.getCount(t));
		}
		return ImmutableMap.copyOf(map);
	}

	private Board.GameState reconstructState(Board board, int mrXLocation) {
		// 1. Reconstruct MrX Player
		Player mrX = new Player(
				Piece.MrX.MRX,
				getTicketMap(board, Piece.MrX.MRX), // Use the helper here
				mrXLocation
		);

		// 2. Reconstruct Detectives
		List<Player> detectives = new ArrayList<>();
		for (Piece p : board.getPlayers()) {
			if (p.isDetective()) {
				int loc = board.getDetectiveLocation((Piece.Detective) p).orElseThrow();
				detectives.add(new Player(
						p,
						getTicketMap(board, p), // And here
						loc
				));
			}
		}

		// 3. Build using your Factory
		return new MyGameStateFactory().build(
				board.getSetup(),
				mrX,
				ImmutableList.copyOf(detectives)
		);
	}

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		var moves = board.getAvailableMoves().asList();
		Board.GameState state = reconstructState(board, moves.get(0).source());
		int depth=3;

		return moves.parallelStream()
				.map(move -> {
					int destination = getDestinations(move).get(getDestinations(move).size() - 1);
					float score = alphaBeta(new VirtualState(state.advance(move)),
							depth, -Float.MAX_VALUE, Float.MAX_VALUE, false, depth);
					return new AbstractMap.SimpleEntry<>(move, score);
				})
				// Find the entry with the highest score
				.max(Comparator.comparing(Map.Entry::getValue))
				.map(Map.Entry::getKey)
				.orElse(moves.get(0));
	}
}
