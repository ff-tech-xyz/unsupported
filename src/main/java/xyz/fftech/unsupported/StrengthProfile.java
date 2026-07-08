package xyz.fftech.unsupported;

public final class StrengthProfile {
    public double m;
    public double C;
    public double S;
    public double T;
    public int maxCantilever;
    public int maxArchSpan;

    public double massKg() {
        return m;
    }

    public double carryKg() {
        return C;
    }

    public double shearKg() {
        return S;
    }

    public double tensionKg() {
        return T;
    }
}
