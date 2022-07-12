package screenpac.controllers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Random;

import screenpac.model.Node;

import screenpac.extract.Constants;
import screenpac.features.NodeScore;
import screenpac.features.Utilities;
import screenpac.model.GameStateInterface;
import screenpac.model.GhostState;
import screenpac.model.MazeInterface;

public class IA2122PL1G4 implements AgentInterface, Constants {

	/*
	 * Random utilizado para adicionar um pouco de aleatoriadade no algoritmo,
	 * principalmente no MCTS
	 */
	Random rand;
	/*
	 * Pill ou Power mais proximo
	 */
	PillMaisProxima pillEater;
	/*
	 * Fantasma mais perto
	 */
	GhostState nearest = null;
	/*
	 * Distancia a qual o pacman tenta comer/fugir dos fantasmas
	 */
	private final static int eatingDistance = 50;
	/*
	 * Valor de C para utilizar no calculo do melhor filho (teoricamente igual a
	 * sqrt(2) )
	 */
	private static final double MCTS_c = 1.41421356237;
	/*
	 * Repeticoes que o ciclo do MCTS vai ver, teoricamente vai demorar no maximo
	 * 40ms
	 */
	private static int MCTS_repeats = 120;
	/*
	 * Numero de passo que a simulacao vai ver, como os passos vao alternando entre
	 * pacman vs fantasma na realidade sao so metade
	 */
	private static int Simulations_repeats = 120;

	/**
	 * Construtor do Algoritmo, inicializa o random e a classe para calcular a pill
	 * ou power mais proximo
	 */
	public IA2122PL1G4() {
		pillEater = new PillMaisProxima();
		rand = new Random(System.currentTimeMillis());

	}

	/**
	 * Classe semelhante ao "NearestPillDistance", calcula a pill mais proxima
	 * exatamente igual mas adiciona também o calculo para os powers, este e
	 * necessario pois se o pacman deixar apenas um power ele fica sem saber o que
	 * fazer
	 * 
	 *
	 */
	public class PillMaisProxima implements NodeScore {
		int max = Integer.MAX_VALUE;
		public Node closest = null;

		/**
		 * <pre>
		 * O estado GS tem pelo menos uma pill ou power existente no maze e o node pertence ao maze
		 * </pre>
		 * 
		 * @param gs   Estado da board
		 * @param node Posicao do pacman
		 * 
		 * 
		 * 
		 * @return Valor da distancia do node ate a pill ou power mais proxima e a
		 *         variavel "closest" guarda a sua posicao
		 */
		public double score(GameStateInterface gs, Node node) {

			int minDist = max;
			ArrayList<Node> pills = gs.getMaze().getPills();
			ArrayList<Node> powers = gs.getMaze().getPowers();

			for (Node n : pills) {
				if (gs.getPills().get(n.pillIndex)) {
					if (gs.getMaze().dist(node, n) < minDist) {
						minDist = gs.getMaze().dist(node, n);
						closest = n;
					}
				}
			}

			for (Node power : powers) {
				if (gs.getPowers().get(power.powerIndex)) {
					if (gs.getMaze().dist(node, power) < minDist) {
						minDist = gs.getMaze().dist(node, power);
						closest = power;
					}
				}
			}

			if (closest != null) {
				closest.col = Color.green;
			}
			return minDist;
		}
	}

	/**
	 * <pre>
	 * O estado gs nao e um estado final do jogo
	 * </pre>
	 * 
	 * 3 casos, 1 - Comer fantasmas (apenas vai ter com o fantasma) 2 - Fugir dos
	 * fantasmas implementado com MCTS 3 - comer a pill ou power mais proximo
	 * (apenas vai ter com a pill ou power mais proxima)
	 * 
	 * @param gs um estado do jogo
	 * 
	 * @return O melhor movimento de acordo com o algoritmo implementado
	 */
	@Override
	public int action(GameStateInterface gs) {

		gs = gs.copy();
		int minGhost = NearestGhostDistance(gs.getGhosts(), gs.getPacman().current, gs.getMaze());
		int dir;
		Node next;
		Node current = gs.getPacman().current;

		if (nearest.edible() && minGhost < eatingDistance) {
			curr = null;
			next = Utilities.getClosest(current.adj, nearest.current, gs.getMaze());
			dir = Utilities.getWrappedDirection(gs.getPacman().current, next, gs.getMaze());
			return dir;
		} else if (!nearest.edible() && minGhost < eatingDistance) {
			return MCTS(gs);
		}
		curr = null;
		pillEater.score(gs, current);
		next = Utilities.getClosest(current.adj, pillEater.closest, gs.getMaze());
		dir = Utilities.getWrappedDirection(current, next, gs.getMaze());
		return dir;
	}

