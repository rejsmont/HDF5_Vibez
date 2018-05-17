package sc.fiji.hdf5;

import ch.systemsx.cisd.hdf5.*;
import io.scif.*;
import io.scif.config.SCIFIOConfig;
import io.scif.io.RandomAccessInputStream;
import io.scif.io.RandomAccessOutputStream;

import java.io.IOException;
import java.nio.*;
import java.util.*;

import io.scif.util.FormatTools;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import net.imagej.ImageJ;
import net.imagej.axis.Axes;
import net.imagej.axis.Axis;
import net.imagej.axis.AxisType;
import net.imagej.axis.CalibratedAxis;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import org.scijava.plugin.Plugin;
import org.scijava.util.ArrayUtils;

@Plugin(type = Format.class, name="HDF5")
public class HDF5Format extends AbstractFormat {

    @Override
    protected String[] makeSuffixArray() {
        return new String[] { "h5", "hdf5" };
    }

    // *** REQUIRED COMPONENTS ***

    // The Metadata class contains all format-specific metadata.
    // Your Metadata class should be filled with fields which define the
    // image format. For example, things like acquisition date, instrument,
    // excitation levels, etc.
    // In the implementation of populateImageMetadata, the format- specific
    // metadata is converted to a generalized ImageMetadata which can be
    // consumed by other components (e.g. readers/writers).
    // As the conversion to ImageMetadata is almost certainly lossy, preserving
    // the original format-specific metadata provides components like
    // Translators an opportunity to preserve as much original information
    // as possible.
    //
    // NB: if your format has a color table/LUT which you would like to expose,
    // it should implement the io.scif.HasColorTable interface.
    public static class Metadata extends AbstractMetadata {

        private IHDF5Reader reader;
        private List<DataSetInfo> datasets;

        public void setReader(IHDF5Reader reader) {
            this.reader = reader;
        }

        public IHDF5Reader getReader() {
            return reader;
        }

        public List<DataSetInfo> getDatasets() {
            return datasets;
        }

        public void setDatasets(List<DataSetInfo> datasets) {
            this.datasets = datasets;
        }

        // This method must be implemented for each concrete Metadata class.
        // Essentially, format-specific metadata is be populated during Parsing or
        // Translation. From the format-specific metadata, ImageMetadata
        // information, common to all formats - such as height, width, etc -
        // is populated here.
        @Override
        public void populateImageMetadata() {

            int imageCount = datasets.size();
            createImageMetadata(imageCount);

            for (int i = 0; i < imageCount; i++) {
                ImageMetadata iMeta = get(i);
                DataSetInfo dsInfo = datasets.get(i);
                iMeta.setPixelType(
                        FormatTools.pixelTypeFromString(
                                dsInfo.getType()
                        )
                );
                iMeta.setBitsPerPixel(
                        FormatTools.getBitsPerPixel(
                                iMeta.getPixelType()
                        )
                );
                String axisOrder = dsInfo.getAxisOrder();
                String[] axisLengths = dsInfo.getDimensions().split("x");
                String[] voxelSizes = dsInfo.getVoxelSize().split("x");
                boolean hasVoxelSizes = ! voxelSizes[0].equals("Unknown");
                for (int a = 0; a < axisLengths.length; a++) {
                    switch(axisOrder.charAt(axisLengths.length - a - 1)) {
                        case 'x':
                            iMeta.addAxis(Axes.X, Long.parseLong(axisLengths[a]));
                            System.out.printf("Adding axis %d: x axis, %s px.\n", a, axisLengths[a]);
                            if (hasVoxelSizes) {
                                iMeta.getAxis(a).calibratedValue(Double.parseDouble(voxelSizes[a]));
                                System.out.printf("Setting calibration on axis %d to %s.\n", a, voxelSizes[a]);
                            }
                            break;
                        case 'y':
                            iMeta.addAxis(Axes.Y, Long.parseLong(axisLengths[a]));
                            System.out.printf("Adding axis %d: y axis, %s px.\n", a, axisLengths[a]);
                            if (hasVoxelSizes) {
                                iMeta.getAxis(a).calibratedValue(Double.parseDouble(voxelSizes[a]));
                                System.out.printf("Setting calibration on axis %d to %s.\n", a, voxelSizes[a]);
                            }
                            break;
                        case 'z':
                            iMeta.addAxis(Axes.Z, Long.parseLong(axisLengths[a]));
                            System.out.printf("Adding axis %d: z axis, %s slices.\n", a, axisLengths[a]);
                            if (hasVoxelSizes) {
                                iMeta.getAxis(a).calibratedValue(Double.parseDouble(voxelSizes[a]));
                                System.out.printf("Setting calibration on axis %d to %s.\n", a, voxelSizes[a]);
                            }
                            break;
                        case 't':
                            iMeta.addAxis(Axes.TIME, Long.parseLong(axisLengths[a]));
                            System.out.printf("Adding axis %d: time axis, %s timepoints.\n", a, axisLengths[a]);
                            break;
                        case 'c':
                            iMeta.addAxis(Axes.CHANNEL, Long.parseLong(axisLengths[a]));
                            System.out.printf("Adding axis %d: channel axis, %s channels.\n", a, axisLengths[a]);
                            break;
                    }
                }
                iMeta.setPlanarAxisCount(2);
            }
        }
    }

