//TODO: możliwość przywrócenia widoczności ukrytego okna z wynikami z poziomu okna sterowania filtra
//TODO: jakieś opisy nad wyświetlanymi obrazami, co jest co
//TODO:
//TODO:
//TODO:
//TODO:
//TODO:
//TODO:
//TODO:
//TODO:


package ch.unizh.ini.jaer.projects.orzeszek;

/**
 *
 * @author Piotr
 */

import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.util.gl2.GLUT;
import javax.swing.JFileChooser;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;
import java.lang.Math;          //sqrt(), round(), toDegrees()
import javax.swing.JOptionPane; // dialog message
import javax.swing.JFrame;      // frame for dialog message
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Size;
import org.opencv.core.Scalar;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import java.util.List;
import java.util.ArrayList;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import javax.swing.JLabel;
import javax.swing.ImageIcon;
import java.util.Random;

@Description("Finds acorn in incoming events.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)

public class Acorn extends EventFilter2D implements FrameAnnotater {
    
    
    private boolean isAcorn = false;
    private int[][] eventMap;
    private int[][] processedMap;
    private int eventSum;
    private int subXMin, subXMax, subYMin, subYMax;
    private int xCenter, yCenter;
    private long xCenterSum, yCenterSum;
    private int[][] sobel;
    private final int[][] sobelMaskH = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
    private final int[][] sobelMaskV = {{1, 2, 1}, {0, 0, 0}, {-1, -2, -1}};
    private final int[][] gaussMask = {{1, 1, 1}, {1, 1, 1}, {1, 1, 1}};
    private JLabel labelEdges;
    private JLabel labelContour;
    private JLabel labelEllipse;
    private JFrame frame;

    // ================================= GUI PARAMS =================================
    private boolean EnableContour = getPrefs().getBoolean("Acorn.Contour", true);
    private boolean WygaszanieEventow = getPrefs().getBoolean("Acorn.WygaszanieEventow", true);
    private boolean EnableSquare = getPrefs().getBoolean("Acorn.EnableSquare", true);
    private boolean EnableCenter = getPrefs().getBoolean("Acorn.EnableCenter", true);
    private boolean EnableNeighborsFiltering = getPrefs().getBoolean("Acorn.EnableNeighborsFiltering", true);
    private int NeighborsThreshold = getPrefs().getInt("Acorn.NeighborsThreshold", 0);
    private boolean EnableAcornMarking = getPrefs().getBoolean("Acorn.EnableAcornMarking", true);
    private boolean AGHLogo = getPrefs().getBoolean("Acorn.AGHLogo", true);
    private boolean IgnoreMultipleNeighbors = getPrefs().getBoolean("Acorn.IgnoreMultipleNeighbors", true);
    private boolean EnableCanny = getPrefs().getBoolean("Acorn.EnableCanny", true);
    private float CannyT1 = getPrefs().getFloat("Acorn.CannyT1", 0);
    private float CannyT2 = getPrefs().getFloat("Acorn.CannyT2", 0);
    
    public boolean getEnableContour() {
        return this.EnableContour;
    }
    public void setEnableContour(boolean enable) {
        if (EnableCanny) {
            this.EnableContour = enable;
            getPrefs().putBoolean("Acorn.EnableContour", enable);
        }
        else if (enable)
            JOptionPane.showMessageDialog(new JFrame("Error"), "Potrzebne Canny!");
    }
    public boolean getEnableCanny() {
        return this.EnableCanny;
    }
    public void setEnableCanny(boolean enable) {
        this.EnableCanny = enable;
        getPrefs().putBoolean("Acorn.EnableCanny", enable);
        if (!enable)
            setEnableContour(enable);
    }
    public boolean getWygaszanieEventow() {
        return this.WygaszanieEventow;
    }
    public void setWygaszanieEventow(boolean enable) {
        this.WygaszanieEventow = enable;
        getPrefs().putBoolean("Acorn.WygaszanieEventow", enable);
    }
    public float getCannyT1() {
        return this.CannyT1;
    }
    public void setCannyT1(float val) {
        if (val < this.CannyT2)
        {
            this.CannyT1 = val;
            getPrefs().putFloat("Acorn.CannyT1", val);
        }
        else
        {
            JOptionPane.showMessageDialog(new JFrame("Error"), "CannyT1 threshold must be smaller than CannyT2!");
            getPrefs().putFloat("Acorn.CannyT1", this.CannyT1);
        }
    }
    public float getCannyT2() {
        return this.CannyT2;
    }
    public void setCannyT2(float val) {
        if (val > this.CannyT1)
        {
            this.CannyT2 = val;
            getPrefs().putFloat("Acorn.CannyT2", val);
        }
        else
        {
            getPrefs().putFloat("Acorn.CannyT2", this.CannyT2);
        }
    }
    public boolean getEnableSquare() {
        return this.EnableSquare;
    }
    public void setEnableSquare(boolean enable) {
        this.EnableSquare = enable;
        getPrefs().putBoolean("Acorn.EnableSquare", enable);
    }
    public boolean getMarkCenter() {
        return this.EnableCenter;
    }
    public void setMarkCenter(boolean mark) {
        this.EnableCenter = mark;
        getPrefs().putBoolean("Acorn.EnableCenter", mark);
    }
    public boolean getIgnoreMultipleNeighbors() {
        return this.IgnoreMultipleNeighbors;
    }
    public void setIgnoreMultipleNeighbors(boolean ignore) {
        this.IgnoreMultipleNeighbors = ignore;
        getPrefs().putBoolean("Acorn.IgnoreMultipleNeighbors", ignore);
    }
    public boolean getNeighborsFilteringEnabled() {
        return this.EnableNeighborsFiltering;
    }
    public void setNeighborsFilteringEnabled(boolean neighbors) {
        this.EnableNeighborsFiltering = neighbors;
        getPrefs().putBoolean("Acorn.EnableNeighborsFiltering", neighbors);
    }
    public int getNeighborsThreshold() {
        return this.NeighborsThreshold;
    }
    public void setNeighborsThreshold(int th) {
        this.NeighborsThreshold = th;
        getPrefs().putInt("Acorn.NeighborsThreshold", th);
    }
    public boolean getAGHLogoEnabled() {
        return this.AGHLogo;
    }
    public void setAGHLogoEnabled(boolean logoEnabled) {
        this.AGHLogo = logoEnabled;
        getPrefs().putBoolean("Acorn.AGHLogo", logoEnabled);
    }
    public boolean getEnableAcornMarking() {
        return this.EnableAcornMarking;
    }
    public void setEnableAcornMarking(boolean markingEnabled) {
        this.EnableAcornMarking = markingEnabled;
        getPrefs().putBoolean("Info.AGHLogo", markingEnabled);
    }
    // =============================== END GUI PARAMS =================================
    
    // method which allocates memory for arrays
    private void initMaps() {
        if (eventMap == null)
            eventMap = new int[getChip().getSizeX()][getChip().getSizeX()];
        if (processedMap == null)
            processedMap = new int[chip.getSizeX()][chip.getSizeY()];
    }
    
    // method which fills maps with zeros
    private void resetMaps() {
        initMaps();
        for (int x = 0; x < getChip().getSizeX(); x++)
            for (int y = 0; y < getChip().getSizeY(); y++)
            {
                eventMap[x][y] = 0;
                processedMap[x][y] = 0;
            }
        eventSum = 0;
    }
    
    public Acorn(AEChip chip) {
        super(chip);
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        initFilter();
        resetFilter();
        
        // gropu's labels
        final String nf = "Neighbors filtering", ann = "Displaying annotations", can = "Canny - edge detection";

        // tooltips
        setPropertyTooltip("WygaszanieEventow", "Tworząc mapę eventów uwzględnia czas wystąpienia eventu - im starszy tym ma mniejszą wartość");
        setPropertyTooltip(ann, "EnableAcornMarking", "Set true to mark on display if acorn is found");
        setPropertyTooltip(ann, "AGHLogoEnabled", "Shows information about authors");
        setPropertyTooltip(ann, "MarkCenter", "Prints dot in the gravity center of events");
        setPropertyTooltip(ann, "EnableSquare", "Prints square surrounding 90% of events in frame");
        setPropertyTooltip(nf, "NeighborsThreshold", "Sets the threshold for noise filtering");
        setPropertyTooltip(nf, "NeighborsFilteringEnabled", "Enables removing events which do not have enough neighbors");
        setPropertyTooltip(nf, "IgnoreMultipleNeighbors", "Ignore multiple events under the same address");
        setPropertyTooltip(can, "EnableCanny", "Enables Cany edge detection algorithm");
        setPropertyTooltip(can, "CannyT1", "Canny filter - threshold T1");
        setPropertyTooltip(can, "CannyT2", "Canny filter - threshold T2");
        setPropertyTooltip("EnableContour", "Zaznacza kontur wykrytego obiektu");
        
        // initial values
        setWygaszanieEventow(true);
        setAGHLogoEnabled(true);
        setEnableAcornMarking(true);
        setMarkCenter(true);
        setEnableSquare(true);
        setNeighborsThreshold(5);
        setNeighborsFilteringEnabled(true);
        setIgnoreMultipleNeighbors(true);
        setEnableCanny(true);
        setCannyT2((float) 2.0);
        setCannyT1((float) 0.3);
        setEnableContour(true);
        
        // nowe okno do wyświetlania wyników poszczególnych etapów
        frame = new JFrame();
        frame.setLayout(new FlowLayout());
        frame.setSize(2*128+50, 2*128+50); //chip.getSizeX() nie działa (dlaczego?)
        labelEdges = new JLabel();
        labelContour = new JLabel();
        labelEllipse = new JLabel();
        frame.add(labelEdges);
        frame.add(labelContour);
        frame.add(labelEllipse);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    }
    
    @Override
    //main filtering method
    public EventPacket<?> filterPacket(EventPacket<?> in) { 
        //avoid always running filter
        if(!filterEnabled) 
            return in;
        
        //making sure we have a valid output packet
        checkOutputPacketEventType(in); 
        
        // resets Maps
        resetMaps();
        
        // initialize and obtain the output event iterator
        OutputEventIterator outItr = out.outputIterator(); 
        int sx=chip.getSizeX()-1;
        int sy=chip.getSizeY()-1;
        int ts=0;
        
        // min and max timestamp
        int minTS = 2^31-1;
        int maxTS = 0;
        for(Object e:in) {
            BasicEvent i = (BasicEvent)e; 
            if (i.timestamp < minTS)
                minTS = i.timestamp;
            else if (i.timestamp > maxTS)
                maxTS = i.timestamp;
        }
        
        // iterate over the inpu packet, cast the object to basic
        // event to get timestamp, x and y
        float[][] initialMap = new float[chip.getSizeX()][chip.getSizeY()];
        for(Object e:in) { 
            BasicEvent i = (BasicEvent)e; 
            short x = (short)(i.x);
            short y = (short)(i.y);
            
            // ignore special events, e.g. with negative address
            if(x > sx || y > sy || x < 0 || y < 0)
                continue;
            
            eventSum++;
            if(!this.WygaszanieEventow)
                initialMap[x][y] += 1;
            else
                initialMap[x][y] += 1.0*(i.timestamp - minTS)/(maxTS - minTS);
        }

        // find max and min
        float min = 9999999;
        float max = 0;
        for (int x = 0; x < chip.getSizeX(); x++)
            for (int y = 0; y < chip.getSizeY(); y++){
                if (initialMap[x][y] < min)
                    min = initialMap[x][y];
                else if (initialMap[x][y] > max)
                    max = initialMap[x][y];
            }
        
        // normalize map
        for (int x = 0; x < chip.getSizeX(); x++)
            for (int y = 0; y < chip.getSizeY(); y++){
                eventMap[x][y] = (int) (initialMap[x][y]-min)*255/((int) max + 1); // prevents dividing by zero
            }
                    
        // filter map.
        for (int x = 0; x < chip.getSizeX(); x++)
            for (int y = 0; y < chip.getSizeY(); y++)
                if(EnableNeighborsFiltering)
                {
                    if (sumNeighbors((short) x, (short) y) >= this.NeighborsThreshold)
                        this.processedMap[x][y] = this.eventMap[x][y];
                }
                else
                    this.processedMap[x][y] = this.eventMap[x][y];
        
        // wyznacz środek ciężkości jeżeli nie będzie wyznaczany później
        if (!EnableCanny) {
            countSubRegion();
            countCenter();
        }

        // copy to openCV Mat type
        Mat mapCV = new Mat(chip.getSizeX(), chip.getSizeY(), org.opencv.core.CvType.CV_8U);
        for (int x = 0; x < chip.getSizeX(); x++)
            for (int y = 0; y < chip.getSizeY(); y++)
                mapCV.put(x, y, processedMap[x][y]);
 
        if (EnableCanny) {
            Mat konturCV = new Mat(chip.getSizeX(), chip.getSizeY(), org.opencv.core.CvType.CV_32F);
            Mat edgesCV = new Mat(chip.getSizeX(), chip.getSizeY(), org.opencv.core.CvType.CV_32F);
            // wykrywanie krawędzi
            Imgproc.Canny(mapCV, edgesCV, CannyT1, CannyT2);

            // kontur
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            // constans from sj.opencv.Constants (nie zainstalowane)
            int CV_CHAIN_APPROX_TC89_L1 = 3;
            int CV_CHAIN_APPROX_NONE = 0;
            int CV_RETR_EXTERNAL = 0;
            Imgproc.findContours(edgesCV, contours, hierarchy, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_TC89_L1);
            Mat kontury = Mat.zeros(edgesCV.size(), CvType.CV_8UC3);
            Mat comboContourMat = Mat.zeros(edgesCV.size(), CvType.CV_8UC3);
            Mat elipsa = Mat.zeros(edgesCV.size(), CvType.CV_8UC3);
            Random rng = new Random();
            List<Point> pointList = new ArrayList<>();
            for (int i = 0; i < contours.size(); i++){
                Scalar color = new Scalar(rng.nextInt()%255, rng.nextInt()%255, rng.nextInt()%255);
                Imgproc.drawContours(kontury, contours, i, color);
                pointList.addAll(contours.get(i).toList());
            }
            
            // kontur zawierający wszystkie znalezione kontury
            List<MatOfPoint> comboContourList = new ArrayList<>();
            MatOfPoint comboContour = new MatOfPoint();
            comboContour.fromList(pointList);
            comboContourList.add(comboContour);
            
            MatOfPoint2f comboContour2f = new MatOfPoint2f();
            comboContour2f.fromList(pointList);
            
            RotatedRect rect = new RotatedRect();
            if (!pointList.isEmpty())
                rect = Imgproc.fitEllipse(comboContour2f);
            
            //Imgproc.drawContours(elipsa, comboContourList, 0, new Scalar(255, 255, 255));
            Imgproc.ellipse(elipsa, rect, new Scalar(255, 30, 30));
            
            
            // wyswietlenie wyniku
            ImageIcon iconEdges = new ImageIcon(Mat2BufferedImage(edgesCV));
            labelEdges.setIcon(iconEdges);
            ImageIcon iconContour = new ImageIcon(Mat2BufferedImage(kontury));
            labelContour.setIcon(iconContour);
            ImageIcon iconEllipse = new ImageIcon(Mat2BufferedImage(elipsa));
            labelEllipse.setIcon(iconEllipse);

            
            countSubRegion();
            countCenter();
        }
    
        //second cycle for copying proper events
        for(Object e:in) { 
            BasicEvent i = (BasicEvent)e; 
            short x = (short)(i.x);
            short y = (short)(i.y);

            //ignore special events, e.g. with negative address
            if(x > sx || y > sy || x < 0 || y < 0)
                continue;

            if(EnableNeighborsFiltering) {
                if(sumNeighbors(x, y) >= NeighborsThreshold)
                    outItr.nextOutput().copyFrom(i);
            }
            else
                outItr.nextOutput().copyFrom(i);
        }
        
        return out;
    }
    
    @Override
    //function for putting annotations on the rendered output image
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        checkBlend(gl);
        int numX = chip.getSizeX(), numY = chip.getSizeY();
        gl.glPushMatrix();
        for (int y = 0; y < numY; y++) {
            for (int x = 0; x < numX; x++) {
                if (EnableAcornMarking) {
                    if(x == 0 || y == 0 || x == numX-1 || y == numY-1) {
                        if (isAcorn) {
                            gl.glColor4f(0, 1f, 0, 1f);
                            gl.glRectf(x, y, x+1f, y+1f);
                        }
                        else {
                            gl.glColor4f(1f, 0, 0, 1f);
                            gl.glRectf(x, y, x+1f, y+1f);
                        }
                    }
                }
                if(EnableSquare) {
                    if((x == subXMin || x == subXMax) && y >= subYMin && y <= subYMax
                    || (y == subYMin  || y == subYMax) && x >= subXMin && x <= subXMax) {
                        gl.glColor4f(0, 0, 1f, 1f);
                        gl.glRectf(x, y, x+1f, y+1f);
                    }
                }
                if(EnableCenter) {
                    if (x == xCenter && y == yCenter) {
                        gl.glColor4f(1f, 0, 0, 1f);
                        gl.glRectf(x, y, x+1f, y+1f);
                    }
                }
            }
        }
        gl.glPopMatrix();

        //draws AGH logo if it is enabled
        drawLogo(drawable);
    }
    
    @Override
    synchronized public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }
    
    private AEViewer getViewer() {
        return getChip().getAeViewer();
    }

    private AEChipRenderer getRenderer() {
        return getChip().getRenderer();
    }
    
    //method draws AGH logo
    private void drawLogo(GLAutoDrawable drawable) {
        if (!getAGHLogoEnabled()) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        // positioning of rate bars depends on num types and display size
        ChipCanvas.Borders borders = chip.getCanvas().getBorders();

        // get screen width in screen pixels, subtract borders in screen pixels to find width of drawn chip area in screen pixels
        float /*h = drawable.getHeight(), */ w = drawable.getSurfaceWidth() - (2 * borders.leftRight * chip.getCanvas().getScale());
        final int sx = chip.getSizeX(), sy = chip.getSizeY();
        final float ypos = .05f * sy;

        gl.glPushMatrix();
        gl.glColor4f(0.2f, 0.2f, 0.8f, 1f);
        int font = GLUT.BITMAP_9_BY_15;
        GLUT glut = chip.getCanvas().getGlut();
        String s = "AGH - EAIiIB - AiR - 2016";
        // get the string length in screen pixels , divide by chip array in screen pixels,
        // and multiply by number of pixels to get string length in screen pixels.
        float sw = (glut.glutBitmapLength(font, s) / w) * sx;
        float xpos = .95f * sx - sw;
        gl.glRasterPos3f(xpos, ypos, 0);
        glut.glutBitmapString(font, s);
        gl.glPopMatrix();
    }
    
    //foreach cell counts it's neighbors
    private int sumNeighbors(short x, short y) {
        int sum = 0;
        for (int i = x-1; i <= x+1; i++)
            for (int j = y-1; j <= y+1; j++)
                if(i > -1 && i < getChip().getSizeX() && j > -1 && j < getChip().getSizeY())
                    if(IgnoreMultipleNeighbors)
                        sum += eventMap[i][j] > 0 ? 1 : 0;
                    else
                        sum += eventMap[i][j];
        return sum;
    }
    
    // wyznacza prostokąt otaczający
    private void countSubRegion() {
        subXMin = subYMin = 0;
        subXMax = chip.getSizeX();
        subYMax = chip.getSizeY();
        int sum = 0;
        boolean subXMinSet = false, subXMaxSet = false, subYMinSet = false, subYMaxSet = false;
        //x limits
        for (int x = 0; x < chip.getSizeX(); x++)
            for (int y = 0; y < chip.getSizeY(); y++)
            {
                if (processedMap[x][y] > 0 && !subXMinSet)
                {
                    subXMin = x;
                    subXMinSet = true;
                }
                else if (processedMap[x][y] > 0) {
                    subXMax = x;
                }
            }
        sum = 0;
        //ylimits
        for (int y = 0; y < chip.getSizeY(); y++)
            for (int x = 0; x < chip.getSizeX(); x++)
            {
                if (processedMap[x][y] > 0 && !subYMinSet)
                {
                    subYMin = y;
                    subYMinSet = true;
                }
                else if (processedMap[x][y] > 0) {
                    subYMax = y;
                }
            }
    }
    
    // wyznacza środek ciężkości z tablicy processedMap
    private void countCenter() {
        xCenterSum = yCenterSum = 0;
        xCenter = yCenter = 0;
        long weight = 0;
        for (int x = 0; x < chip.getSizeX(); x++)
            for (int y = 0; y < chip.getSizeY(); y++)
            {
                xCenterSum += x*processedMap[x][y];
                yCenterSum += y*processedMap[x][y];
                weight += processedMap[x][y];
            }
        if(weight != 0) {
            xCenter = (int) (xCenterSum / weight);
            yCenter = (int) (yCenterSum / weight);
        }
    }
        
    private BufferedImage Mat2BufferedImage(Mat m){
    // source: http://answers.opencv.org/question/10344/opencv-java-load-image-to-gui/
    // Fastest code
    // The output can be assigned either to a BufferedImage or to an Image

        int type = BufferedImage.TYPE_BYTE_GRAY;
        if ( m.channels() > 1 ) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = m.channels()*m.cols()*m.rows();
        byte [] b = new byte[bufferSize];
        m.get(0,0,b); // get all the pixels
        BufferedImage image = new BufferedImage(chip.getSizeX(), chip.getSizeY(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);  
        return image;

    }
}
