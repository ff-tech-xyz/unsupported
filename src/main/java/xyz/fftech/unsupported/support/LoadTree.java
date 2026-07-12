package xyz.fftech.unsupported.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        List<Node> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator.comparingInt(Node::depth).reversed());
        for (Node node : ordered) if (node.parent != null) node.parent.load += node.load;
        List<Node> failed = new ArrayList<>();
        for (Node node : nodes) if (node.parent != null && !node.edgePasses()) collect(node, failed);
        return failed.stream().distinct().toList();
    }

    private static void collect(Node root, List<Node> failed) {
        failed.add(root);
        for (Node child : root.children) collect(child, failed);
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

        private int depth() { return parent == null ? 0 : 1 + parent.depth(); }
        public String id() { return id; }
        public double load() { return load; }
    }
}
