package ch.epfl.biop.qupathfiji;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;

import java.io.File;

public class SpimDataQuPath extends AbstractSpimData<SequenceDescription>
{
    public SpimDataQuPath(final File basePath, final SequenceDescription sequenceDescription, final ViewRegistrations viewRegistrations )
    {
        super( basePath, sequenceDescription, viewRegistrations );
    }

    protected SpimDataQuPath()
    {}
}