	/**
	 * 
	 * @param ghostsPosition Posicoes dos fantasmas
	 * @param pacmanPosition Posicao do pacman
	 * @param maze           Mazem em que se encontram
	 * @return O valor da distncia minima a um fantasma, tambem da set da variavel
	 *         "nearest" para guardar o fantasma que se encontra mais perto
	 */
	public int NearestGhostDistance(GhostState[] ghostsPosition, Node pacmanPosition, MazeInterface maze) {
		int min = Integer.MAX_VALUE;
		int dist;
		for (GhostState ghost : ghostsPosition) {
			dist = maze.dist(ghost.current, pacmanPosition);
			if (dist < min && !ghost.returning()) {
				min = dist;
				nearest = ghost;
			}
		}

		return min;
	}

	/**
	 * Classe utilizada apenas para o algoritmo MCTS
	 *
	 */
	private class Tree_Node {
		Tree_Node pai;
		ArrayList<Tree_Node> filhos;
		int visitados;
		double wins;
		Node pacman;
		Node[] ghosts;

		/**
		 * Consutor de quando o node e uma raiz
		 * 
		 * @param gs_
		 */
		public Tree_Node(GameStateInterface gs_) {
			this.pai = null;
			this.visitados = 0;
			this.wins = 0;
			this.filhos = null;
			this.pacman = gs_.getPacman().current;
			this.ghosts = new Node[4];
			for (int i = 0; i < 4; i++) {
				ghosts[i] = gs_.getGhosts()[i].current;
			}
		}

		/**
		 * Construtor para quando o no e um filho
		 * 
		 * @param pai         No pai desse estado
		 * @param pacman_move Estado do pacman
		 * @param ghosts2     Estado dos fantasmas
		 */
		public Tree_Node(Tree_Node pai, Node pacman_move, Node[] ghosts2) {
			this.pai = pai;
			this.visitados = 0;
			this.wins = 0;
			this.filhos = null;
			this.pacman = pacman_move;
			this.ghosts = ghosts2;
		}

	}

	/**
	 * Seleciona um no folha de acordo com a raiz
	 * 
	 * Caso seja movimento do pacman este escolhe vai sempre escolher um que nao
	 * tenha sido verificado, se todos forem verificamos escolhe aleatoriamente Se
	 * for o movimento dos fantasmas simplesmente escolhe um aleatoriamente
	 * 
	 * @param hihi Node que e uma raiz
	 * @return Node que se encontra como folha de acordo com a politica
	 */
	public Tree_Node select(Tree_Node hihi) {
		Tree_Node temp;
		int i = -1;
		while (true) {
			i++;
			temp = null;
			if (hihi.filhos == null) {
				return hihi;
			}
			if (i % 2 == 0) {

				for (Tree_Node filhos : hihi.filhos)
					if (filhos.filhos == null) {
						temp = filhos;
						break;
					}
				if (temp != null) {
					hihi = temp;
					continue;
				}
			}
			hihi = hihi.filhos.get(rand.nextInt(hihi.filhos.size()));

		}

	}

	/**
	 * Expandir o no que queremos mas apenas para o movimento do pacman
	 * 
	 * @param folha no para expandir
	 */
	public void expansion_pacman(Tree_Node folha) {
		ArrayList<Node> pacman_moves = folha.pacman.adj;
		ArrayList<Tree_Node> calcFilhos = new ArrayList<Tree_Node>(pacman_moves.size());
		for (Node pacman_move : pacman_moves) {
			if ((folha.pai == null || folha.pai.pacman != pacman_move) // evita repetir o mesmo movimento durante 2
																		// vezes seguidas
					&& (folha.pai == null || folha.pai.pai == null || folha.pai.pai.pacman != pacman_move)) {
				Tree_Node filho = new Tree_Node(folha, pacman_move, folha.ghosts);
				calcFilhos.add(filho);
			}

		}
		folha.filhos = calcFilhos;
	}

