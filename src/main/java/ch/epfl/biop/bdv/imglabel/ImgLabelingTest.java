package ch.epfl.biop.bdv.imglabel;

import bdv.util.*;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imagej.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelingType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.labeleditor.core.model.DefaultLabelEditorModel;
import sc.fiji.labeleditor.core.view.LabelEditorView;
import sc.fiji.labeleditor.plugin.interfaces.bdv.BdvInterface;

import java.io.IOException;

@Plugin(type = Command.class, menuPath = "BDV_SciJava>TestLabel")
public class ImgLabelingTest implements Command {

    @Parameter
    ImageJ ij;

    @Parameter(type = ItemIO.BOTH)
    BdvHandle bdvh;

    @Parameter
    int sourceIndex;

    @Parameter
    int timepoint;

    public void run() {

        Img input = null;

        if (!(bdvh instanceof BdvHandlePanel)) {
            System.err.println("Your bdv handle is not an instance of BdvHandlePanel -> This command cannot work");
            return;
        }

        //try {
            Source src = bdvh.getViewerPanel().getState().getSources().get(sourceIndex).getSpimSource();

            RandomAccessibleInterval<IntType> rai = convertRealSource(src).getSource(timepoint, 0);
            /*ImgLabeling labeling = new ImgLabeling(rai);

            final Cursor<LabelingType< Integer >> labelCursor = Views.flatIterable( labeling ).cursor();
            for ( final UnsignedByteType inputC : Views.flatIterable(rai) )
            {
                final LabelingType< Integer > element = labelCursor.next();
                if ( inputC.get() != 0 )
                    element.add( inputC.get() );
            }*/

            //final Img< FloatType > img2 = ImageJFunctions.convertFloat( labelMap );

            final Dimensions dims = rai;// img2;
            final IntType t = new IntType();
            final RandomAccessibleInterval< IntType > img = Util.getArrayOrCellImgFactory( dims, t ).create( dims );
            final ImgLabeling< Integer, IntType > labeling = new ImgLabeling<>( img );

            final Cursor< LabelingType< Integer > > labelCursor = Views.flatIterable( labeling ).cursor();
            for ( final IntType inputpix : Views.flatIterable( rai ))//ImageJFunctions.wrapByte( labelMap ) ) )
            {
                final LabelingType< Integer > element = labelCursor.next();
                if ( inputpix.get() != 0 )
                    element.add( inputpix.get() );
            }




            AffineTransform3D at3D = new AffineTransform3D();

            src.getSourceTransform(timepoint,0,at3D);
            //BdvF

            //input = (Img) ij.io().open("C:\\Users\\nicol\\Desktop\\blobs.gif");

            //Img binary = (Img) ij.op().threshold().otsu(input);

            //ImgLabeling<Integer, IntType> labeling = ij.op().labeling().cca(binary, ConnectedComponents.StructuringElement.EIGHT_CONNECTED);

            DefaultLabelEditorModel<Integer> model = new DefaultLabelEditorModel<>(labeling);


            LabelEditorView<Integer> view = new LabelEditorView<>(model);
            view.renderers().addDefaultRenderers();

            model.colors().getDefaultFaceColor().set(255,255,0,55);

            view.renderers().forEach(renderer -> BdvFunctions.show(renderer.getOutput(), renderer.getName(), Bdv.options().addTo(bdvh).sourceTransform(at3D)));

            BdvInterface.control(model, view, (BdvHandlePanel) bdvh);

        //} catch (IOException e) {
          //  e.printStackTrace();
        //}
    }


    static public Source<IntType> convertRealSource(Source<RealType> iniSrc) {
        Converter<RealType,IntType> cvt = (i, o) -> o.set(((int) (i.getRealDouble()/64)));
        Source<IntType> cvtSrc = new Source<IntType>() {
            @Override
            public boolean isPresent(int t) {
                return iniSrc.isPresent(t);
            }

            @Override
            public RandomAccessibleInterval<IntType> getSource(int t, int level) {
                return Converters.convert(iniSrc.getSource(t,level),cvt,new IntType());
            }

            @Override
            public RealRandomAccessible<IntType> getInterpolatedSource(int t, int level, Interpolation method) {
                return Converters.convert(iniSrc.getInterpolatedSource(t,level,method),cvt,new IntType());
            }

            @Override
            public void getSourceTransform(int t, int level, AffineTransform3D transform) {
                iniSrc.getSourceTransform(t,level,transform);
            }

            @Override
            public IntType getType() {
                return new IntType();
            }

            @Override
            public String getName() {
                return iniSrc.getName();
            }

            @Override
            public VoxelDimensions getVoxelDimensions() {
                return iniSrc.getVoxelDimensions();
            }

            @Override
            public int getNumMipmapLevels() {
                return iniSrc.getNumMipmapLevels();
            }
        };

        return cvtSrc;
    }

    static public <T> Source<IntType> convertSource(Source<T> iniSrc) {
        if (iniSrc.getType() instanceof IntType) return (Source<IntType>) iniSrc;
        if (iniSrc.getType() instanceof RealType) return convertRealSource((Source<RealType>) iniSrc);
        System.err.println("Cannot convert source to Unsigned Short Type, "+iniSrc.getType().getClass().getSimpleName()+" cannot be converted to RealType");
        return null;
    }


}