    // The Parser is your interface with the image source.
    // It has one purpose: to take the raw image information and generate a
    // Metadata instance, populating all format-specific fields.
    public static class Parser extends AbstractParser<Metadata> {

        // In this method we populate the given Metadata object
        @Override
        public void typedParse(final RandomAccessInputStream stream,
                               final Metadata meta, final SCIFIOConfig config) throws IOException, FormatException
        {
            IHDF5ReaderConfigurator conf = HDF5Factory.configureForReading(stream.getFileName());
            conf.performNumericConversions();
            IHDF5Reader reader = conf.reader();
            HDF5LinkInformation link = reader.object().getLinkInformation("/");
            ArrayList<DataSetInfo> datasets = getDataSetInfo(reader, link);
            meta.setReader(reader);
            meta.setDatasets(datasets);
        }

        private ArrayList<DataSetInfo> getDataSetInfo(IHDF5Reader reader, HDF5LinkInformation link)
        {
            ArrayList<DataSetInfo> datasets = new ArrayList<DataSetInfo>();
            populateDatasetInfo(reader, link, datasets);

            return datasets;
        }

        private void populateDatasetInfo(IHDF5Reader reader, HDF5LinkInformation link, ArrayList<DataSetInfo> dataSets)
        {
            List<HDF5LinkInformation> members = reader.object().getGroupMemberInformation(link.getPath(), true);
            for (HDF5LinkInformation info : members) {
                switch (info.getType()) {
                    case DATASET:
                        /*
                         TODO: implement attribute retrieval
                         This should also retrieve all attributes and populate metadata with them. Voxel size reading
                         should be implemented inside Metadata object and read the actual values from populated HDF5
                         attributes of a given HDF5 dataset.
                         Currently it only implements basic functionality of the Vibez reader.
                         */
                        HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation(info.getPath());
                        String type = getDataSetTypes(dsInfo);
                        String dimensions = getDatasetDimensions(dsInfo);
                        /* This definitely needs to be more generic! */
                        String voxelSize = getDatasetVoxelSize(reader, info, "element_size_um");
                        dataSets.add(new DataSetInfo(info.getPath(), dimensions, type, voxelSize));
                        break;
                    case SOFT_LINK:
                        /*
                        TODO: At some point we probably would need to follow the link and retrieve relevant objects
                         */
                        break;
                    case GROUP:
                        populateDatasetInfo(reader, info, dataSets);
                        break;
                    default:
                        break;
                }
            }
        }

        private String getDataSetTypes(HDF5DataSetInformation dsInfo) {
            HDF5DataTypeInformation dsType = dsInfo.getTypeInformation();
            StringBuilder typeText = new StringBuilder("");

            if (!dsType.isSigned()) {
                typeText.append("U");
            }

            switch(dsType.getDataClass()) {
                case INTEGER:
                    typeText.append("int").append(8 * dsType.getElementSize());
                    break;
                case FLOAT:
                    switch(8 * dsType.getElementSize()) {
                        case 32:
                            typeText.append("float");
                            break;
                        case 64:
                            typeText.append("double");
                            break;
                    }
                    break;
                default:
                    typeText.append(dsInfo.toString());
            }
            return typeText.toString();
        }

        private String getDatasetDimensions(HDF5DataSetInformation dsInfo)
        {
            StringBuilder dimensions = new StringBuilder("");
            if( dsInfo.getRank() == 0) {
                dimensions.append("1");
            }
            else
            {
                int rank = dsInfo.getRank();
                dimensions.append(dsInfo.getDimensions()[rank - 1]);
                for( int i = rank - 2; i >= 0; --i) {
                    dimensions.append("x").append(dsInfo.getDimensions()[i]);
                }
            }
            return dimensions.toString();
        }

