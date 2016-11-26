

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


@Description("Finds acorn in incoming events.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)

public class Acorn extends EventFilter2D implements FrameAnnotater {
    
    
    private boolean isAcorn = false;
    private int[][] lastTimestamps;
    private int[][] eventMap;
    
    // ================================= GUI =================================
    private boolean EnableNeighborsFiltering = getPrefs().getBoolean("Acorn.EnableNeighborsFiltering", true);
    private int threshold = getPrefs().getInt("Acorn.NeighborsThreshold", 0);
    private boolean EnableMarking = getPrefs().getBoolean("Acorn.EnableMarking", true);
    private boolean AGHLogo = getPrefs().getBoolean("Acorn.AGHLogo", true);
    public boolean getNeighborsFilteringEnabled() {
        return EnableNeighborsFiltering;
    }
    public void setNeighborsFilteringEnabled(boolean neighbors) {
        this.EnableNeighborsFiltering = neighbors;
        getPrefs().putBoolean("Acorn.EnableNeighborsFiltering", neighbors);
    }
    public int getNeighborsThreshold() {
        return threshold;
    }
    public void setNeighborsThreshold(int th) {
        this.threshold = th;
        getPrefs().putInt("Acorn.NeighborsThreshold", th);
    }
    public boolean getAGHLogoEnabled() {
        return AGHLogo;
    }
    public void setAGHLogoEnabled(boolean logoEnabled) {
        this.AGHLogo = logoEnabled;
        getPrefs().putBoolean("Acorn.AGHLogo", logoEnabled);
    }
    public boolean getMarkingEnabled() {
        return EnableMarking;
    }
    public void setMarkingEnabled(boolean markingEnabled) {
        this.EnableMarking = markingEnabled;
        getPrefs().putBoolean("Info.AGHLogo", markingEnabled);
    }
    void allocateMaps(AEChip chip){
        lastTimestamps = new int[chip.getSizeX()][chip.getSizeY()];
    }
    // =============================== END GUI =================================
    
    private void resetEventMap() {
        if (eventMap == null)
            eventMap = new int[getChip().getSizeX()][getChip().getSizeX()];
        for (int x = 0; x < getChip().getSizeX(); x++)
            for (int y = 0; y < getChip().getSizeY(); y++)
                eventMap[x][y] = 0;
    }
    
    public Acorn(AEChip chip) {
        super(chip);
        initFilter();
        resetFilter();
        
        //gropus labels
        final String nf = "Neighbors filtering", ann = "Displaying annotations";

        //tooltips
        setPropertyTooltip(ann, "MarkingEnabled", "Set true to mark on display if acorn is found");
        setPropertyTooltip(ann, "AGHLogoEnabled", "Shows information about authors");
        setPropertyTooltip(nf, "NeighborsThreshold", "Sets the threshold for noise filtering");
        setPropertyTooltip(nf, "NeighborsFilteringEnabled", "Enables removing events which do not have enough neighbors");

        //initial values
        setAGHLogoEnabled(true);
        setMarkingEnabled(true);
        setNeighborsThreshold(5);
        setNeighborsFilteringEnabled(true);
    } 
    
    @Override
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
            
            eventMap[x][y]++;
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
                if(sumNeighbors(x, y) >= threshold)
                    outItr.nextOutput().copyFrom(i);
            }
            else
                outItr.nextOutput().copyFrom(i);
        }
        return out;
    }
    
    @Override
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
    
    private int sumNeighbors(short x, short y) {
        int sum = 0;
        for (int i = x-1; i <= x+1; i++)
            for (int j = y-1; j <= y+1; j++)
                if(i > -1 && i < getChip().getSizeX() && j > -1 && j < getChip().getSizeY())
                    sum += eventMap[i][j];
        return sum;
    }
}
