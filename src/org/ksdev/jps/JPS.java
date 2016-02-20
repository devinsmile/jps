package org.ksdev.jps;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * @author Kevin
 */
public abstract class JPS<T extends Node> {
    protected final Graph<T> graph;

    public JPS(Graph<T> graph) {
        this.graph = graph;
    }

    public Future<Queue<T>> findPath(T start, T goal) {
        FutureTask<Queue<T>> future = new FutureTask<>(() -> findPathSync(start, goal));
        future.run();
        return future;
    }

    public Queue<T> findPathSync(T start, T goal) {
        Queue<T> open = new PriorityQueue<>((a, b) -> {
            // we want the nodes with the lowest projected F value to be checked first
            return Double.compare(a.f, b.f);
        });
        Set<T> closed = new HashSet<>();
        Map<T, T> parentMap = new HashMap<>();

        if (!goal.isWalkable()) {
            return null;
        }

        System.out.println("Start: " + start.x + "," + start.y);
        // push the start node into the open list
        open.add(start);

        // while the open list is not empty
        while (!open.isEmpty()) {
            //System.out.println(open.size());
            // pop the position of node which has the minimum `f` value.
            T node = open.poll();
            // mark the current node as checked
            closed.add(node);

            if (node.equals(goal)) {
                return backtrace(goal, parentMap);
            }
            // add all possible next steps from the current node
            identifySuccessors(node, goal, open, closed, parentMap);
        }

        // failed to find a path
        return null;
    }

    /**
     * Identify successors for the given node. Runs a JPS in direction of each available neighbor, adding any open
     * nodes found to the open list.
     * @return All the nodes we have found jumpable from the current node
     */
    private void identifySuccessors(T node, T goal, Queue<T> open, Set<T> closed, Map<T, T> parentMap) {
        // get all neighbors to the current node
        Collection<T> neighbors = findNeighbors(node, parentMap);

        double d;
        double ng;
        for (T neighbor : neighbors) {
            // jump in the direction of our neighbor
            T jumpNode = jump(neighbor, node, goal);

            // don't add a node we have already gotten to quicker
            if (jumpNode == null || closed.contains(jumpNode)) continue;

            // determine the jumpNode's distance from the start along the current path
            d = graph.getDistance(jumpNode, node);
            ng = node.g + d;

            // if the node has already been opened and this is a shorter path, update it
            // if it hasn't been opened, mark as open and update it
            if (!open.contains(jumpNode) || ng < jumpNode.g) {
                jumpNode.g = ng;
                jumpNode.h = graph.getHeuristicDistance(jumpNode, goal);
                jumpNode.f = jumpNode.g + jumpNode.h;
                parentMap.put(jumpNode, node);

                if (!open.contains(jumpNode)) {
                    open.offer(jumpNode);
                }
            }
        }
    }

    /**
     * Find all neighbors for a given node. If node has a parent then prune neighbors based on JPS algorithm,
     * otherwise return all neighbors.
     */
    protected abstract Collection<T> findNeighbors(T node, Map<T, T> parentMap);

    /**
     * Search towards the child from the parent, returning when a jump point is found.
     */
    protected abstract T jump(T neighbor, T current, T goal);

    /**
     * Returns a path of the parent nodes from a given node.
     */
    private Queue<T> backtrace(T node, Map<T, T> parentMap) {
        LinkedList<T> path = new LinkedList<>();
        path.add(node);

        int previousX, previousY, currentX, currentY;
        int dx, dy;
        int steps;
        T temp;
        while (parentMap.containsKey(node)) {
            previousX = parentMap.get(node).x;
            previousY = parentMap.get(node).y;
            currentX = node.x;
            currentY = node.y;
            steps = Integer.max(Math.abs(previousX - currentX), Math.abs(previousY - currentY));
            dx = Integer.compare(previousX, currentX);
            dy = Integer.compare(previousY, currentY);

            temp = node;
            for (int i = 0; i < steps; i++) {
                temp = graph.getNode(temp.x + dx, temp.y + dy);
                path.addFirst(temp);
            }

            node = parentMap.get(node);
        }
        return path;
    }

    public static class JPSFactory {
        public static <T extends Node> JPS<T> getJPS(Graph<T> graph, Graph.Diagonal diagonal) {
            switch (diagonal) {
                case ALWAYS:
                    return new JPSDiagAlways<>(graph);
                case ONE_OBSTACLE:
                    return new JPSDiagOneObstacle<>(graph);
                case NO_OBSTACLES:
                    return new JPSDiagNoObstacles<>(graph);
                case NEVER:
                    return new JPSDiagNever<>(graph);
            }
            return null;
        }
    }
}