        private String getDatasetVoxelSize(IHDF5Reader reader, HDF5LinkInformation info, String attributeName)
        {
            StringBuilder voxelSize = new StringBuilder("");
            try {
                float[] voxelSizeArray = reader.float32().getArrayAttr(info.getPath(), attributeName);
                if (voxelSizeArray.length > 0) {
                    voxelSize.append(voxelSizeArray[voxelSizeArray.length - 1]);
                    for (int i = voxelSizeArray.length - 2; i >= 0; --i) {
                        voxelSize.append("x").append(voxelSizeArray[i]);
                    }
                }
            }
            catch (HDF5Exception err) {
                voxelSize = new StringBuilder("Unknown");
            }
            return voxelSize.toString();
        }
    }

    // The purpose of the Checker is to determine if an image source is
    // compatible with this Format.
    // If you just want to use basic extension checking to determine
    // compatibility, you do not need to implement any methods in this class -
    // it's already handled for you in the Abstract layer.
    //
    // However, if your format embeds an identifying flag - e.g. a magic string
    // or number - then it should override suffixSufficient, suffixNecessary
    // and isFormat(RandomAccessInputStream) as appropriate.
    public static class Checker extends AbstractChecker {

        // By default, this method returns true, indicating that extension match
        // alone is sufficient to determine compatibility. If this method returns
        // false, then the isFormat(RandomAccessInputStream) method will need to
        // be checked.
//			@Override
//			public boolean suffixSufficient() {
//				return false;
//			}

        // If suffixSufficient returns true, this method has no meaning. Otherwise
        // if this method returns true (the default) then the extension will have
        // to match in addition to the result of isFormat(RandomAccessInputStream)
        // If this returns false, then isFormat(RandomAccessInputStream) is solely
        // responsible for determining compatibility.
//			@Override
//			public boolean suffixNecessary() {
//				return false;
//			}

        // By default, this method returns false and is not considered during
        // extension checking. If your format uses a magic string, etc... then
        // you should override this method and check for the string or value as
        // appropriate.
//			@Override
//			public boolean isFormat(final RandomAccessInputStream stream)
//				throws IOException
//			{
//				return stream.readBoolean() == true;
//			}
    }

    // The Reader component uses parsed Metadata to determine how to extract
    // pixel data from an image source.
    // In the core SCIFIO library, image planes can be returned as byte[] or
    // BufferedImages, based on which Reader class is extended. Note that the
    // BufferedImageReader converts BufferedImages to byte[], so the
    // ByteArrayReader is typically faster and the default choice here. But
    // select the class that makes the most sense for your format.
    public static class Reader extends ByteArrayReader<Metadata> {

        Map<Integer, Buffer> imageBuffers = null;

        // The purpose of this method is to populate the provided Plane object by
        // reading from the specified image and plane indices in the underlying
        // image source.
        // planeMin and planeMax are dimensional indices determining the requested
        // subregion offsets into the specified plane.
        @Override
        public ByteArrayPlane openPlane(int imageIndex, long planeIndex,
                                        ByteArrayPlane plane, long[] planeMin, long[] planeMax,
                                        SCIFIOConfig config) throws FormatException, IOException
        {
            final Metadata meta = getMetadata();
            final ImageMetadata iMeta = meta.get(imageIndex);

            System.out.printf("%d %d %d %s %s\n", imageIndex, planeIndex, FormatTools.getPlaneSize(this, imageIndex),
                    Arrays.toString(planeMin), Arrays.toString(planeMax));

            plane.setData(readPlane(imageIndex, planeIndex, plane, planeMin, planeMax));

            return plane;
        }

