package ch.epfl.biop.qupathfiji;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import mpicbg.spim.data.generic.base.NamedEntity;

import java.net.URI;

public class QuPathEntryEntity extends NamedEntity implements Comparable<QuPathEntryEntity>
{

    public QuPathEntryEntity(final int id, final String name)
    {
        super( id, name );
    }

    public QuPathEntryEntity(final int id )
    {
        this( id, Integer.toString( id ) );
    }

    /**
     * Get the unique id of this location.
     */
    @Override
    public int getId()
    {
        return super.getId();
    }

    /**
     * Get the name of this tile.
     *
     * The name is used for example to replace it in filenames when
     * opening individual 3d-stacks (e.g. SPIM_TL20_Tile1_Angle45.tif)
     */
    @Override
    public String getName()
    {
        return super.getName();
    }

    /**
     * Set the name of this tile.
     */
    @Override
    public void setName( final String name )
    {
        super.setName( name );
    }

    /**
     * Compares the {@link #getId() ids}.
     */
    @Override
    public int compareTo( final QuPathEntryEntity o )
    {
        return getId() - o.getId();
    }

    protected QuPathEntryEntity()
    {}

    public static String getNameFromURIAndSerie(URI uri, int iSerie) {
        return new Gson().toJson(new QuPathSourceIdentifier(uri, iSerie));
    }

    public static URI getUri(String name) {
        return new Gson().fromJson(JsonParser.parseString(name), QuPathSourceIdentifier.class).uri;
    }

    public static int getSerie(String name) {
        return new Gson().fromJson(JsonParser.parseString(name), QuPathSourceIdentifier.class).iSerie;
    }

    public static class QuPathSourceIdentifier {
        final URI uri;
        final int iSerie;

        public QuPathSourceIdentifier(URI uri, int iSerie) {
            this.iSerie = iSerie;
            this.uri = uri;
        }
    }
}
