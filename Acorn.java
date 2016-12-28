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
import net.sf.jaer.event.PolarityEvent;
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
    private int[][] filteredMap;
    private int subXMin, subXMax, subYMin, subYMax;
    private int xCenter, yCenter;
    private long xCenterSum, yCenterSum;
    private JLabel labelGray;
    private JLabel labelEllipse;
    private JFrame frame;
    private Mat mapCV;
    private Mat elipsa;
    private Mat convexHull;
    private final int scallingRatio = 2;
    // ================================= GUI PARAMS =================================
    private boolean EventsToning = getPrefs().getBoolean("Acorn.EventsToning", true);
    private boolean EnableSquare = getPrefs().getBoolean("Acorn.EnableSquare", true);
    private boolean EnableCenter = getPrefs().getBoolean("Acorn.EnableCenter", true);
    private boolean EnableNeighborsFiltering = getPrefs().getBoolean("Acorn.EnableNeighborsFiltering", true);
    private int NeighborsThreshold = getPrefs().getInt("Acorn.NeighborsThreshold", 0);
    private boolean EnableAcornMarking = getPrefs().getBoolean("Acorn.EnableAcornMarking", true);
    private boolean AGHLogo = getPrefs().getBoolean("Acorn.AGHLogo", true);
    private boolean IgnoreMultipleNeighbors = getPrefs().getBoolean("Acorn.IgnoreMultipleNeighbors", true);
    private boolean OnlyOnEvents = getPrefs().getBoolean("Acorn.OnlyOnEvents", false);
    private boolean OnlyOffEvents = getPrefs().getBoolean("Acorn.OnlyOffEvents", false);
    
    public boolean getOnlyOnEvents() {
        return this.OnlyOnEvents;
    }
    public void setOnlyOnEvents(boolean enable) {
        this.OnlyOnEvents = enable;
        getPrefs().putBoolean("Acorn.OnlyOnEvents", enable);
        if (enable && this.OnlyOffEvents)
            this.setOnlyOffEvents(false);
    }
    public boolean getOnlyOffEvents() {
        return this.OnlyOffEvents;
    }
    public void setOnlyOffEvents(boolean enable) {
        this.OnlyOffEvents = enable;
        getPrefs().putBoolean("Acorn.OnlyOffEvents", enable);
        if (enable && this.OnlyOnEvents)
            this.setOnlyOnEvents(false);
    }
    public boolean getEventsToning() {
        return this.EventsToning;
    }
    public void setEventsToning(boolean enable) {
        this.EventsToning = enable;
        getPrefs().putBoolean("Acorn.EventsToning", enable);
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
    public void doShowSteps() {
        frame.setVisible(!frame.isVisible());
    }
    // =============================== END GUI PARAMS =================================
    
    // method which allocates memory for arrays
    private void initMaps() {
        if (eventMap == null)
            eventMap = new int[getChip().getSizeX()][getChip().getSizeX()];
        if (filteredMap == null)
            filteredMap = new int[chip.getSizeX()][chip.getSizeY()];
    }
    
    // method which fills maps with zeros
    private void resetMaps() {
        initMaps();
        for (int x = 0; x < getChip().getSizeX(); x++)
            for (int y = 0; y < getChip().getSizeY(); y++)
            {
                eventMap[x][y] = 0;
                filteredMap[x][y] = 0;
            }
    }
    
    public Acorn(AEChip chip) {
        super(chip);
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        initFilter();
        resetFilter();
        
        // gropu's labels
        final String nf = "Filtering", ann = "Displaying annotations", can = "Canny - edge detection", pol = "Events polarity";

        // tooltips
        setPropertyTooltip("EventsToning", "Consider event timestamp when creating event map - the older event the lower value");
        setPropertyTooltip(ann, "EnableAcornMarking", "Set true to mark on display if acorn is found");
        setPropertyTooltip(ann, "AGHLogoEnabled", "Shows information about authors");
        setPropertyTooltip(ann, "MarkCenter", "Prints dot in the gravity center of events");
        setPropertyTooltip(ann, "EnableSquare", "Prints square surrounding 90% of events in frame");
        setPropertyTooltip(nf, "NeighborsThreshold", "Sets the threshold for noise filtering");
        setPropertyTooltip(nf, "NeighborsFilteringEnabled", "Enables removing events which do not have enough neighbors");
        setPropertyTooltip(nf, "IgnoreMultipleNeighbors", "Ignore multiple events under the same address");
        setPropertyTooltip("ShowSteps", "Shows additional window with visualization of succeeding steps");
        setPropertyTooltip(pol, "OnlyOnEvents", "Ignore off events");
        setPropertyTooltip(pol, "OnlyOffEvents", "Ignore on events");
        
        // initial values
        setEventsToning(true);
        setAGHLogoEnabled(true);
        setEnableAcornMarking(true);
        setMarkCenter(true);
        setEnableSquare(true);
        setNeighborsThreshold(5);
        setNeighborsFilteringEnabled(true);
        setIgnoreMultipleNeighbors(true);
        setOnlyOnEvents(false);
        setOnlyOffEvents(false);
        
        // nowe okno do wyświetlania wyników poszczególnych etapów
        frame = new JFrame();
        frame.setLayout(new FlowLayout());
        frame.setSize(scallingRatio*2*128+50, scallingRatio*2*128+50); //chip.getSizeX() nie działa (dlaczego?)
        labelGray = new JLabel();
        labelEllipse = new JLabel();
        frame.add(labelGray);
        frame.add(labelEllipse);
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
            //BasicEvent i = (BasicEvent)e; 
            PolarityEvent i = (PolarityEvent)e;
            if (i.type == 1)
                continue;
            if (i.timestamp < minTS)
                minTS = i.timestamp;
            else if (i.timestamp > maxTS)
                maxTS = i.timestamp;
        }
        
        // iterate over the inpu packet, cast the object to basic
        // event to get timestamp, x and y
        float[][] initialMap = new float[chip.getSizeX()][chip.getSizeY()];
        for(Object e:in) { 
            PolarityEvent i = (PolarityEvent) e;
            if (i.polarity == PolarityEvent.Polarity.On && OnlyOffEvents)
                continue;
            if (i.polarity == PolarityEvent.Polarity.Off && OnlyOnEvents)
                continue;
            
            short x = (short)(i.x);
            short y = (short)(i.y);
            
            // ignore special events, e.g. with negative address
            if(x > sx || y > sy || x < 0 || y < 0)
                continue;
            
            if(!this.EventsToning)
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
                        this.filteredMap[x][y] = this.eventMap[x][y];
                }
                else
                    this.filteredMap[x][y] = this.eventMap[x][y];
        
        countSubRegion();
        countCenter();
        
        // copy to openCV Mat type
        mapCV = new Mat(chip.getSizeX(), chip.getSizeY(), org.opencv.core.CvType.CV_8UC3);
        for (int x = 0; x < chip.getSizeX(); x++)
            for (int y = 0; y < chip.getSizeY(); y++)
            {
                int val = filteredMap[x][chip.getSizeY() - 1 - y];
                mapCV.put(y, x, val, val, val);
            }
 
        // convert to list
        List<Point> pointList = new ArrayList<>();
        for (int i = 0; i < chip.getSizeX(); i++)
            for (int j = 0; j < chip.getSizeY(); j++)
                if ( mapCV.get(i, j)[0] > 0)
                {
                    Point p = new Point(i, j);
                    pointList.add(p);
                }
        
        // convex Hull
        // allocate memory, for future use
        MatOfPoint2f matHull = new MatOfPoint2f();
        MatOfPoint2f polyHull2f = new MatOfPoint2f();
        List<MatOfPoint> polyHullList = new ArrayList<>();
        List<Point> hullList = new ArrayList<>();
        MatOfInt hull = new MatOfInt();
        convexHull = new Mat(chip.getSizeX(), chip.getSizeY(), org.opencv.core.CvType.CV_8U);
        // create MatOfPoint
        MatOfPoint mapCVP = new MatOfPoint();
        mapCVP.fromList(pointList);
        // run convexHull algorithm and convert to array
        /*Imgproc.convexHull(mapCVP, hull);
        int[] hullArray = hull.toArray();
        // create list with proper points (hull gives indexes of input list)
        for (int i = 0; i < hullArray.length; i++)
            hullList.add(pointList.get(hullArray[i]));
        // create mat from list and approximate polygonal curve
        matHull.fromList(hullList);
        Imgproc.approxPolyDP(matHull, polyHull2f, 1, true);
        Point[] hullPoints = polyHull2f.toArray();
        MatOfPoint points = new MatOfPoint();
        points.fromArray(hullPoints);
        polyHullList.add(points);
        Scalar color = new Scalar(240, 40, 40);
        Imgproc.polylines(mapCV, polyHullList, true, color);
        //for (int i = 0; i < hullPoints.length; i++)
          //  mapCV.put(hullPoints[i].x, hullPoints[i].y, color);
        */
        
        // ellipse
        elipsa = Mat.zeros(mapCV.size(), CvType.CV_8UC3);       
        MatOfPoint2f mapCV2f = new MatOfPoint2f();
        mapCV2f.fromList(pointList);
        // pointList = mapCV.
        RotatedRect rect = new RotatedRect();
        if (pointList.size() >= 5)
            rect = Imgproc.fitEllipse(mapCV2f);
        
        // Imgproc.drawContours(elipsa, comboContourList, 0, new Scalar(255, 255, 255));
        Imgproc.ellipse(elipsa, rect, new Scalar(255, 30, 30));

        // wyswietl poszczegolne kroki w osobnym okienku
        if(frame.isVisible())
            Visualize();
        
        // second cycle for copying proper events to output packet
        for(Object e:in) { 
            //BasicEvent i = (BasicEvent)e;
            PolarityEvent i = (PolarityEvent) e;
            if (i.polarity == PolarityEvent.Polarity.On && OnlyOffEvents)
                continue;
            if (i.polarity == PolarityEvent.Polarity.Off && OnlyOnEvents)
                continue;
            
            short x = (short)(i.x);
            short y = (short)(i.y);

            // ignore special events, e.g. with negative address
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
                if (filteredMap[x][y] > 0 && !subXMinSet)
                {
                    subXMin = x;
                    subXMinSet = true;
                }
                else if (filteredMap[x][y] > 0) {
                    subXMax = x;
                }
            }
        sum = 0;
        //ylimits
        for (int y = 0; y < chip.getSizeY(); y++)
            for (int x = 0; x < chip.getSizeX(); x++)
            {
                if (filteredMap[x][y] > 0 && !subYMinSet)
                {
                    subYMin = y;
                    subYMinSet = true;
                }
                else if (filteredMap[x][y] > 0) {
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
                xCenterSum += x*filteredMap[x][y];
                yCenterSum += y*filteredMap[x][y];
                weight += filteredMap[x][y];
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
        BufferedImage image = new BufferedImage(scallingRatio*chip.getSizeX(), scallingRatio*chip.getSizeY(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);  
        return image;

    }

    private void Visualize() {
            //skalowanie
            Size sz = new Size(scallingRatio*chip.getSizeX(), scallingRatio*chip.getSizeY());
            
            Mat mapCVL = new Mat();
            Mat elipsaL = new Mat();
            
            Imgproc.resize(mapCV, mapCVL, sz);
            Imgproc.resize(elipsa, elipsaL, sz);
            
            ImageIcon iconEdges = new ImageIcon(Mat2BufferedImage(mapCVL));
            labelGray.setIcon(iconEdges);
            ImageIcon iconEllipse = new ImageIcon(Mat2BufferedImage(elipsaL));
            labelEllipse.setIcon(iconEllipse);
    }

}