        // HDF5 reader fetches the whole dataset as a native array. Since we will be serving planes from
        // and read the whole dataset anyway, let's cache the dataset native array for the image we are reading.
        private void prefetchPlanes(final int imageIndex)
        {
            final Metadata meta = getMetadata();
            final ImageMetadata iMeta = meta.get(imageIndex);
            final IHDF5Reader reader = meta.getReader();
            final String dataset = meta.getDatasets().get(imageIndex).getPath();

            if (imageBuffers == null) {
                imageBuffers = new HashMap<>();
            }

            Buffer buffer = null;

            switch (iMeta.getPixelType()) {
                case FormatTools.INT8:
                    buffer = ByteBuffer.wrap(reader.int8().readMDArray(dataset).getAsFlatArray());
                    break;
                case FormatTools.UINT8:
                    buffer = ByteBuffer.wrap(reader.uint8().readMDArray(dataset).getAsFlatArray());
                    break;
                case FormatTools.INT16:
                    buffer = ShortBuffer.wrap(reader.int16().readMDArray(dataset).getAsFlatArray());
                    break;
                case FormatTools.UINT16:
                    buffer = ShortBuffer.wrap(reader.uint16().readMDArray(dataset).getAsFlatArray());
                    break;
                case FormatTools.INT32:
                    buffer = IntBuffer.wrap(reader.int32().readMDArray(dataset).getAsFlatArray());
                    break;
                case FormatTools.UINT32:
                    buffer = IntBuffer.wrap(reader.uint32().readMDArray(dataset).getAsFlatArray());
                    break;
                case FormatTools.FLOAT:
                    buffer = FloatBuffer.wrap(reader.float32().readMDArray(dataset).getAsFlatArray());
                    break;
                case FormatTools.DOUBLE:
                    buffer = DoubleBuffer.wrap(reader.float64().readMDArray(dataset).getAsFlatArray());
                    break;
            }

            if (buffer != null) {
                imageBuffers.put(imageIndex, buffer);
            }
        }

        private byte[] readPlane(int imageIndex, long planeIndex,
                                 ByteArrayPlane plane, long[] planeMin, long[] planeMax)
        {
            final Metadata meta = getMetadata();
            final ImageMetadata iMeta = meta.get(imageIndex);

            if ((imageBuffers == null) || (imageBuffers.size() < imageIndex)) {
                prefetchPlanes(imageIndex);
            }

            Buffer rawBuffer = imageBuffers.get(imageIndex);

            if (rawBuffer == null) {
                prefetchPlanes(imageIndex);
                rawBuffer = imageBuffers.get(imageIndex);
            }

            ByteBuffer planeBuffer = ByteBuffer.wrap(plane.getData());

            int length = (int) (8 * iMeta.getPlaneSize() / iMeta.getBitsPerPixel());
            int offset = (int) (planeIndex * length);

            switch (iMeta.getPixelType()) {
                case FormatTools.INT8:
                case FormatTools.UINT8:
                    ByteBuffer rawByteBuffer = (ByteBuffer) rawBuffer;
                    planeBuffer.put(rawByteBuffer.array(), offset, length);
                    break;
                case FormatTools.INT16:
                case FormatTools.UINT16:
                    ShortBuffer rawShortBuffer = (ShortBuffer) rawBuffer;
                    ShortBuffer shortBuffer = planeBuffer.asShortBuffer();
                    shortBuffer.put(rawShortBuffer.array(), offset, length);
                    break;
                case FormatTools.INT32:
                case FormatTools.UINT32:
                    IntBuffer rawIntBuffer = (IntBuffer) rawBuffer;
                    IntBuffer intBuffer = planeBuffer.asIntBuffer();
                    intBuffer.put(rawIntBuffer.array(), offset, length);
                    break;
                case FormatTools.FLOAT:
                    FloatBuffer rawFloatBuffer = (FloatBuffer) rawBuffer;
                    FloatBuffer floatBuffer = planeBuffer.asFloatBuffer();
                    floatBuffer.put(rawFloatBuffer.array(), offset, length);
                    break;
                case FormatTools.DOUBLE:
                    DoubleBuffer rawDoubleBuffer = (DoubleBuffer) rawBuffer;
                    DoubleBuffer doubleBuffer = planeBuffer.asDoubleBuffer();
                    doubleBuffer.put(rawDoubleBuffer.array(), offset, length);
                    break;
            }

            return planeBuffer.array();
        }

        // You must declare what domains your reader is associated with, based
        // on the list of constants in io.scif.util.FormatTools.
        // It is also sufficient to return an empty array here.
        @Override
        protected String[] createDomainArray() {
            return new String[0];
        }

    }

    // *** OPTIONAL COMPONENTS ***

    // Writers are not implemented for proprietary formats, as doing so
    // typically violates licensing. However, if your format is open source you
    // are welcome to implement a writer.
    public static class Writer extends AbstractWriter<Metadata> {

