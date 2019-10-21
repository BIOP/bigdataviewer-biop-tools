package ch.epfl.biop.bdv.bioformats;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvStackSource;
import bdv.viewer.state.SourceGroup;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvSource;
import ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData;
import ch.epfl.biop.bdv.bioformats.imageloader.BioFormatsSetupLoader;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData.MILLIMETER;

import static ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData.MICROMETER;

import static ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData.NANOMETER;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"Open>Open Files with BioFormats Bdv Bridge")
public class OpenImageWithBioformatsBigdataviewerBridge implements Command {

    @Parameter
    File[] files;

    @Parameter
    boolean advancedParameters;

    @Parameter
    CommandService cs;

    @Parameter
    boolean autosetColor;

    @Parameter
    boolean setGrouping;

    @Parameter
    boolean positionConventionIsCenter=false;

    @Parameter(choices = {MILLIMETER,MICROMETER,NANOMETER})
    public String unit;

    @Parameter(type = ItemIO.OUTPUT)
    public BdvHandle bdv_h;

    @Override
    public void run() {
        Map<String, Object> predefinedParameters = new HashMap<>();
        predefinedParameters.put("inputFiles", files);
        predefinedParameters.put("unit", unit);
        predefinedParameters.put("positionConventionIsCenter", positionConventionIsCenter);

        if (!advancedParameters) {
            predefinedParameters.put("xmlFilePath", new File(files[0].getParent()));
            predefinedParameters.put("useBioFormatsCacheBlockSize", true);
            predefinedParameters.put("xmlFileName", "dataset.xml");
            predefinedParameters.put("cacheSizeX", 0);
            predefinedParameters.put("cacheSizeY", 0);
            predefinedParameters.put("cacheSizeZ", 0);
            predefinedParameters.put("saveDataset", false);
            predefinedParameters.put("switchZandC", false);
            predefinedParameters.put("verbose", false);
        }

        try {
            final AbstractSpimData asd = (AbstractSpimData) cs.run(BioFormatsConvertFilesToSpimData.class, true,predefinedParameters).get().getOutput("asd");
            List<BdvStackSource<?>> lbss = BdvFunctions.show(asd);
            bdv_h = lbss.get(0).getBdvHandle();

            if (autosetColor) {
                asd.getSequenceDescription().getViewSetupsOrdered().forEach(id_vs -> {
                            int idx = ((mpicbg.spim.data.sequence.ViewSetup) id_vs).getId();
                            BioFormatsSetupLoader bfsl = (BioFormatsSetupLoader) asd.getSequenceDescription().getImgLoader().getSetupImgLoader(idx);
                            lbss.get(idx).setColor(
                                    BioFormatsMetaDataHelper.getSourceColor((BioFormatsBdvSource) bfsl.concreteSource)
                            );
                            lbss.get(idx).setDisplayRange(0, 255);
                        }
                );
            }

            if (setGrouping) {
                Map<BioFormatsMetaDataHelper.BioformatsChannel, List<Integer>> srcsGroupedByChannel =
                        (Map<BioFormatsMetaDataHelper.BioformatsChannel, List<Integer>>)
                asd.getSequenceDescription()
                   .getViewSetupsOrdered().stream()
                   .map(obj -> ((mpicbg.spim.data.sequence.ViewSetup) obj).getId())
                   .collect(Collectors.groupingBy(e -> {
                               BioFormatsSetupLoader bfsl = (BioFormatsSetupLoader) asd.getSequenceDescription().getImgLoader().getSetupImgLoader((int)e);
                               return
                               new BioFormatsMetaDataHelper.BioformatsChannel((IMetadata) bfsl.getReader().getMetadataStore(), bfsl.iSerie, bfsl.iChannel, bfsl.getReader().isRGB());
                           },
                           Collectors.toList()));

                List<SourceGroup> sgs = srcsGroupedByChannel.entrySet().stream().map(
                        e -> {
                            SourceGroup sg = new SourceGroup(e.getKey().chName);
                            e.getValue().forEach(idx -> sg.addSource(idx));
                            return sg;
                        }
                ).collect(Collectors.toList());

                int idx = 0;
                while (idx<sgs.size()) {
                    if (idx<bdv_h.getViewerPanel().getVisibilityAndGrouping().getSourceGroups().size()) {
                        SourceGroup sg = bdv_h.getViewerPanel()
                                .getVisibilityAndGrouping()
                                .getSourceGroups().get(idx);
                        //System.out.println("sgs.get("+idx+").getName() = "+sgs.get(idx).getName());
                        sg.setName(sgs.get(idx).getName());
                        final int idx_cp = idx;
                        sgs.get(idx).getSourceIds().stream().forEach(
                                id -> {
                                    sg.addSource(id);
                                    bdv_h.getSetupAssignments().moveSetupToGroup(bdv_h.getSetupAssignments().getConverterSetups().get(id),
                                            bdv_h.getSetupAssignments().getMinMaxGroups().get(idx_cp));
                                }
                        );
                    } else {
                        bdv_h.getViewerPanel().addGroup(sgs.get(idx));
                    }
                    idx++;
                }

                // dirty but updates display - update do not have a public access
                SourceGroup dummy = new SourceGroup("dummy");
                bdv_h.getViewerPanel().addGroup(dummy);
                bdv_h.getViewerPanel().removeGroup(dummy);

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
