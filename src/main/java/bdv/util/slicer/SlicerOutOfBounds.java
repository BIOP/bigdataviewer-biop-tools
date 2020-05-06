package bdv.util.slicer;

import net.imglib2.*;
import net.imglib2.outofbounds.OutOfBounds;

public class SlicerOutOfBounds< T > extends AbstractLocalizable implements OutOfBounds< T > {
    final protected RandomAccess< T > outOfBoundsRandomAccess;

    /**
     * Dimensions of the wrapped {@link RandomAccessible}.
     */
    final protected long[] dimension;

    /**
     * Minimum of the wrapped {@link RandomAccessible}.
     */
    final protected long[] min;

    final protected long[] beforeMin;

    /**
     * Maximum of the wrapped {@link RandomAccessible}.
     */
    final protected long[] max;

    final protected long[] pastMax;

    final protected boolean[] dimIsOutOfBounds;

    protected boolean isOutOfBounds = false;

    // Useless
    public SlicerOutOfBounds( final SlicerOutOfBounds< T > outOfBounds )
    {
        super( outOfBounds.numDimensions() );
        dimension = new long[ n ];
        min = new long[ n ];
        beforeMin = new long[ n ];
        max = new long[ n ];
        pastMax = new long[ n ];
        dimIsOutOfBounds = new boolean[ n ];
        for ( int d = 0; d < n; ++d )
        {
            dimension[ d ] = outOfBounds.dimension[ d ];
            min[ d ] = outOfBounds.min[ d ];
            beforeMin[ d ] = outOfBounds.beforeMin[ d ];
            max[ d ] = outOfBounds.max[ d ];
            pastMax[ d ] = outOfBounds.pastMax[ d ];
            position[ d ] = outOfBounds.position[ d ];
            dimIsOutOfBounds[ d ] = outOfBounds.dimIsOutOfBounds[ d ];
        }

        outOfBoundsRandomAccess = outOfBounds.outOfBoundsRandomAccess.copyRandomAccess();
    }

    //
    public < F extends Interval & RandomAccessible< T > > SlicerOutOfBounds( final F f )
    {
        super( f.numDimensions() );
        dimension = new long[ n ];
        f.dimensions( dimension );
        min = new long[ n ];
        f.min( min );
        max = new long[ n ];
        f.max( max );
        beforeMin = new long[ n ];
        pastMax = new long[ n ];
        for ( int d = 0; d < n; ++d )
        {
            beforeMin[ d ] = min[ d ] - 1;
            pastMax[ d ] = max[ d ] + 1;
        }
        dimIsOutOfBounds = new boolean[ n ];

        outOfBoundsRandomAccess = f.randomAccess();
    }

    final protected void checkOutOfBounds()
    {
        for ( int d = 0; d < n; ++d )
        {
            if ( dimIsOutOfBounds[ d ] )
            {
                isOutOfBounds = true;
                return;
            }
        }
        isOutOfBounds = false;
    }

    /* OutOfBounds */

    @Override
    public boolean isOutOfBounds()
    {
        return isOutOfBounds;
    }

    /* Sampler */

    @Override
    public T get()
    {
        return outOfBoundsRandomAccess.get();
    }

    @Override
    final public SlicerOutOfBounds< T > copy()
    {
        return new SlicerOutOfBounds< T >( this );
    }

    /* RandomAccess */

    @Override
    final public SlicerOutOfBounds< T > copyRandomAccess()
    {
        return copy();
    }

    /* Positionable */

    @Override
    final public void fwd( final int d )
    {
        final long p = ++position[ d ];
        if ( p == min[ d ] )
        {
            dimIsOutOfBounds[ d ] = false;
            checkOutOfBounds();
        }
        else if ( p == pastMax[ d ] )
            dimIsOutOfBounds[ d ] = isOutOfBounds = true;

        final long q = outOfBoundsRandomAccess.getLongPosition( d );
        if ( q == max[ d ] ) {
            outOfBoundsRandomAccess.setPosition(min[d], d);
        } else {
            outOfBoundsRandomAccess.fwd(d);
        }
    }

    @Override
    final public void bck( final int d )
    {
        final long p = --position[ d ];
        if ( p == beforeMin[ d ] )
            dimIsOutOfBounds[ d ] = isOutOfBounds = true;
        else if ( p == max[ d ] )
        {
            dimIsOutOfBounds[ d ] = false;
            checkOutOfBounds();
        }

        final long q = outOfBoundsRandomAccess.getLongPosition( d );
        if ( q == min[ d ] )
            outOfBoundsRandomAccess.setPosition( max[ d ], d );
        else
            outOfBoundsRandomAccess.bck( d );
    }

    @Override
    final public void setPosition( final long position, final int d )
    {
        this.position[ d ] = position;
        final long minD = min[ d ];
        final long maxD = max[ d ];
        if ( position < minD )
        {
            outOfBoundsRandomAccess.setPosition( maxD - ( maxD - position ) % dimension[ d ], d );
            dimIsOutOfBounds[ d ] = isOutOfBounds = true;
        }
        else if ( position > maxD )
        {
            outOfBoundsRandomAccess.setPosition( minD + ( position - minD ) % dimension[ d ], d );
            dimIsOutOfBounds[ d ] = isOutOfBounds = true;
        }
        else
        {
            outOfBoundsRandomAccess.setPosition( position, d );
            if ( isOutOfBounds )
            {
                dimIsOutOfBounds[ d ] = false;
                checkOutOfBounds();
            }
        }
    }

    @Override
    public void move( final long distance, final int d )
    {
        setPosition( getLongPosition( d ) + distance, d );
    }

    @Override
    public void move( final int distance, final int d )
    {
        move( ( long ) distance, d );
    }

    @Override
    public void move( final Localizable localizable )
    {
        for ( int d = 0; d < n; ++d )
            move( localizable.getLongPosition( d ), d );
    }

    @Override
    public void move( final int[] distance )
    {
        for ( int d = 0; d < n; ++d )
            move( distance[ d ], d );
    }

    @Override
    public void move( final long[] distance )
    {
        for ( int d = 0; d < n; ++d )
            move( distance[ d ], d );
    }

    @Override
    public void setPosition( final int position, final int d )
    {
        setPosition( ( long ) position, d );
    }

    @Override
    public void setPosition( final Localizable localizable )
    {
        for ( int d = 0; d < n; ++d )
            setPosition( localizable.getLongPosition( d ), d );
    }

    @Override
    public void setPosition( final int[] position )
    {
        for ( int d = 0; d < position.length; ++d )
            setPosition( position[ d ], d );
    }

    @Override
    public void setPosition( final long[] position )
    {
        for ( int d = 0; d < position.length; ++d )
            setPosition( position[ d ], d );
    }

    /* Object */

    //@Override
    //public String toString()
    /*{
        return Util.printCoordinates( position ) + " = " + get();
    }*/

}
