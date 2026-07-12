package xyz.fftech.unsupported.support;

import java.util.List;

public final class SupportLogicTest {
    public static void main(String[] args) {
        assertClose(25.0, StateMassModifiers.apply(100.0, "snow", 2, false));
        assertClose(200.0, StateMassModifiers.apply(100.0, "slab_double", 1, false));
        assertClose(400.0, StateMassModifiers.apply(100.0, "candle", 4, false));
        assertClose(1_100.0, StateMassModifiers.apply(100.0, "plain", 1, true));

        LoadTree.Node root = new LoadTree.Node("root", 10, 50, 50, 50);
        LoadTree.Node middle = new LoadTree.Node("middle", 40, 100, 100, 100);
        LoadTree.Node leaf = new LoadTree.Node("leaf", 20, 100, 100, 100);
        middle.attach(root, SupportEdgeType.DOWN, 1);
        leaf.attach(middle, SupportEdgeType.DOWN, 1);
        List<LoadTree.Node> failed = LoadTree.evaluate(List.of(root, middle, leaf));
        assertClose(70, root.load());
        assertClose(60, middle.load());
        assertEquals(List.of("middle", "leaf"), failed.stream().map(LoadTree.Node::id).toList());
        assertEquals(SupportEdgeType.DOWN, LoadTree.firstAvailable(true, true, true, true));
        assertEquals(SupportEdgeType.ARCH, LoadTree.firstAvailable(false, true, true, true));
        assertEquals(SupportEdgeType.SIDE, LoadTree.firstAvailable(false, false, true, true));
        assertEquals(SupportEdgeType.HANG, LoadTree.firstAvailable(false, false, false, true));
        benchmarkBoundedLoadTree();
        System.out.println("SupportLogicTest: 4 passed");
    }

    private static void benchmarkBoundedLoadTree() {
        java.util.ArrayList<LoadTree.Node> nodes = new java.util.ArrayList<>();
        for (int i = 0; i < 256; i++) {
            LoadTree.Node node = new LoadTree.Node(Integer.toString(i), 1, 1_000_000, 1_000_000, 1_000_000);
            if (!nodes.isEmpty()) node.attach(nodes.get(i - 1), SupportEdgeType.DOWN, 1);
            nodes.add(node);
        }
        for (int i = 0; i < 100; i++) LoadTree.evaluate(nodes);
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) LoadTree.evaluate(nodes);
        double micros = (System.nanoTime() - start) / 10_000.0 / 1_000.0;
        System.out.printf("LoadTree benchmark: 256 nodes average %.3f us%n", micros);
        if (micros >= 1_000.0) throw new AssertionError("256-node load tree exceeded 1 ms budget: " + micros + " us");
    }

    private static void assertClose(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.0001) throw new AssertionError(expected + " != " + actual);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
}