	/**
	 * Expandir o no que queremos mas apenas para o movimento dos fantasmas
	 * 
	 * @param folha no para expandir
	 */
	public void expansion_ghosts(Tree_Node folha) {

		ArrayList<Node> ghost0_ = folha.ghosts[0].adj;
		ArrayList<Node> ghost1_ = folha.ghosts[1].adj;
		ArrayList<Node> ghost2_ = folha.ghosts[2].adj;
		ArrayList<Node> ghost3_ = folha.ghosts[3].adj;

		ArrayList<Tree_Node> fds = new ArrayList<Tree_Node>(
				ghost0_.size() * ghost1_.size() * ghost2_.size() * ghost3_.size());
		Node[] ya;
		for (Node ghost0 : ghost0_) {
			for (Node ghost1 : ghost1_) {
				for (Node ghost2 : ghost2_) {
					for (Node ghost3 : ghost3_) {
						ya = new Node[4];

						ya[0] = ghost0;
						ya[1] = ghost1;
						ya[2] = ghost2;
						ya[3] = ghost3;

						Tree_Node filho = new Tree_Node(folha, folha.pacman, ya);
						fds.add(filho);
					}
				}
			}
		}

		folha.filhos = fds;
	}

	/**
	 * Expandir o no folha que selecionamos
	 * 
	 * @param folha No que queremos expandir
	 * @param i     Ver se e o turno do pacman ou dos fantasmas
	 */
	public void expansion(Tree_Node folha, int i) {
		if (folha.filhos != null)
			return;

		if (i % 2 == 0) {
			expansion_pacman(folha);
		} else {
			expansion_ghosts(folha);
		}

	}

	/**
	 * Verifica se o pacman ainda se encontra vivo
	 * 
	 * @param folha no em que queremos verificar se o pacman esta vivo ou nao
	 * @return -1 caso o pacman morra, 1 se o pacman nao morrer
	 */
	public double still_alive(Tree_Node folha) {

		Node pacman = folha.pacman;
		Node[] ghosts = folha.ghosts;

		for (Node ghost : ghosts) {
			if (currMaze.dist(pacman, ghost) < 10) {
				return -1;
			}
		}

		return 1;
	}

	/**
	 * Atribui um valor ao estado final de acordo de como ele comecou Este valor e
	 * atribuido de acordo com a media das distancias aos fantasmas, caso este se
	 * tenha afastado mais ganha uma maior quantidade de pontos, caso este nao se
	 * tenha afastado ganha 0.1 pontos
	 * 
	 * @param Starting Estado inicial da simulacao
	 * @param Final    Estado final da simulacao
	 * @return Os valores que atribui ao estado final de acordo com o estado inicial
	 */
	public double give_points(Tree_Node Starting, Tree_Node Final) {
		int count = 0;
		double dist, dist2;
		dist = 0;
		for (Node ghost : Starting.ghosts) {
			if (currMaze.dist(ghost, Starting.pacman) < 120) {
				dist += currMaze.dist(ghost, Starting.pacman);
				count++;
			}
		}
		dist /= count;

		count = 0;
		dist2 = 0;
		for (Node ghost : Final.ghosts) {
			if (currMaze.dist(ghost, Final.pacman) < 120) {
				dist2 += currMaze.dist(ghost, Final.pacman);
				count++;
			}
		}
		dist2 /= count;

		if (dist + 20 < dist2) {
			return 1;
		} else if (dist < dist2) {
			return 0.6;
		} else {
			return 0.1;
		}

	}

	/**
	 * Faz uma simulacao de uma quantidade fixa de passos e atribui uma pontuacao
	 * caso este morra / aguente vivo
	 * 
	 * @param folha no que queremos simular
	 * @param i     Em que passo estamos? (pacman vs ghosts)
	 * @return O mesmo no que simulamos (poderia retornar void)
	 */
	public Tree_Node simulate(Tree_Node folha, int i) {
//		System.out.println(folha.wins + " " + folha.visitados);
		ArrayList<Node> pacman_history = new ArrayList<>();
		ArrayList<Node[]> ghosts_history = new ArrayList<>();
		Tree_Node temp = folha;
		Tree_Node test = null;
		int j = 0;
		while (j < Simulations_repeats) {

			expansion(folha, i);

			if (i % 2 != 0) {
				int h = 0;
				test = folha.filhos.get(rand.nextInt(folha.filhos.size()));
				while (h != 8 && pacman_history.contains(test.pacman)) {
					h++;
					test = folha.filhos.get(rand.nextInt(folha.filhos.size()));
				}

				folha = test;
				pacman_history.add(folha.pacman);
			}

			else {
				int h = 0;
				test = folha.filhos.get(rand.nextInt(folha.filhos.size()));
				while (h != 30 && ghosts_history.contains(test.ghosts)) {
					h++;
					test = folha.filhos.get(rand.nextInt(folha.filhos.size()));
				}

				folha = test;
				ghosts_history.add(folha.ghosts);
			}

			if (still_alive(folha) == -1) {
				temp.filhos = null; // Nao guardamos o que expandimos da simulacao
				temp.wins = 0 - ((double) (Simulations_repeats - j) / Simulations_repeats); // atribui um valor entre -1
																							// e 0 dependendo de quanto
																							// tempo aguentou vivo
				temp.visitados++;
				return temp;
			}
			i++;
			j++;
		}

		temp.wins = give_points(temp, folha);
		temp.filhos = null; // Nao guardamos o que expandimos da simulacao
		temp.visitados++;

		return temp;
	}

