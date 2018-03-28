/*-
 * #%L
 * HDF5 plugin for ImageJ and Fiji - IJ2 interface.
 * %%
 * Copyright (C) 2011 - 2018 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
//
// Part of the HDF5 plugin for ImageJ
// written by: Radoslaw Ejsmont (radoslaw@ejsmont.net)
// Copyright: GPL v2
//

package sc.fiji.hdf5;

import ch.systemsx.cisd.hdf5.*;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImageJ;
import net.imagej.ImageJService;
import net.imagej.axis.Axis;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.*;
import net.imglib2.type.numeric.integer.*;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Fraction;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;

import java.io.File;

@Plugin(type=Service.class)
public class HDF5ImageJ2 extends AbstractService implements ImageJService {

    @Parameter
    private DatasetService datasetService;


    public Dataset read(String filename, String dataset)
    {
        return readDataset(filename, dataset);
    }

    public Dataset read(String filename, String[] datasets)
    {
        return readDataset(filename, datasets[0]);
    }

    public Dataset read(String filename, String[] datasets, int nFrames, int nChannels)
    {
        return readDataset(filename, datasets[0]);
    }

    public Dataset read( String filename, String[] datasets, String layout)
    {
        return readDataset(filename, datasets[0]);
    }

    @SuppressWarnings("unchecked")
    private Dataset readDataset(String filename, String dataset)
    {
        IHDF5ReaderConfigurator conf = HDF5Factory.configureForReading(filename);
        conf.performNumericConversions();
        IHDF5Reader reader = conf.reader();

        float[] element_size_um = {1,1,1};
        try {
            element_size_um = reader.float32().getArrayAttr(dataset, "element_size_um");
        }
        catch (HDF5Exception err) {}
        
        Img img = createImage(reader, dataset);
        reader.close();

        Dataset ds = datasetService.create(img);

        Axis[] axes = new Axis[ds.numDimensions()];

        for (int i = 0; i < ds.numDimensions(); i++ ) {
            axes[i] = new DefaultLinearAxis(element_size_um[i]);
        }

        return ds;
    }

    private Img<?> createImage(IHDF5Reader reader, String dataset)
    {
        HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation(dataset);
        HDF5DataTypeInformation dsType = dsInfo.getTypeInformation();

        Img<?> img = null;
        long[] dim = dsInfo.getDimensions();

        // HDF5 array's dimensions are reversed
        for(int i = 0; i < dim.length / 2; i++)
        {
            long temp = dim[i];
            dim[i] = dim[dim.length - i - 1];
            dim[dim.length - i - 1] = temp;
        }

        switch(dsType.getDataClass())
        {
            case INTEGER:
                img = createIntegerImage(reader, dataset, dim, dsType);
                break;
            case FLOAT:
                img = createFloatImage(reader, dataset, dim, dsType);
                break;
        }

        return img;
    }

    private Img<?> createIntegerImage(IHDF5Reader reader, String dataset, long[] dimensions, HDF5DataTypeInformation dsType)
    {
        Img<?> img = null;
        int bits = dsType.getElementSize() * 8;
        boolean signed = dsType.isSigned();

        switch(bits)
        {
            case 8:
                ByteArray byteData = new ByteArray(reader.int8().readArray(dataset));
                if (signed) {
                    ArrayImg<ByteType, ByteArray> byteImg = new ArrayImg<>(byteData, dimensions, new Fraction());
                    byteImg.setLinkedType(new ByteType(byteImg));
                    img = byteImg;
                } else {
                    ArrayImg<UnsignedByteType, ByteArray> ubyteImg = new ArrayImg<>(byteData, dimensions, new Fraction());
                    ubyteImg.setLinkedType(new UnsignedByteType(ubyteImg));
                    img = ubyteImg;
                }
                break;
            case 16:
                ShortArray shortData = new ShortArray(reader.int16().readArray(dataset));
                if (signed) {
                    ArrayImg<ShortType, ShortArray> shortImg = new ArrayImg<>(shortData, dimensions, new Fraction());
                    shortImg.setLinkedType(new ShortType(shortImg));
                    img = shortImg;
                } else {
                    ArrayImg<UnsignedShortType, ShortArray> ushortImg = new ArrayImg<>(shortData, dimensions, new Fraction());
                    ushortImg.setLinkedType(new UnsignedShortType(ushortImg));
                    img = ushortImg;
                }
                break;
            case 32:
                IntArray intData = new IntArray(reader.int32().readArray(dataset));
                if (signed) {
                    ArrayImg<IntType, IntArray> intImg = new ArrayImg<>(intData, dimensions, new Fraction());
                    intImg.setLinkedType(new IntType(intImg));
                    img = intImg;
                } else {
                    ArrayImg<UnsignedIntType, IntArray> uintImg = new ArrayImg<>(intData, dimensions, new Fraction());
                    uintImg.setLinkedType(new UnsignedIntType(uintImg));
                    img = uintImg;
                }
                break;
            case 64:
                LongArray longData = new LongArray(reader.int64().readArray(dataset));
                if (signed) {
                    ArrayImg<LongType, LongArray> longImg = new ArrayImg<>(longData, dimensions, new Fraction());
                    longImg.setLinkedType(new LongType(longImg));
                    img = longImg;
                } else {
                    ArrayImg<UnsignedLongType, LongArray> ulongImg = new ArrayImg<>(longData, dimensions, new Fraction());
                    ulongImg.setLinkedType(new UnsignedLongType(ulongImg));
                    img = ulongImg;
                }
                break;
        }

        return img;
    }

    private Img<?> createFloatImage(IHDF5Reader reader, String dataset, long[] dimensions, HDF5DataTypeInformation dsType)
    {
        Img<?> img = null;
        int bits = dsType.getElementSize() * 8;

        switch(bits)
        {
            case 32:
                FloatArray floatData = new FloatArray(reader.float32().readArray(dataset));
                ArrayImg<FloatType, FloatArray> floatImg = new ArrayImg<>(floatData, dimensions, new Fraction());
                floatImg.setLinkedType(new FloatType(floatImg));
                img = floatImg;
                break;
            case 64:
                DoubleArray doubleData = new DoubleArray(reader.float64().readArray(dataset));
                ArrayImg<DoubleType, DoubleArray> doubleImg = new ArrayImg<>(doubleData, dimensions, new Fraction());
                doubleImg.setLinkedType(new DoubleType(doubleImg));
                img = doubleImg;
                break;
        }

        return img;
    }

    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        /*
        final File file = ij.ui().chooseFile(null, "open");

        if (file != null) {

            final HDF5ImageJ2 hdf5s = ij.get(HDF5ImageJ2.class);
            Dataset dataset = hdf5s.read(file.getPath(), "/raw/fused/channel1");

            ij.ui().show(dataset);
        }
        */
    }
}
