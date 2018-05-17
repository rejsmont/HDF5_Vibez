package sc.fiji.hdf5;

import java.util.Comparator;

public class DataSetInfo
{
    static class DataSetInfoComparator implements Comparator<DataSetInfo> {
        public int compare(DataSetInfo a, DataSetInfo b) {
            return a.numericPath.compareTo(b.numericPath);
        }
    }

    private String path;
    private String numericPath;
    private String dimensions;
    private String type;
    private String voxelSize;
    private String axisOrder;
    private final int numPaddingSize = 10;

    public DataSetInfo( String p, String d, String t, String e) {
        this(p, d, t, e, "zyxct");
    }

    public DataSetInfo(String p, String d, String t, String e, String o) {
        setPath(p);
        dimensions = d;
        type = t;
        voxelSize = e;
        axisOrder = o;
    }

    public String getPath() {
        return path;
    }

    public String getDimensions() {
        return dimensions;
    }

    public String getType() {
        return type;
    }

    public String getVoxelSize() {
        return voxelSize;
    }

    public String getAxisOrder() {
        return axisOrder;
    }

    private void setPath(String p) {
        path = p;
        StringBuilder numPath = new StringBuilder("");
        StringBuilder num = new StringBuilder("");

        for(int i = 0; i < p.length(); ++i) {
            if (isNum(p.charAt(i))) {
                num.append(p.charAt(i));
            } else {
                if (! num.toString().equals("")) {
                    for (int j = 0; j < numPaddingSize - num.length(); ++j) {
                        numPath.append("0");
                    }
                    numPath.append(num);
                    num = new StringBuilder("");
                }
                numPath.append(p.charAt(i));
            }
        }

        if (! num.toString().equals("")) {
            for (int j = 0; j < numPaddingSize - num.length(); ++j) {
                numPath.append("0");
            }
            numPath.append(num);
        }

        numericPath = numPath.toString();
    }

    public static Comparator<DataSetInfo> createComparator()
    {
        return new DataSetInfoComparator();
    }

    private boolean isNum(char c)
    {
        return c >= '0' && c <= '9';
    }
}