        // NB: note that there is no writePlane method that uses a SCIFIOConfig.
        // The writer configuration comes into play in the setDest methods.
        // Note that all the default SCIFIOConfig#writer[XXXX] functionality is
        // handled in the Abstract layer.
        // But if there is configuration state for the writer you need to access,
        // you should override this setDest signature (as it is the lowest-level
        // signature, thus guaranteed to be called). Typically you will still want
        // a super.setDest call to ensure the standard boilerplate is handled
        // properly.
//			@Override
//			public void setDest(final RandomAccessOutputStream out, final int imageIndex,
//				final SCIFIOConfig config) throws FormatException, IOException
//			{
//				super.setDest(out, imageIndex, config);
//			}

        // Writers take a source plane and save it to their attached output stream
        // The image and plane indices are references to the final output dataset
        @Override
        public void writePlane(int imageIndex, long planeIndex, Plane plane,
                               long[] planeMin, long[] planeMax) throws FormatException, IOException
        {
            // This Metadata object describes how to write the data out to the
            // destination image.
            final Metadata meta = getMetadata();

            // This stream is the destination image to write to.
            final RandomAccessOutputStream stream = getStream();

            // The given Plane object is the source plane to write
            final byte[] bytes = plane.getBytes();

            System.out.println(bytes.length);
        }

        // If your writer supports a compression type, you can declare that here.
        // Otherwise it is sufficient to return an empty String[]
        @Override
        protected String[] makeCompressionTypes() {
            return new String[0];
        }
    }

    // The purpose of a Translator is similar to that of a Parser: to populate
    // the format-specific metadata of a Metadata object.
    // However, while a Parser reads from an image source to perform this
    // operation, a Translator reads from a Metadata object of another format.
    //
    // There are two main reasons when you would want to implement a Translator:
    // 1) If you implement a Writer, you should also implement a Translator to
    // describe how io.scif.Metadata should be translated to your Format-
    // specific metadata. This translator will then be called whenever
    // SCIFIO writes out your format, and it will be able to handle any
    // input format type. Essentially this is translating ImageMetadata to
    // your format-specific metadata.
    // 2) If you are adding support for a new Metadata schema to SCIFIO, you
    // will probably want to create Translators to and from your new Metadata
    // schema and core SCIFIO Metadata classes. The purpose of these
    // Translators is to more accurately or richly capture metadata
    // information, without the lossy ImageMetadata intermediate that would
    // be used by default translators.
    // This is a more advanced use case but mentioned for completeness. See
    // https://github.com/scifio/scifio-ome-xml/tree/dec59b4f37461a248cc57b1d38f4ebe2eaa3593e/src/main/java/io/scif/ome/translators
    // for examples of this case.
    public static class Translator extends
            AbstractTranslator<io.scif.Metadata, Metadata>
    {

        // The source and dest methods are used for finding matching Translators
        // They require only trivial implementations.

        @Override
        public Class<? extends io.scif.Metadata> source() {
            return io.scif.Metadata.class;
        }

        @Override
        public Class<? extends io.scif.Metadata> dest() {
            return Metadata.class;
        }

        // ** TRANSLATION METHODS **
        // There are three translation method hooks you can use. It is critical
        // to understand that the source.getAll() method may return a DIFFERENT
        // list of ImageMetadata than what is passed to these methods.
        // This is because the source's ImageMetadata may still be the direct
        // translation of its format-specific Metadata, but the provided
        // ImageMetadata may be the result of modification - cropping, zooming,
        // etc...
        // So, DO NOT CALL:
        // - Metadata#get(int)
        // - Metadata#getAll()
        // in these methods unless you have a good reason to do so. Use the
        // ImageMetadata provided.
        //
        // There are three hooks you can use in translation:
        // 1) typedTranslate gives you access to the concrete source and
        // destination metadata objects, along with the ImageMeatadata.
        // 2) translateFormatMetadata when you want to use format-specific
        // metadata from the source (only really applicable in reason #2 above
        // for creating a Translator)
        // 3) translateImageMetadata when you want to use the source's
        // ImageMetadata (which is always the case when making a translator
        // with a general io.scif.Metadata source)

        // Not used in the general case
//			@Override
//			protected void typedTranslate(final io.scif.Metadata source,
//				final List<ImageMetadata> imageMetadata, final Metadata dest)
//			{
//				super.typedTranslate(source, imageMetadata, dest);
//			}

        // Not used in the general case
//			@Override
//			protected void translateFormatMetadata(final io.scif.Metadata source,
//				final Metadata dest)
//			{
//			}

        // Here we use the state in the ImageMetadata to populate format-specific
        // metadata
        @Override
        protected void translateImageMetadata(final List<ImageMetadata> source,
                                              final Metadata dest)
        {

        }
    }
}
// *** END OF SAMPLE FORMAT ***