	/**
	 * Propagacao dos valores encontrados durante a simualcao ate a raiz da arvore
	 * 
	 * @param folha No que foi realizado a simulacao
	 */
	public void BackProg(Tree_Node folha) {
		double wins = folha.wins;
		while (folha.pai != null) {
			folha = folha.pai;
			folha.wins += wins;
			folha.visitados++;
		}
	}

	/**
	 * De acordo com a formula de UCT utilizando uma aproximacao para sqrt(2)
	 * calcula o filho que maximiza o valor dessa formula
	 * 
	 * @param folha node que queremos encontrar o melhor filho
	 * @return o melhor filho, ou caso de nenhum satisfazer a formula um aleatorio
	 */
	public Tree_Node MelhorFilho(Tree_Node folha) {

		double melhor = Double.NEGATIVE_INFINITY;
		double atual = 0;
		Tree_Node melhorFilho = folha.filhos.get(rand.nextInt(folha.filhos.size()));
		for (Tree_Node filho : folha.filhos) {
			if (filho.visitados != 0) {
				atual = (filho.wins / filho.visitados)
						+ MCTS_c * Math.sqrt(Math.log(folha.visitados) / filho.visitados);
				if (atual > melhor) {
					melhor = atual;
					melhorFilho = filho;
				}
			}
		}

		return melhorFilho;
	}

	MazeInterface currMaze;
	Tree_Node curr;

	/**
	 * Caso uma tree ja exista, este tenta procurar no passo seguinte se foi um dos
	 * que explorou para a reutilizar quando se reutiliza a tree ajuda o algoritmo
	 * porque ja tem nodes explorados entao escolhemos um filho mais adequado
	 * 
	 * @param gs estado que estamos a procurar na arvore
	 * @return o no da arvore que representa o estado, ou caso contrario um no com o
	 *         estado gs
	 */
	public Tree_Node ReutilizarTree(GameStateInterface gs) {
		if (curr == null) {
			return new Tree_Node(gs);
		}

		boolean found = false;
		ArrayList<Tree_Node> filhos = curr.filhos;
		for (Tree_Node pacman_move : filhos) {
			if (pacman_move.pacman.x == gs.getPacman().current.x && pacman_move.pacman.y == gs.getPacman().current.y) {
				curr = pacman_move;
				found = true;
				break;
			}
		}

		if (found && curr.filhos != null) {
			filhos = curr.filhos;
			for (Tree_Node ghost_move : filhos) {
				found = true;
				for (int i = 0; i < 4; i++) {
					if (!(ghost_move.ghosts[i].x == gs.getGhosts()[i].current.x
							&& ghost_move.ghosts[i].y == gs.getGhosts()[i].current.y)) {
						found = false;
						break;
					}
				}
				if (found) {
					curr = ghost_move;
					curr.pai = null; // faz algo? limpa espaco?
					break;
				}
			}

		}

		if (!found) {
			curr = new Tree_Node(gs);
		}
		return curr;
	}

	/**
	 * Metodo responsavel por explorar o estado inicial com o algoritmo de Monte
	 * Carlo Tree Search
	 * 
	 * @param gs estado inicial
	 * @return Melhor movimento para fugir dos fantasmas
	 */
	public int MCTS(GameStateInterface gs) {
		currMaze = gs.getMaze();
		curr = ReutilizarTree(gs);
		Tree_Node MCTS_Found;
		for (int i = 0; i < MCTS_repeats; i++) { // Se trocarmos este ciclo para demorar uma quantidade fixa de tempo
													// (40ms) o jogo parece que fica mais lento entao escolhi um numero
													// alto o suficiente que parece nao atrasar o jogo
			MCTS_Found = select(curr);
			expansion(MCTS_Found, i);
			MCTS_Found = simulate(MCTS_Found.filhos.get(rand.nextInt(MCTS_Found.filhos.size())), i);
			BackProg(MCTS_Found);
		}
		Tree_Node melhorFilho = MelhorFilho(curr);
		return Utilities.getWrappedDirection(curr.pacman, melhorFilho.pacman, currMaze);
	}

}