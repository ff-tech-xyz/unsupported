package xyz.fftech.unsupported.support;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class LoadTree {
    private LoadTree() {}

    public static SupportEdgeType firstAvailable(boolean down, boolean arch, boolean side, boolean hang) {
        if (down) return SupportEdgeType.DOWN;
        if (arch) return SupportEdgeType.ARCH;
        if (side) return SupportEdgeType.SIDE;
        if (hang) return SupportEdgeType.HANG;
        return SupportEdgeType.NONE;
    }

    public static List<Node> evaluate(List<Node> nodes) {
        for (Node node : nodes) node.load = node.mass;
        accumulateLeavesFirst(nodes);
        List<Node> failed = new ArrayList<>();
        Set<Node> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Node node : nodes) if (node.parent != null && !node.edgePasses()) collect(node, failed, seen);
        return List.copyOf(failed);
    }

    private static void accumulateLeavesFirst(List<Node> nodes) {
        Set<Node> included = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        included.addAll(nodes);
        IdentityHashMap<Node, Integer> remainingChildren = new IdentityHashMap<>();
        ArrayDeque<Node> leaves = new ArrayDeque<>();
        for (Node node : nodes) {
            int count = 0;
            for (Node child : node.children) if (included.contains(child)) count++;
            remainingChildren.put(node, count);
            if (count == 0) leaves.addLast(node);
        }
        int processed = 0;
        while (!leaves.isEmpty()) {
            Node node = leaves.removeFirst();
            processed++;
            if (node.parent != null) {
                node.parent.load += node.load;
                if (included.contains(node.parent)) {
                    int left = remainingChildren.merge(node.parent, -1, Integer::sum);
                    if (left == 0) leaves.addLast(node.parent);
                }
            }
        }
        if (processed != nodes.size()) throw new IllegalArgumentException("load tree contains a parent cycle");
    }

    private static void collect(Node root, List<Node> failed, Set<Node> seen) {
        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            Node node = stack.removeLast();
            if (!seen.add(node)) continue;
            failed.add(node);
            for (Node child : node.children) stack.addLast(child);
        }
    }

    public static final class Node {
        private final String id;
        private final double mass;
        private final double carry;
        private final double shear;
        private final double tension;
        private final List<Node> children = new ArrayList<>();
        private Node parent;
        private SupportEdgeType edge = SupportEdgeType.NONE;
        private int momentArm;
        private double load;

        public Node(String id, double mass, double carry, double shear, double tension) {
            this.id = id; this.mass = mass; this.carry = carry; this.shear = shear; this.tension = tension;
        }

        public void attach(Node parent, SupportEdgeType edge, int momentArm) {
            this.parent = parent; this.edge = edge; this.momentArm = Math.max(1, momentArm); parent.children.add(this);
        }

        private boolean edgePasses() {
            return switch (edge) {
                case DOWN, ARCH -> load <= parent.carry;
                case SIDE -> load * momentArm <= parent.shear;
                case HANG -> load <= parent.tension;
                case NONE -> false;
            };
        }

        public String id() { return id; }
        public double load() { return load; }
    }
}
