package ch.epfl.biop.sourceandconverter.exporter;

import java.util.Collections;
import java.util.List;

/**
 * Defining a CZT Range with a class
 */
public class CZTRange {

    final List<Integer> rangeC;
    final List<Integer> rangeZ;
    final List<Integer> rangeT;

    /**
     * Javadoc needed
     * @param rangeC just read the name
     * @param rangeZ just read the name
     * @param rangeT just read the name
     */
    public CZTRange(List<Integer> rangeC,
                    List<Integer> rangeZ,
                    List<Integer> rangeT) {
        this.rangeC = Collections.unmodifiableList(rangeC);
        this.rangeZ = Collections.unmodifiableList(rangeZ);
        this.rangeT = Collections.unmodifiableList(rangeT);
    }

    /**
     *
     * @return an array containing the number of channels, slices, and timepoints
     */
    public int[] getCZTDimensions() {
        return new int[]{rangeC.size(), rangeZ.size(), rangeT.size()};
    }

    /**
     *
     * @return the range in C as a List
     */
    public List<Integer> getRangeC() {
        return rangeC;
    }

    /**
     *
     * @return the range in Z as a List
     */
    public List<Integer> getRangeZ() {
        return rangeZ;
    }

    /**
     *
     * @return the range in T as a List
     */
    public List<Integer> getRangeT() {
        return rangeT;
    }

    /**
     *
     * @return a String representation of this CZT Range
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("C:");
        rangeC.forEach(c -> builder.append(c+","));
        builder.append(" Z:");
        rangeZ.forEach(z -> builder.append(z+","));
        builder.append(" T:");
        rangeT.forEach(t -> builder.append(t+","));
        return builder.toString();
    }

    /**
     *
     * @return the total number of planes to expect from this range
     */
    public long getTotalPlanes() {
        return (long) rangeC.size() *rangeZ.size()*rangeT.size();
    }

    /**
     * Builder to build a CZTRange from a String
     */
    public static class Builder {
        private String expressionRangeC = "";
        private String expressionRangeZ = "";
        private String expressionRangeT = "";

        /**
         *
         * @param exp expression
         * @return builder
         */
        public Builder setC(String exp) {
            expressionRangeC = exp;
            return this;
        }

        /**
         *
         * @param exp expression
         * @return builder
         */
        public Builder setZ(String exp) {
            expressionRangeZ = exp;
            return this;
        }

        /**
         *
         * @param exp expression
         * @return builder
         */
        public Builder setT(String exp) {
            expressionRangeT = exp;
            return this;
        }

        /**
         * Construct the CZT Range
         * @param nC maximal number of channels
         * @param nZ maximal number of Slices
         * @param nT maximal number of timepoints
         * @return CZT range
         * @throws Exception if the String parser is bad
         */
        public CZTRange get(int nC, int nZ, int nT) throws Exception {
            List<Integer> rangeC = new IntRangeParser(expressionRangeC).get(nC);
            List<Integer> rangeZ = new IntRangeParser(expressionRangeZ).get(nZ);
            List<Integer> rangeT = new IntRangeParser(expressionRangeT).get(nT);

            return new CZTRange(rangeC, rangeZ, rangeT);
        }

        /**
         * Construct the CZT Range
         * @param nC maximal number of channels
         * @param nZ maximal number of Slices
         * @param nT maximal number of timepoints
         * @return CZT range
         * @throws Exception if the String parser is bad
         */
        public CZTRange get(int c0, int nC, int z0, int nZ, int t0, int nT) throws Exception {
            List<Integer> rangeC = new IntRangeParser(expressionRangeC).get(c0, nC);
            List<Integer> rangeZ = new IntRangeParser(expressionRangeZ).get(z0, nZ);
            List<Integer> rangeT = new IntRangeParser(expressionRangeT).get(t0, nT);

            return new CZTRange(rangeC, rangeZ, rangeT);
        }

    }
}
