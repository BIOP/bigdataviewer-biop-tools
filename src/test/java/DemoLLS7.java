import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import mpicbg.spim.data.generic.AbstractSpimData;

import java.io.File;

public class DemoLLS7 {

    // TODO
    public static void main(String... args) {
        /*String lls7DatasetURL = "https://zenodo.org/records/7117784/files/RBC_full_time_series.czi";
        File flls7 = DatasetHelper.getDataset(lls7DatasetURL);

        // Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
        AbstractSpimData<?> dataset = (AbstractSpimData<?>) cs.run(CreateBdvDatasetBioFormatsCommand.class,
                true,
                "datasetname", "Egg_Chamber",
                "unit", "MICROMETER",
                "files", new File[]{eggChamber},
                "split_rgb_channels", false,
                "plane_origin_convention", "CENTER",
                "auto_pyramidize", true,
                "disable_memo", false
        ).get().getOutput("spimdata");

        SourceAndConverter<?>[] eggChamberSources = ss.getSourceAndConverterFromSpimdata(dataset).toArray(new SourceAndConverter<?>[0]);

        BdvHandle bdvh = ds.getNewBdv();*/


    }

}
