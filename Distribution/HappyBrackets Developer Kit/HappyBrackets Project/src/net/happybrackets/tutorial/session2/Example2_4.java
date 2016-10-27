package net.happybrackets.tutorial.session2;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import net.beadsproject.beads.core.AudioContext;
import net.beadsproject.beads.core.Bead;
import net.beadsproject.beads.data.Buffer;
import net.beadsproject.beads.data.Pitch;
import net.beadsproject.beads.events.KillTrigger;
import net.beadsproject.beads.ugens.*;
import net.happybrackets.controller.gui.WaveformVisualiser;

import java.util.Random;

/**
 */
public class Example2_4 extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        //set up the audio context
        AudioContext ac = new AudioContext();
        ac.start();

        //random number generator
        Random random = new Random();

        //the clock
        Clock clock = new Clock(ac, 500);
        ac.out.addDependent(clock);

        int basePitch = 50;

        clock.addMessageListener(new Bead() {
            @Override
            protected void messageReceived(Bead bead) {


                if(clock.getCount() % 16 == 0) {
                    //add the waveplayer
                    int pitch = basePitch + 12 + Pitch.major[random.nextInt(7)];
                    float freq = Pitch.mtof(pitch);
                    WavePlayer wp = new WavePlayer(ac, freq, Buffer.SINE);
                    //add the gain
                    Envelope e = new Envelope(ac, 0.1f);
                    Gain g = new Gain(ac, 1, e);
                    e.addSegment(0, 200, new KillTrigger(g));
                    //connect together
                    g.addInput(wp);
                    ac.out.addInput(g);
                }


                if(clock.getCount() % 6 == 0) {
                    //add the waveplayer
                    int pitch = basePitch + Pitch.major[random.nextInt(7)];
                    float freq = Pitch.mtof(pitch);
                    WavePlayer wp = new WavePlayer(ac, freq, Buffer.SQUARE);
                    //add the gain
                    Envelope e = new Envelope(ac, 0.1f);
                    Gain g = new Gain(ac, 1, e);
                    e.addSegment(0, 200, new KillTrigger(g));
                    //connect together
                    g.addInput(wp);
                    ac.out.addInput(g);
                }

            }
        });

        //visualiser
        WaveformVisualiser.open(ac);
    }

}