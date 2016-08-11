package net.happybrackets.extras.assignment_autograding;

import com.google.gson.Gson;
import net.beadsproject.beads.core.AudioContext;
import net.beadsproject.beads.core.Bead;
import net.beadsproject.beads.core.UGen;
import net.beadsproject.beads.core.io.NonrealtimeIO;
import net.beadsproject.beads.ugens.Clock;
import net.beadsproject.beads.ugens.DelayTrigger;
import net.beadsproject.beads.ugens.RecordToFile;

import java.io.*;
import java.util.Set;

/**
 * Created by ollie on 27/07/2016.
 */
public class BeadsChecker {

    public interface BeadsCheckable {
        public void task(AudioContext ac, StringBuffer buf, Object... args);
    }

    public BeadsChecker(BeadsCheckable testCode, int totalTime, int snapshotInterval, String resultsDir) {

        File resultsDirFile = new File(resultsDir);
        if(!resultsDirFile.exists()) {
            resultsDirFile.mkdir();
        }

        AudioContext ac = new AudioContext(new NonrealtimeIO());

        //set up recorder
        RecordToFile rtf = null;
        try {
            rtf = new RecordToFile(ac, 2, new File(resultsDir + "/" + "audio.wav"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        rtf.addInput(ac.out);
        ac.out.addDependent(rtf);

        //here we're running the test code -- the system will be set up but won't have run until later
        //when we call "runForNMillisecondsNonRealTime()".
        StringBuffer result = new StringBuffer();
        testCode.task(ac, result);

        //set up a clock to make snapshots
        Clock snapshotter = new Clock(ac, snapshotInterval);
        ac.out.addDependent(snapshotter);
        snapshotter.addMessageListener(new Bead() {
            @Override
            protected void messageReceived(Bead bead) {
                if(snapshotter.isBeat()) {
                    //TAKE SNAPSHOT
                    System.out.println("** snapshot ** " + ac.getTime());
                    //grab snapshot and print somewhere
                    StringBuffer buf = new StringBuffer();
                    printCallChain(ac.out, buf, 0);
                    try {
                        FileOutputStream fos = new FileOutputStream(new File(resultsDir + "/" + "snapshot" + snapshotter.getBeatCount()));
                        PrintStream ps = new PrintStream(fos);
                        ps.append(buf.toString());
                        ps.close();
                        fos.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        ac.runForNMillisecondsNonRealTime(totalTime);
        System.out.println("** completed ** " + ac.getTime());

        //close record stream
        rtf.kill();

        //save result text
        try {
            FileOutputStream fos = new FileOutputStream(new File(resultsDir + "/" + "result"));
            PrintStream ps = new PrintStream(fos);
            ps.append(result.toString());
            ps.close();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void printCallChain(UGen ug, StringBuffer sb, int depth) {
        for(int i = 0; i < depth; i++) {
            sb.append(" ");
        }
        sb.append(ug);
        Set<UGen> inputs = ug.getConnectedInputs();
        for(UGen input : inputs) {
            sb.append("\n");
            printCallChain(input, sb, depth+1);
        }
    }

}
