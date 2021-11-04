package ch.epfl.biop.sourceandconverter.processor;

import bdv.viewer.SourceAndConverter;

public class SourcesIdentity implements SourcesProcessor {
    @Override
    public SourceAndConverter[] apply(SourceAndConverter[] sourceAndConverters) {
        return sourceAndConverters;
    }

    public String toString() {
        return "Id";
    }
}
