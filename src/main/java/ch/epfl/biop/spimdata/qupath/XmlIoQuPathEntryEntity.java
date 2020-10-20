package ch.epfl.biop.spimdata.qupath;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.base.ViewSetupAttributeIo;
import mpicbg.spim.data.generic.base.XmlIoNamedEntity;
import org.jdom2.Element;

@ViewSetupAttributeIo( name = "qupathentryentity", type = QuPathEntryEntity.class )
public class XmlIoQuPathEntryEntity extends XmlIoNamedEntity<QuPathEntryEntity>
{
    public XmlIoQuPathEntryEntity()
    {
        super( "qupathentryentity", QuPathEntryEntity.class );
    }

    @Override
    public Element toXml(final QuPathEntryEntity qpee )
    {
        final Element elem = super.toXml( qpee );
        elem.addContent(XmlHelpers.textElement( "QuPathProjectLocation", qpee.getQuPathProjectionLocation()));
        return elem;
    }

    @Override
    public QuPathEntryEntity fromXml( final Element elem ) throws SpimDataException
    {
        final QuPathEntryEntity qupathEntry = super.fromXml( elem );

        qupathEntry.setQuPathProjectionLocation( elem.getChildText( "QuPathProjectLocation" ) );
        return qupathEntry;
    }
}
