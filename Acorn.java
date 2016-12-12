

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


@Description("Finds acorn in incoming events.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)

public class Acorn extends EventFilter2D implements FrameAnnotater {
    
    
    private boolean isAcorn = false;
    private int[][] lastTimestamps;
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
    
    // ================================= GUI PARAMS =================================
    private boolean EnableSquare = getPrefs().getBoolean("Acorn.EnableSquare", true);
    private boolean EnableCenter = getPrefs().getBoolean("Acorn.EnableCenter", true);
    private boolean EnableNeighborsFiltering = getPrefs().getBoolean("Acorn.EnableNeighborsFiltering", true);
    private int threshold = getPrefs().getInt("Acorn.NeighborsThreshold", 0);
    private boolean EnableMarking = getPrefs().getBoolean("Acorn.EnableMarking", true);
    private boolean AGHLogo = getPrefs().getBoolean("Acorn.AGHLogo", true);
    private boolean IgnoreMultipleNeighbors = getPrefs().getBoolean("Acorn.IgnoreMultipleNeighbors", true);
    private boolean EnableCanny = getPrefs().getBoolean("Acorn.EnableCanny", true);
    private int CannyT1 = getPrefs().getInt("Acorn.CannyT1", 0);
    private int CannyT2 = getPrefs().getInt("Acorn.CannyT2", 0);
    
    public boolean getEnableCanny() {
        return this.EnableCanny;
    }
    public void setEnableCanny(boolean enable) {
        this.EnableCanny = enable;
        getPrefs().putBoolean("Acorn.EnableCanny", enable);
    }
    public int getCannyT1() {
        return this.CannyT1;
    }
    public void setCannyT1(int val) {
        if (val < this.CannyT2)
        {
            this.CannyT1 = val;
            getPrefs().putInt("Acorn.CannyT1", val);
        }
        else
        {
            JOptionPane.showMessageDialog(new JFrame("Error"), "CannyT1 threshold must be smaller than CannyT2!");
            getPrefs().putInt("Acorn.CannyT1", this.CannyT1);
        }
    }
    public int getCannyT2() {
        return this.CannyT2;
    }
    public void setCannyT2(int val) {
        if (val > this.CannyT1)
        {
            this.CannyT2 = val;
            getPrefs().putInt("Acorn.CannyT2", val);
        }
        else
        {
            JOptionPane.showMessageDialog(new JFrame("Error"), "CannyT2 threshold must be larger than CannyT2!");
            getPrefs().putInt("Acorn.CannyT2", this.CannyT2);
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
        return this.threshold;
    }
    public void setNeighborsThreshold(int th) {
        this.threshold = th;
        getPrefs().putInt("Acorn.NeighborsThreshold", th);
    }
    public boolean getAGHLogoEnabled() {
        return this.AGHLogo;
    }
    public void setAGHLogoEnabled(boolean logoEnabled) {
        this.AGHLogo = logoEnabled;
        getPrefs().putBoolean("Acorn.AGHLogo", logoEnabled);
    }
    public boolean getMarkingEnabled() {
        return this.EnableMarking;
    }
    public void setMarkingEnabled(boolean markingEnabled) {
        this.EnableMarking = markingEnabled;
        getPrefs().putBoolean("Info.AGHLogo", markingEnabled);
    }
    void allocateMaps(AEChip chip){
        this.lastTimestamps = new int[chip.getSizeX()][chip.getSizeY()];
    }
    // =============================== END GUI PARAMS =================================
    
    //method which allocates memory for eventMap array and/or fills it with zeros
    private void resetEventMap() {
        if (eventMap == null)
            eventMap = new int[getChip().getSizeX()][getChip().getSizeX()];
        if (processedMap == null)
            processedMap = new int[chip.getSizeX()][chip.getSizeY()];

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
        initFilter();
        resetFilter();
        
        //gropu's labels
        final String nf = "Neighbors filtering", ann = "Displaying annotations", can = "Canny - edge detection";

        //tooltips
        setPropertyTooltip(ann, "MarkingEnabled", "Set true to mark on display if acorn is found");
        setPropertyTooltip(ann, "AGHLogoEnabled", "Shows information about authors");
        setPropertyTooltip(ann, "MarkCenter", "Prints dot in the gravity center of events");
        setPropertyTooltip(ann, "EnableSquare", "Prints square surrounding 90% of events in frame");
        setPropertyTooltip(nf, "NeighborsThreshold", "Sets the threshold for noise filtering");
        setPropertyTooltip(nf, "NeighborsFilteringEnabled", "Enables removing events which do not have enough neighbors");
        setPropertyTooltip(nf, "IgnoreMultipleNeighbors", "Ignore multiple events under the same address");
        setPropertyTooltip(can, "EnableCanny", "Enables Cany edge detection algorithm");
        setPropertyTooltip(can, "CannyT1", "Canny filter - threshold T1");
        setPropertyTooltip(can, "CannyT2", "Canny filter - threshold T2");
        
        //initial values
        setAGHLogoEnabled(true);
        setMarkingEnabled(true);
        setMarkCenter(false);
        setEnableSquare(false);
        setNeighborsThreshold(5);
        setNeighborsFilteringEnabled(true);
        setIgnoreMultipleNeighbors(false);
        setCannyT2(10);
        setCannyT1(5);
    }
    
    @Override
    //main filtering method
    public EventPacket<?> filterPacket(EventPacket<?> in) { 
        //avoid always running filter
        if(!filterEnabled) 
            return in;
        
        //making sure we have a valid output packet
        checkOutputPacketEventType(in); 
        if(lastTimestamps==null) 
            allocateMaps(chip);
        
        //check if memory for eventMap has been allocated 
        resetEventMap();
        
        //initialize and obtain the output event iterator
        OutputEventIterator outItr = out.outputIterator(); 
        int sx=chip.getSizeX()-1;
        int sy=chip.getSizeY()-1;
        int ts=0;
        
        //iterate over the inpu packet, cast the object to basic
        //event to get timestamp, x and y
        for(Object e:in) { 
            BasicEvent i = (BasicEvent)e; 
            short x = (short)(i.x);
            short y = (short)(i.y);
            
            //ignore special events, e.g. with negative address
            if(x > sx || y > sy || x < 0 || y < 0)
                continue;
            
            eventSum++;
            eventMap[x][y]++;
        }
        countSubRegion();
        countCenter();

        // filter map.
        for (int x = 1; x < chip.getSizeX()-1; x++)
            for (int y = 1; y < chip.getSizeY()-1; y++)
                if(EnableNeighborsFiltering)
                {
                    if (sumNeighbors((short) x, (short) y) >= this.threshold)
                        this.processedMap[x][y] = this.eventMap[x][y];
                }
                else
                    this.processedMap[x][y] = this.eventMap[x][y];
                        
        if (this.EnableCanny)
            this.canny(processedMap, processedMap);
        
        //second cycle for copying proper events
        /*for(Object e:in) { 
            BasicEvent i = (BasicEvent)e; 
            short x = (short)(i.x);
            short y = (short)(i.y);
            
            //ignore special events, e.g. with negative address
            if(x > sx || y > sy || x < 0 || y < 0)
                continue;
            
            if(EnableNeighborsFiltering) {
                if(sumNeighbors(x, y) >= threshold)
                    outItr.nextOutput().copyFrom(i);
            }
            else
                outItr.nextOutput().copyFrom(i);
        }*/
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
                if (EnableMarking) {
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
                if(EnableCanny) {
                    if (processedMap[x][y] > 0) {
                        gl.glColor4f(1f, 1f, 1f, 1f);
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
                sum += eventMap[x][y];
                if (!subXMinSet && sum >= 0.1 * eventSum) {
                    subXMin = x;
                    subXMinSet = true;
                }
                else if (!subXMaxSet && sum >= 0.9 * eventSum) {
                    subXMax = x;
                    subXMaxSet = true;
                }
            }
        sum = 0;
        //ylimits
        for (int y = 0; y < chip.getSizeY(); y++)
            for (int x = 0; x < chip.getSizeX(); x++)
            {
                sum += eventMap[x][y];
                if (!subYMinSet && sum >= 0.05 * eventSum) {
                    subYMin = y;
                    subYMinSet = true;
                }
                else if (!subYMaxSet && sum >= 0.95 * eventSum) {
                    subYMax = y;
                    subYMaxSet = true;
                }
            }
    }
    
    private void countCenter() {
        xCenterSum = yCenterSum = 0;
        xCenter = yCenter = 0;
        for (int x = 0; x < chip.getSizeX(); x++)
            for (int y = 0; y < chip.getSizeY(); y++)
            {
                xCenterSum += x*eventMap[x][y];
                yCenterSum += y*eventMap[x][y];
            }
        if(eventSum != 0) {
            xCenter = (int) (xCenterSum / (long) eventSum);
            yCenter = (int) (yCenterSum / (long) eventSum);
        }
    }
    
    private void convolution(int[][] input, int[][] output, int maskInd) {
        int[][] mask;
        switch (maskInd)
        {
            case 1:
                mask = this.sobelMaskH;
                break;
            case 2:
                mask = this.sobelMaskV;
                break;
            default:
                mask = this.gaussMask;
        }
        
        for (int x = 1; x < chip.getSizeX()-1; x++)
            for (int y = 1; y < chip.getSizeY()-1; y++)
            {
                output[x][y] = input[x-1][y-1]*mask[0][0];
                output[x][y] += input[x-1][y]*mask[0][1];
                output[x][y] += input[x-1][y+1]*mask[0][2];
                output[x][y] += input[x][y-1]*mask[1][0];
                output[x][y] += input[x][y]*mask[1][1];
                output[x][y] += input[x][y+1]*mask[1][2];
                output[x][y] += input[x+1][y-1]*mask[2][0];
                output[x][y] += input[x+1][y]*mask[2][1];
                output[x][y] += input[x+1][y+1]*mask[2][2];
            }
    }
    
    // canny edge detection algorithm
    private void canny(int[][] input, int[][] output) {
        int[][] gauss = new int[chip.getSizeX()][chip.getSizeY()];
        int[][] sobelV = new int[chip.getSizeX()][chip.getSizeY()];
        int[][] sobelH = new int[chip.getSizeX()][chip.getSizeY()];
        int[][] edgeMap = new int[chip.getSizeX()][chip.getSizeY()];
        double[][] gradient = new double[chip.getSizeX()][chip.getSizeY()];
        int[][] direction = new int[chip.getSizeX()][chip.getSizeY()];

        // gaussian filter
        this.convolution(input, gauss, 0);
        // sobel operators
        this.convolution(gauss, sobelH, 1);
        this.convolution(gauss, sobelV, 2);
        
        // gradient and direction computing
        // prevent from dividing by zero
        double e = 0.0000001;
        for (int x = 1; x < chip.getSizeX()-1; x++)
            for (int y = 1; y < chip.getSizeY()-1; y++)
            {
                gradient[x][y] = Math.sqrt(sobelV[x][y]^2 + sobelH[x][y]^2);
                direction[x][y] = 45 * (int) Math.round(Math.toDegrees(Math.atan(sobelV[x][y]/(sobelH[x][y]+e)))/45.0);
            }
        
        // zerujemy gradienty nie będące maksymalne na kierunku prostopadłym do danego gradientu
        for (int x = 1; x < chip.getSizeX()-1; x++)
            for (int y = 1; y < chip.getSizeY()-1; y++)
            {
                switch (direction[x][y])
                {
                    case -180:
                    case 0:
                    case 180:
                        gradient[x][y] = gradient[x][y] > gradient[x][y-1] && gradient[x][y] > gradient[x][y+1] ? gradient[x][y] : 0;
                        break;
                    case 45:
                    case -135:
                        gradient[x][y] = gradient[x][y] > gradient[x-1][y-1] && gradient[x][y] > gradient[x+1][y+1] ? gradient[x][y] : 0;
                        break;
                    case 135:
                    case -45:
                        gradient[x][y] = gradient[x][y] > gradient[x+1][y-1] && gradient[x][y] > gradient[x-1][y+1] ? gradient[x][y] : 0;
                        break;
                    default: // 90 v -90
                        gradient[x][y] = gradient[x][y] > gradient[x-1][y] && gradient[x][y] > gradient[x+1][y] ? gradient[x][y] : 0;
                }
            }
        
        for (int x = 1; x < chip.getSizeX()-1; x++)
            for (int y = 1; y < chip.getSizeY()-1; y++)
            {
                if (gradient[x][y] > this.CannyT2)
                {
                    travers(gradient, direction, edgeMap, x, y, 1);
                    travers(gradient, direction, edgeMap, x, y, -1);
                }
            }
        for (int x = 0; x < chip.getSizeX(); x++)
            for (int y = 0; y < chip.getSizeY(); y++)
            {
                output[x][y] = edgeMap[x][y];
            }
    }
    
    // pomocnicza metoda do Canny
    private void travers(double[][] gradient, int[][] direction, int[][] edgeMap, int x, int y, int side){
        if (x == 0 || y == 0 || x == chip.getSizeX()-1 || y == chip.getSizeY()-1)
            return;
        
        if (gradient[x][y] > this.CannyT1)
            edgeMap[x][y] = 1;
        else
            return;
        
        // rekurencyjnie przechodzimy w kierunku gradientu do momentu aż gradient[x][y] <= CannyT1
        switch (direction[x][y])
        {
            case -180:
            case 0:
            case 180:
                travers(gradient, direction, edgeMap, x+side, y, side);
                break;
            case 45:
            case -135:
                travers(gradient, direction, edgeMap, x+side, y-side, side);
                break;
            case 135:
            case -45:
                travers(gradient, direction, edgeMap, x-side, y+side, side);
                break;
            default: // 90 v -90
                travers(gradient, direction, edgeMap, x, y+side, side);
        }
    }
}
