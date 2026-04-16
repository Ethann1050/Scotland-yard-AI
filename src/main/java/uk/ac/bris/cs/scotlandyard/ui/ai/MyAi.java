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
import uk.ac.bris.cs.scotlandyard.model.Move.*;


public class MyAi implements Ai {

	@Nonnull @Override public String name() { return "Name me!"; }

	private Integer breadthFirstSearch(GameSetup game, Board board, Predicate<Integer> target, int startingLocation, Piece detective) {

		Set<Integer> spotsToSearch = new HashSet<>();
		spotsToSearch.add(startingLocation);

		Set<Integer> spotsAlreadyChecked = new HashSet<>();
		spotsAlreadyChecked.add(startingLocation);

		int movesCount = 0;

		while (!spotsToSearch.isEmpty()) {
			//he's quite safe anyway at this point it's computationally expensive to search more
			if (movesCount==6){return movesCount;}

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
						if (canThisDetectiveUseNode(game, board, currentSpot, connectedSpot, detective)) {
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

	private List<VirtualState> getDetectiveResponses(VirtualState state) {
		Board.GameState currentState = state.gameState;
		int mrXLocation = state.getPieceLocation(Piece.MrX.MRX);
		Set<Integer> mrXExits = new HashSet<>(currentState.getSetup().graph.adjacentNodes(mrXLocation));

		//loop through detectives
		for (Piece player : currentState.getPlayers().asList()) {
			if (player.isDetective()) {
				Move bestMove = null;
				float bestScore = Float.MAX_VALUE;
// go through all detective moves available
				ImmutableSet<Move> availableMoves = currentState.getAvailableMoves();
				for (Move move : availableMoves) {
					if (move.commencedBy().equals(player)) {

						Board.GameState nextState = currentState.advance(move);
						//checks for a winner
						if (!nextState.getWinner().isEmpty()) {
							return List.of(new VirtualState(nextState));
						}

						int destination = getDestinations(move).get(getDestinations(move).size() - 1);
						int distance = breadthFirstSearch(currentState.getSetup(), currentState, n -> n == mrXLocation, destination, player);

						float currentMoveScore=distance;

						//higher score if mrX exit is blocked
						if (mrXExits.contains(destination)){
							//looking for lowest possible distance so negatives
							currentMoveScore -=2.5;
						}
						// choose score with the lowest distance
						if (currentMoveScore < bestScore) {
							bestScore=currentMoveScore;
							bestMove = move;
						}
					}
				}
				if (bestMove != null) {
					currentState = currentState.advance(bestMove);
					//remove exit that is covered
					mrXExits.remove(getDestinations(bestMove).get(0));
				}
			}
		}
		return List.of(new VirtualState(currentState));
	}

	private float alphaBeta(VirtualState state, int depth, float alpha, float beta, boolean isMrX, int maxDepth, long deadline){
//     base case
		if (System.currentTimeMillis() > deadline) {
			throw new RuntimeException();
		}

		if (depth==0 || isCaught(state)){
			return evaluateReward(state);
		}

		if (isMrX){
			float max= -Float.MAX_VALUE;
//        go through MrX moves
			for (Move move: state.getAvailableMoves()){
//           double moves are skipped at lower depth to optimise as double moves only really matter for fast escape if detectives are near
				if (depth < maxDepth && move.accept(new Visitor<Boolean>() {
					@Override public Boolean visit(SingleMove m) { return false; }
					@Override public Boolean visit(DoubleMove m) { return true; }
				})) continue;

				VirtualState next = state.advance(move);

				float eval = alphaBeta(next,depth-1,alpha,beta,false, maxDepth, deadline);

				max=Math.max(max,eval);

				alpha= Math.max(alpha, eval);

				if (beta<= alpha){break;}
			}
			return max;
		}

		float min= Float.MAX_VALUE;
// look at the best possible detective response based on getDetectiveResponses
		for (VirtualState nextResponse : getDetectiveResponses(state)){
			float eval = alphaBeta(nextResponse, depth -1, alpha, beta, true, maxDepth, deadline);
			min=Math.min(min,eval);

			beta=Math.min(beta, eval);

			if (beta<=alpha){break;}
		}
		return min;
	}

	private boolean canThisDetectiveUseNode (GameSetup game, Board board, Integer current, Integer connected, Piece detective) {
		ImmutableSet<ScotlandYard.Transport> requiredTransports = game.graph.edgeValueOrDefault(current, connected, ImmutableSet.of());

		for (ScotlandYard.Transport t : requiredTransports) {
//        check if the detective has the specific ticket (Taxi, Bus, or Underground)
//        gets the count for each ticket type
			int count = board.getPlayerTickets(detective)
					.map(tickets -> tickets.getCount(t.requiredTicket()))
					.orElse(0);
//        detective could use that edge
			if (count > 0) return true;
		}
		return false;
	}

	//  bfs search for nearest detective from the current potential node being tested
	private Integer distanceToDetective(GameSetup game,Board board, List<Piece> detectives, int myLocation) {
		int closestDistance = Integer.MAX_VALUE;
		int currentDistance = Integer.MAX_VALUE;
		for (Piece detective : detectives) {
			int detectiveLocation = board.getDetectiveLocation( (Piece.Detective) detective).orElse(-1);

			currentDistance = breadthFirstSearch(game, board, node -> node == myLocation, detectiveLocation, detective);
			if (currentDistance != -1 && currentDistance < closestDistance) {
				closestDistance = currentDistance;
			}
		}
		return closestDistance;

	}

	//  gets the destination from a move uses visitor pattern
	private List<Integer> getDestinations(Move move) {
		return move.accept(new Visitor<>() {
			@Override
			public List<Integer> visit(SingleMove move) {
				return List.of(move.destination);
			}

			@Override
			public List<Integer> visit(DoubleMove move) {
				return List.of(move.destination1, move.destination2);
			}
		});
	}

	//  evaluates heuristics and weighting
	private float evaluateReward(VirtualState state) {
		float penalty = 0;
		float finalReward = 0;
		Board.GameState boardState=state.gameState;

		ImmutableSet<Piece> winner = boardState.getWinner();
		if (winner.contains(Piece.MrX.MRX)) {
			return 100000.0f; //mrX Won
		}
		if (!winner.isEmpty()) {
			return -100000.0f;
		}

		List<Piece> detectivePieces = boardState.getPlayers().stream().filter(p -> p.isDetective()).toList();
		int mrXLocation = state.getPieceLocation(Piece.MrX.MRX);

		float detectiveDistance = distanceToDetective(boardState.getSetup(),boardState, detectivePieces, mrXLocation);

		int escapeRoutes = boardState.getSetup().graph.adjacentNodes(mrXLocation).size();

//     if on a hub like bus or underground that allows more movement
		boolean isAtHub = false;

		for (int neighbor : boardState.getSetup().graph.adjacentNodes(mrXLocation)) {
			ImmutableSet<ScotlandYard.Transport> transports = boardState.getSetup().graph.edgeValueOrDefault(mrXLocation, neighbor, ImmutableSet.of());

			if (transports.contains(ScotlandYard.Transport.UNDERGROUND) || transports.contains(ScotlandYard.Transport.BUS)) {
				isAtHub = true;
				break;
			}
		}
		// ticket weighting
		int secrets = boardState.getPlayerTickets(Piece.MrX.MRX).map(t->t.getCount(ScotlandYard.Ticket.SECRET)).orElse(0);
		int doubles = boardState.getPlayerTickets(Piece.MrX.MRX).map(t->t.getCount(ScotlandYard.Ticket.DOUBLE)).orElse(0);

		int taxi = boardState.getPlayerTickets(Piece.MrX.MRX).map(t->t.getCount(ScotlandYard.Ticket.TAXI)).orElse(0);
		int bus = boardState.getPlayerTickets(Piece.MrX.MRX).map(t->t.getCount(ScotlandYard.Ticket.BUS)).orElse(0);
		int underground = boardState.getPlayerTickets(Piece.MrX.MRX).map(t->t.getCount(ScotlandYard.Ticket.UNDERGROUND)).orElse(0);

//     prevent near misses with detectives
		if (detectiveDistance <= 1) {
			penalty += 100000.0f; //immediate capture
			finalReward = (detectiveDistance * 10000.0f) + (escapeRoutes * 4000.0f) + (doubles * 100.0f) - penalty;

		} else if (detectiveDistance <= 2) {
			penalty += 7000.0f; //high risk of being cut off
			finalReward = (detectiveDistance * 10000.0f) + (escapeRoutes * 4000.0f) + (doubles * 100.0f) - penalty;

		}
		else {
			finalReward = (detectiveDistance * 1000.0f) + (escapeRoutes * 400.0f) + (doubles * 1500.0f) - penalty;
		}

		if (isAtHub) {
			finalReward += 1500.0f; //reward staying near hubs
		}

		finalReward += (secrets * 200.0f) + (underground * 100.0f) + (bus * 50.0f) + (taxi * 10);

		return finalReward;
	}

	//  gets the tickets for a specific piece
	private ImmutableMap<ScotlandYard.Ticket, Integer> getTicketMap(Board board, Piece piece) {
		Board.TicketBoard ticketBoard = board.getPlayerTickets(piece).orElseThrow();

		Map<ScotlandYard.Ticket, Integer> map = new HashMap<>();
		for (ScotlandYard.Ticket t : ScotlandYard.Ticket.values()) {
			map.put(t, ticketBoard.getCount(t));
		}
		return ImmutableMap.copyOf(map);
	}

	private Board.GameState reconstructState(Board board, int mrXLocation) {
		//reconstruct MrX Player
		Player mrX = new Player(
				Piece.MrX.MRX,
				getTicketMap(board, Piece.MrX.MRX), // Use the helper here
				mrXLocation
		);

		//reconstruct Detectives
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

		//build using factory
		return new MyGameStateFactory().build(
				board.getSetup(),
				mrX,
				ImmutableList.copyOf(detectives)
		);
	}

	@Nonnull @Override public Move pickMove (
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		ImmutableList<Move> moves = board.getAvailableMoves().asList();
		Board.GameState state = reconstructState(board, moves.get(0).source());

		//timer to make move but 2 seconds before it ends to allow time
		long deadline = System.currentTimeMillis() + timeoutPair.right().toMillis(timeoutPair.left()) - 2000;
		Move bestMoveOverall = moves.get(0);

		// does each depth step by step to allow time deadlines to interrupt it and play the current best move from the depth its gotten to so far
		try {
			for (int d = 1; d <= 5; d++) {
				float maxScore = -Float.MAX_VALUE;
				Move bestAtDepth = null;
				for (Move move : moves) {
					if (System.currentTimeMillis() > deadline) throw new RuntimeException();
					float score = alphaBeta(new VirtualState(state.advance(move)), d, -Float.MAX_VALUE, Float.MAX_VALUE, false, d, deadline);
					if (score > maxScore) {
						maxScore = score;
						bestAtDepth = move;
						bestMoveOverall = bestAtDepth;
					}
				}
			}
		} catch (RuntimeException e) {}

		return bestMoveOverall;
	}
}