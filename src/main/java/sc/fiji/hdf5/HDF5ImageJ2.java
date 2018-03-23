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

import ch.systemsx.cisd.base.mdarray.MDByteArray;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;
import ch.systemsx.cisd.base.mdarray.MDShortArray;
import ch.systemsx.cisd.hdf5.*;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import net.imagej.Dataset;
import net.imagej.DefaultDataset;


public class HDF5ImageJ2 {

    public static Dataset hdf5read(String filename, String[] datasets, int nFrames, int nChannels)
    {
        return loadDataset( filename, datasets, nFrames, nChannels);
    }

    public static Dataset hdf5read( String filename, String[] datasets, String layout)
    {
        return loadDataset( filename, datasets, layout);
    }

    static Dataset loadDataset(String filename, String dataset)
    {
        try {
            IHDF5ReaderConfigurator conf = HDF5Factory.configureForReading(filename);
            conf.performNumericConversions();
            IHDF5Reader reader = conf.reader();
            ImagePlus imp = null;
            int dimensions = 0;
            int slices     = 0;
            int rows       = 0;
            int columns    = 0;
            boolean isRGB  = false;
            int bitdepth   = 0;
            double maxGray = 1;
            String typeText = "";

            // load data set
            //
            IJ.showStatus( "Loading " + dataset);
            HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation(dataset);
            float[] element_size_um = {1,1,1};
            try {
                element_size_um = reader.float32().getArrayAttr(dataset, "element_size_um");
            }
            catch (HDF5Exception err) {
                IJ.log("Warning: Can't read attribute 'element_size_um' from file '" + filename
                        + "', dataset '" + dataset + "':\n"
                        + err + "\n"
                        + "Assuming element size of 1 x 1 x 1 um^3");
            }

            // in first call create hyperstack
            //
            if (imp == null) {
                dimensions = dsInfo.getRank();
                typeText = dsInfoToTypeString(dsInfo);
                if (dimensions == 2) {
                    slices = 1;
                    rows = (int)dsInfo.getDimensions()[0];
                    columns = (int)dsInfo.getDimensions()[1];
                } else if (dimensions == 3) {
                    slices  = (int)dsInfo.getDimensions()[0];
                    rows    = (int)dsInfo.getDimensions()[1];
                    columns = (int)dsInfo.getDimensions()[2];
                    if( typeText.equals( "uint8") && columns == 3)
                    {
                        slices  = 1;
                        rows    = (int)dsInfo.getDimensions()[0];
                        columns = (int)dsInfo.getDimensions()[1];
                        isRGB   = true;
                    }
                } else if (dimensions == 4 && typeText.equals( "uint8")) {
                    slices  = (int)dsInfo.getDimensions()[0];
                    rows    = (int)dsInfo.getDimensions()[1];
                    columns = (int)dsInfo.getDimensions()[2];
                    isRGB   = true;
                } else {
                    IJ.error( dataset + ": rank " + dimensions + " of type " + typeText + " not supported (yet)");
                    return null;
                }

                bitdepth = assignHDF5TypeToImagePlusBitdepth( typeText, isRGB);



                imp = IJ.createHyperStack( filename + ": " + dataset,
                        columns, rows, 1, slices, 1, bitdepth);
                imp.getCalibration().pixelDepth  = element_size_um[0];
                imp.getCalibration().pixelHeight = element_size_um[1];
                imp.getCalibration().pixelWidth  = element_size_um[2];
                imp.getCalibration().setUnit("micrometer");
                imp.setDisplayRange(0,255);
            }

            // copy slices to hyperstack
            int sliceSize = columns * rows;

            if (typeText.equals( "uint8") && isRGB == false) {
                MDByteArray rawdata = reader.uint8().readMDArray(dataset);
                for( int lev = 0; lev < slices; ++lev) {
                    ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex(
                            1, lev+1, 1));
                    System.arraycopy( rawdata.getAsFlatArray(), lev*sliceSize,
                            (byte[])ip.getPixels(),   0,
                            sliceSize);
                }
                maxGray = 255;
            }  else if (typeText.equals( "uint8") && isRGB) {  // RGB data
                MDByteArray rawdata = reader.uint8().readMDArray(dataset);
                byte[] srcArray = rawdata.getAsFlatArray();


                for( int lev = 0; lev < slices; ++lev) {
                    ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex(
                            1, lev+1, 1));
                    int[] trgArray = (int[])ip.getPixels();
                    int srcOffset = lev*sliceSize*3;

                    for( int rc = 0; rc < sliceSize; ++rc)
                    {
                        int red   = srcArray[srcOffset + rc*3] & 0xff;
                        int green = srcArray[srcOffset + rc*3 + 1] & 0xff;
                        int blue  = srcArray[srcOffset + rc*3 + 2] & 0xff;
                        trgArray[rc] = (red<<16) + (green<<8) + blue;
                    }

                }
                maxGray = 255;

            } else if (typeText.equals( "uint16")) {
                MDShortArray rawdata = reader.uint16().readMDArray(dataset);
                for( int lev = 0; lev < slices; ++lev) {
                    ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex(
                            1, lev+1, 1));
                    System.arraycopy( rawdata.getAsFlatArray(), lev*sliceSize,
                            (short[])ip.getPixels(),   0,
                            sliceSize);
                }
                short[] data = rawdata.getAsFlatArray();
                for (int i = 0; i < data.length; ++i) {
                    if (data[i] > maxGray) maxGray = data[i];
                }
            } else if (typeText.equals( "int16")) {
                MDShortArray rawdata = reader.int16().readMDArray(dataset);
                for( int lev = 0; lev < slices; ++lev) {
                    ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex(
                            1, lev+1, 1));
                    System.arraycopy( rawdata.getAsFlatArray(), lev*sliceSize,
                            (short[])ip.getPixels(),   0,
                            sliceSize);
                }
                short[] data = rawdata.getAsFlatArray();
                for (int i = 0; i < data.length; ++i) {
                    if (data[i] > maxGray) maxGray = data[i];
                }
            } else if (typeText.equals( "float32") || typeText.equals( "float64") ) {
                MDFloatArray rawdata = reader.float32().readMDArray(dataset);
                for( int lev = 0; lev < slices; ++lev) {
                    ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex(
                            1, lev+1, 1));
                    System.arraycopy( rawdata.getAsFlatArray(), lev*sliceSize,
                            (float[])ip.getPixels(),   0,
                            sliceSize);
                }
                float[] data = rawdata.getAsFlatArray();
                for (int i = 0; i < data.length; ++i) {
                    if (data[i] > maxGray) maxGray = data[i];
                }
            }

            reader.close();
        }

        catch (HDF5Exception err)
        {
            IJ.error("Error while opening '" + filename
                    + "', dataset '" + dataset + "':\n"
                    + err);
        }
        catch (Exception err)
        {
            IJ.error("Error while opening '" + filename
                    + "', dataset '" + dataset + "':\n"
                    + err);
        }
        catch (OutOfMemoryError o)
        {
            IJ.outOfMemory("Load HDF5");
        }
        //return null;

        return new DefaultDataset(null, null);
    }

    static Dataset loadDataset(String filename, String[] datasets, int nFrames, int nChannels)
    {
        String dsetName = "";
        try {
            IHDF5ReaderConfigurator conf = HDF5Factory.configureForReading(filename);
            conf.performNumericConversions();
            IHDF5Reader reader = conf.reader();
            ImagePlus imp = null;
            int rank      = 0;
            int nLevels   = 0;
            int nRows     = 0;
            int nCols     = 0;
            boolean isRGB = false;
            int nBits     = 0;
            double maxGray = 1;
            String typeText = "";
            for (int frame = 0; frame < nFrames; ++frame) {
                for (int channel = 0; channel < nChannels; ++channel) {
                    // load data set
                    //
                    dsetName = datasets[frame*nChannels+channel];
                    IJ.showStatus( "Loading " + dsetName);
                    IJ.showProgress( frame*nChannels+channel+1, nFrames*nChannels);
                    HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation(dsetName);
                    float[] element_size_um = {1,1,1};
                    try {
                        element_size_um = reader.float32().getArrayAttr(dsetName, "element_size_um");
                    }
                    catch (HDF5Exception err) {
                        IJ.log("Warning: Can't read attribute 'element_size_um' from file '" + filename
                                + "', dataset '" + dsetName + "':\n"
                                + err + "\n"
                                + "Assuming element size of 1 x 1 x 1 um^3");
                    }

                    // in first call create hyperstack
                    //
                    if (imp == null) {
                        rank = dsInfo.getRank();
                        typeText = dsInfoToTypeString(dsInfo);
                        if (rank == 2) {
                            nLevels = 1;
                            nRows = (int)dsInfo.getDimensions()[0];
                            nCols = (int)dsInfo.getDimensions()[1];
                        } else if (rank == 3) {
                            nLevels = (int)dsInfo.getDimensions()[0];
                            nRows   = (int)dsInfo.getDimensions()[1];
                            nCols   = (int)dsInfo.getDimensions()[2];
                            if( typeText.equals( "uint8") && nCols == 3)
                            {
                                nLevels = 1;
                                nRows = (int)dsInfo.getDimensions()[0];
                                nCols = (int)dsInfo.getDimensions()[1];
                                isRGB = true;
                            }
                        } else if (rank == 4 && typeText.equals( "uint8")) {
                            nLevels = (int)dsInfo.getDimensions()[0];
                            nRows   = (int)dsInfo.getDimensions()[1];
                            nCols   = (int)dsInfo.getDimensions()[2];
                            isRGB   = true;
                        } else {
                            IJ.error( dsetName + ": rank " + rank + " of type " + typeText + " not supported (yet)");
                            return null;
                        }

                        nBits = assignHDF5TypeToImagePlusBitdepth( typeText, isRGB);


                        imp = IJ.createHyperStack( filename + ": " + dsetName,
                                nCols, nRows, nChannels, nLevels, nFrames, nBits);
                        imp.getCalibration().pixelDepth  = element_size_um[0];
                        imp.getCalibration().pixelHeight = element_size_um[1];
                        imp.getCalibration().pixelWidth  = element_size_um[2];
                        imp.getCalibration().setUnit("micrometer");
                        imp.setDisplayRange(0,255);
                    }

                    // copy slices to hyperstack
                    int sliceSize = nCols * nRows;

                    if (typeText.equals( "uint8") && isRGB == false) {
                        MDByteArray rawdata = reader.uint8().readMDArray(dsetName);
                        for( int lev = 0; lev < nLevels; ++lev) {
                            ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex(
                                    channel+1, lev+1, frame+1));
                            System.arraycopy( rawdata.getAsFlatArray(), lev*sliceSize,
                                    (byte[])ip.getPixels(),   0,
                                    sliceSize);
                        }
                        maxGray = 255;
                    }  else if (typeText.equals( "uint8") && isRGB) {  // RGB data
                        MDByteArray rawdata = reader.uint8().readMDArray(dsetName);
                        byte[] srcArray = rawdata.getAsFlatArray();


                        for( int lev = 0; lev < nLevels; ++lev) {
                            ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex(
                                    channel+1, lev+1, frame+1));
                            int[] trgArray = (int[])ip.getPixels();
                            int srcOffset = lev*sliceSize*3;

                            for( int rc = 0; rc < sliceSize; ++rc)
                            {
                                int red   = srcArray[srcOffset + rc*3] & 0xff;
                                int green = srcArray[srcOffset + rc*3 + 1] & 0xff;
                                int blue  = srcArray[srcOffset + rc*3 + 2] & 0xff;
                                trgArray[rc] = (red<<16) + (green<<8) + blue;
                            }

                        }
                        maxGray = 255;

                    } else if (typeText.equals( "uint16")) {
                        MDShortArray rawdata = reader.uint16().readMDArray(dsetName);
                        for( int lev = 0; lev < nLevels; ++lev) {
                            ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex(
                                    channel+1, lev+1, frame+1));
                            System.arraycopy( rawdata.getAsFlatArray(), lev*sliceSize,
                                    (short[])ip.getPixels(),   0,
                                    sliceSize);
                        }
                        short[] data = rawdata.getAsFlatArray();
                        for (int i = 0; i < data.length; ++i) {
                            if (data[i] > maxGray) maxGray = data[i];
                        }
                    } else if (typeText.equals( "int16")) {
                        MDShortArray rawdata = reader.int16().readMDArray(dsetName);
                        for( int lev = 0; lev < nLevels; ++lev) {
                            ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex(
                                    channel+1, lev+1, frame+1));
                            System.arraycopy( rawdata.getAsFlatArray(), lev*sliceSize,
                                    (short[])ip.getPixels(),   0,
                                    sliceSize);
                        }
                        short[] data = rawdata.getAsFlatArray();
                        for (int i = 0; i < data.length; ++i) {
                            if (data[i] > maxGray) maxGray = data[i];
                        }
                    } else if (typeText.equals( "float32") || typeText.equals( "float64") ) {
                        MDFloatArray rawdata = reader.float32().readMDArray(dsetName);
                        for( int lev = 0; lev < nLevels; ++lev) {
                            ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex(
                                    channel+1, lev+1, frame+1));
                            System.arraycopy( rawdata.getAsFlatArray(), lev*sliceSize,
                                    (float[])ip.getPixels(),   0,
                                    sliceSize);
                        }
                        float[] data = rawdata.getAsFlatArray();
                        for (int i = 0; i < data.length; ++i) {
                            if (data[i] > maxGray) maxGray = data[i];
                        }
                    }
                }
            }
            reader.close();

            // aqdjust max gray
            for( int c = 1; c <= nChannels; ++c)
            {
                imp.setC(c);
                imp.setDisplayRange(0,maxGray);
            }

            imp.setC(1);
        }

        catch (HDF5Exception err)
        {
            IJ.error("Error while opening '" + filename
                    + "', dataset '" + dsetName + "':\n"
                    + err);
        }
        catch (Exception err)
        {
            IJ.error("Error while opening '" + filename
                    + "', dataset '" + dsetName + "':\n"
                    + err);
        }
        catch (OutOfMemoryError o)
        {
            IJ.outOfMemory("Load HDF5");
        }
        //return null;

        return new DefaultDataset(null, null);
    }

    static Dataset loadDataset( String filename, String[] datasets, String layout)
    {
        return new DefaultDataset(null, null);
    }

    //-----------------------------------------------------------------------------
    static int assignHDF5TypeToImagePlusBitdepth( String type, boolean isRGB) {
        int nBits = 0;
        if (type.equals("uint8")) {
            if( isRGB ) {
                nBits = 24;
            } else {
                nBits = 8;
            }
        } else if (type.equals("uint16") || type.equals("int16")) {
            nBits = 16;
        } else if (type.equals("float32") || type.equals("float64")) {
            nBits = 32;
        } else {
            IJ.error("Type '" + type + "' Not handled yet!");
        }
        return nBits;
    }

    //-----------------------------------------------------------------------------
    static String dsInfoToTypeString( HDF5DataSetInformation dsInfo) {
        HDF5DataTypeInformation dsType = dsInfo.getTypeInformation();
        String typeText = "";

        if (dsType.isSigned() == false) {
            typeText += "u";
        }

        switch( dsType.getDataClass())
        {
            case INTEGER:
                typeText += "int" + 8*dsType.getElementSize();
                break;
            case FLOAT:
                typeText += "float" + 8*dsType.getElementSize();
                break;
            default:
                typeText += dsInfo.toString();
        }
        return typeText;
    }
}
