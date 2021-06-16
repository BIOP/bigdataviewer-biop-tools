package ch.epfl.biop.sourceandconverter.exporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CZTRange {

    final List<Integer> rangeC;
    final List<Integer> rangeZ;
    final List<Integer> rangeT;

    public CZTRange(List<Integer> rangeC,
                    List<Integer> rangeZ,
                    List<Integer> rangeT) {
        this.rangeC = Collections.unmodifiableList(rangeC);
        this.rangeZ = Collections.unmodifiableList(rangeZ);
        this.rangeT = Collections.unmodifiableList(rangeT);
    }

    public int[] getCZTDimensions() {
        return new int[]{rangeC.size(), rangeZ.size(), rangeT.size()};
    }

    public List<Integer> getRangeC() {
        return rangeC;
    }

    public List<Integer> getRangeZ() {
        return rangeZ;
    }

    public List<Integer> getRangeT() {
        return rangeT;
    }

    public long getTotalPlanes() {
        return rangeC.size()*rangeZ.size()*rangeT.size();
    }

    public static class Builder {
        private String expressionRangeC = "";
        private String expressionRangeZ = "";
        private String expressionRangeT = "";

        public Builder setC(String exp) {
            expressionRangeC = exp;
            return this;
        }

        public Builder setZ(String exp) {
            expressionRangeZ = exp;
            return this;
        }

        public Builder setT(String exp) {
            expressionRangeT = exp;
            return this;
        }

        public CZTRange get(int nC, int nZ, int nT) throws Exception {
            List<Integer> rangeC = new IntRangeParser(expressionRangeC).get(nC);
            List<Integer> rangeZ = new IntRangeParser(expressionRangeZ).get(nZ);
            List<Integer> rangeT = new IntRangeParser(expressionRangeT).get(nT);

            return new CZTRange(rangeC, rangeZ, rangeT);
        }
    }
}
