package net.happybrackets.assignment_tasks.Session8;

import net.happybrackets.extras.assignment_autograding.SimpleCheckable;

/**
 * In this task you should populate the array smoothSensorData by running a sliding window average over the array sensorData, using the window length specified by windowLength.
 */
public class CodeTask8_1 implements SimpleCheckable {

    public static void main(String[] args) {
        StringBuffer buf = new StringBuffer();
        float[] sensorData = new float[]{0.1f, 0.13f, 0.154f, 0.1234f, 0.14523f, 0.12965f, 0.1f};
        float[] smoothSensorData = new float[sensorData.length];
        int windowLength = 3;
        new CodeTask8_1().task(new Object[]{buf, sensorData, smoothSensorData, windowLength});
        System.out.println(buf);
    }

    @Override
    public void task(Object... objects) {
        //********** do your work here ONLY **********
        //your objects...
        StringBuffer buf = (StringBuffer)objects[0];
        float[] sensorData = (float[])objects[1];
        float[] smoothSensorData = (float[])objects[2];
        int windowLength = (int)objects[3];
        //do stuff here, remove the following line
        buf.append("Hello World!\n");
        //********** do your work here ONLY **********
    }
}