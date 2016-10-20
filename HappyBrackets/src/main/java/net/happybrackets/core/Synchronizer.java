package net.happybrackets.core;

import de.sciss.net.OSCMessage;
import de.sciss.net.OSCTransmitter;
import net.happybrackets.core.config.LoadableConfig;

import java.io.IOException;
import java.net.*;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Synchronizer {

	/*
	 * A tool for each device to work out its current synch with respect to all other devices.
	 * We keep this independent of the audio system because the audio system start-time needs to be synched.
	 *
	 * Each synchronizer sends regular pulses every second with the syntax:
	 * s <MAC1> <timeMS>
	 *
	 * An s means send. Upon receiving an s, each synchronizer also responds with
	 * r <MAC1> <timeMS> <MAC2> <timeMS>
	 */

	final static Logger logger = LoggerFactory.getLogger(Synchronizer.class);
    final static String oscPath = "/hb/synchonizer";

    private BroadcastManager broadcast;

	private String myMAC = "0"; //how to uniquely identify this machine
	private long timeCorrection = 0;			//add this to current time to getInstance the REAL current time
	private long stableTimeCorrection = 0;
	private long lastTick;
	private int stabilityCount = 0;

	private boolean on = true;
	private boolean verbose = false;
	private boolean timedebug = false;

	private Map<Long, Map<String, long[]>> log;		//first referenced by message send time, then by respodent's name, with the time the respondent replied and the current time

	static Synchronizer singletonSynchronizer;

	public synchronized static Synchronizer getInstance() {
		if(singletonSynchronizer == null) {
			singletonSynchronizer = new Synchronizer();
		}
		return singletonSynchronizer;
	}

	public static long time() {
		return getInstance().correctedTimeNow();
	}

	private Synchronizer() {
		//basics
		log = new Hashtable<Long, Map<String, long[]>>();
        broadcast = new BroadcastManager(LoadableConfig.getInstance().getMulticastAddr(), LoadableConfig.getInstance().getClockSynchPort());
		try {
			//start listening
			setupListener();
            logger.info("Synchronizer is listening.");
            //start sending
            startSending();
            logger.info("Synchronizer is sending synch pulses.");
            //display clock (optional)
            //displayClock();
		} catch(Exception e) {
			logger.error("Unable to setup Synchronizer!", e);
		}
	}

	public long stableTimeNow() {
		return System.currentTimeMillis() + stableTimeCorrection;
	}

	public long correctedTimeNow() {
		return stableTimeNow() + timeCorrection;
	}

	public void displayClock() {
		Thread t = new Thread() {
			public void run() {
				while(on) {
					long timeNow = correctedTimeNow();
					long tick = timeNow / 10000;
					if(tick != lastTick && timeNow % 10000 < 4) {
						//display
						Date d = new Date(timeNow);
						// This looks like it shouldn't be logged?
						System.out.println("The time is: " + d.getHours() + ":" + d.getMinutes() + ":" + d.getSeconds() + " (short correction = " + timeCorrection + "ms, long correction = " + stableTimeCorrection + "ms)");
						lastTick = tick;
					}
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						logger.error("Poll interval interupted for displayClock!", e);
					}
				}
			}
		};
		t.start();
	}

	private void setupListener() throws IOException {
        broadcast.addOnMessage(new BroadcastManager.OnListener(){
            @Override
            public void cb(NetworkInterface ni, OSCMessage msg, SocketAddress sender, long time) {
				if (!msg.getName().equals(oscPath)) {
                    return;
                }
                else if(msg.getArgCount() != 5) {
                    logger.debug("Received sync message with {} args, expected 5", msg.getArgCount());
                    return;
                }
                String myMAC = Device.selectMAC(ni);
//				String[] parts = new String[msg.getArgCount()];
//                for (int i = 0; i < msg.getArgCount(); i++) {
//                    parts[i] = (String) msg.getArg(i);
//                }
//                String[] parts = ((String) msg.getArg(0)).split("[ ]");

//                for(int i = 0; i < parts.length; i++) {
//                    parts[i] = parts[i].trim();
//                }

                if (logger.isTraceEnabled()) {
                    String logMessage = msg.getName() + " args:\n";
                    Object[] logArgs = new Object[msg.getArgCount() * 2];
                    for (int i = 0; i < msg.getArgCount(); i++) {
                        logMessage += "    message arg {} is a {}\n";
                        logArgs[2*i] = i;
                        logArgs[(2*i)+1] = msg.getArg(i).getClass().getName();
                    }
                    logger.trace(logMessage, logArgs);
                }

                String action           = (String) msg.getArg(0);
                String sourceMAC        = (String) msg.getArg(1);
                long timeOriginallySent = Long.parseLong((String) msg.getArg(2));
                String otherMAC         = (String) msg.getArg(3);
                long timeReturnSent     = Long.parseLong((String) msg.getArg(4));

                if(action.equals("s")) {
                    //an original send message
                    //respond if you were not the sender
                    if(!sourceMAC.equals(myMAC)) {
                        //ensure our long values are strings so we can upack them at the other side
                        // by default longs become ints when packed for OSC :( probably a comparability feature for max the ancient dinosaur.
                        broadcast.broadcast(oscPath, "r", sourceMAC, ""+timeOriginallySent, myMAC, ""+stableTimeNow());
                    }
                }
                else if(action.equals("r")) {
                    //a response message
                    //respond only if you WERE the sender
                    if(sourceMAC.equals(myMAC)) {
                        //find out how long the return trip was
//                        long timeOriginallySent = Long.parseLong(timeOriginallySent);
//                        String otherMAC = otherMAC;
//                        long timeReturnSent = Long.parseLong(timeReturnSent);
                        long currentTime = stableTimeNow();
                        log(timeOriginallySent, otherMAC, timeReturnSent, currentTime);

                        if(verbose) {
                            long returnTripTime = currentTime - timeOriginallySent;
                            long timeAheadOfOther = (currentTime - (returnTripTime / 2)) - timeReturnSent;	//+ve if this unit is ahead of other unit
                            System.out.println("Return trip from " + myMAC + " to " + otherMAC + " took " + returnTripTime + "ms");
                            System.out.println("This machine (" + myMAC + ") is " + (timeAheadOfOther > 0 ? "ahead of" : "behind") + " " + otherMAC + " by " + Math.abs(timeAheadOfOther) + "ms");
                        }
                    }
                }
            }
        });

	}

	public void doAtTime(final Runnable r, long time) {
		final long waitTime = time - correctedTimeNow();
		if(waitTime <= 0) {				//either run immediately
			r.run();
		} else {						//or wait the required time
			//create a new thread just in order to run this incoming thread
			new Thread() {
				public void run() {
					try {
						Thread.sleep(waitTime);
					} catch (InterruptedException e) {
						logger.error("Interupted while waiting to execute action in Synchronizer.doAtTime!", e);
					}
					r.run();
				}
			}.start();

		}
	}

	public void doAtNextStep(final Runnable r, long timeMultiplier) {
		long timeNow = correctedTimeNow();
		long time = ((timeNow / timeMultiplier) + 1) * timeMultiplier;
		final long waitTime = time - correctedTimeNow();
		if(waitTime <= 0) {				//either run immediately
			r.run();
		} else {						//or wait the required time
			//create a new thread just in order to run this incoming thread
			new Thread() {
				public void run() {
					try {
						Thread.sleep(waitTime);
					} catch (InterruptedException e) {
						logger.error("Interupted while waiting for next time step!", e);
					}
					r.run();
				}
			}.start();

		}
	}

	private void startSending() {
		Thread t = new Thread() {
			public void run() {
                BroadcastManager.OnTransmitter sync = new BroadcastManager.OnTransmitter() {
                    @Override
                    public void cb(NetworkInterface ni, OSCTransmitter transmitter) throws IOException {
                        String myMac = Device.selectMAC(ni);
                        transmitter.send(
                                new OSCMessage(
                                        oscPath,
                                        new Object[] {"s", myMac, ""+stableTimeNow(), myMac, ""+stableTimeNow() }
                                )
                        );
                    }
                };
				while(on) {

                    broadcast.forAllTransmitters(sync);

					try {
						Thread.sleep(500 + (int)(100 * Math.random()));	//randomise send time to break network send patterns
					} catch (InterruptedException e) {
						logger.error("Interupted while waiting to receive Synchronizer messages!", e);
					}
					//now that all of the responses have come back...
					calculateTimeCorrection();
					try {
						Thread.sleep(500 + (int)(100 * Math.random()));	//randomise send time to break network send patterns
					} catch (InterruptedException e) {
						logger.error("Interupted while waiting to send Synchronizer message!", e);
					}
				}
			}
		};
		t.start();
	}

	public void close() {
		on = false;
		broadcast.dispose();
	}

	/**
	 * Estimates the difference between this devices clock and the clock of the "leader" device.
	 * This method modifies the value of the timeCorrection field, and less frequently updates the stableTimeCorrection field.
	 */
	private void calculateTimeCorrection() {
		for(Long sendTime : log.keySet()) {
			Map<String, long[]> responses = log.get(sendTime);
			//find the leader
			String theLeader = myMAC;
			if(timedebug) System.out.println("At send time = " + sendTime);
			for(String mac : responses.keySet()) {
				if(timedebug) System.out.println("          Response from: " + mac + " return sent: " + responses.get(mac)[0] + ", received: " + responses.get(mac)[1]);
				if(theLeader.compareTo(mac) < 0) {
					theLeader = mac;
				}
			}
			if(timedebug) System.out.println("Leader is " + theLeader);
			if(theLeader != myMAC) {
				//if you are not the leader then make a time adjustment
				long[] times = responses.get(theLeader);
				long leaderResponseTime = times[0];
				long receiveTime = times[1];
				long roundTripTime = receiveTime - sendTime;
				long messageTime = roundTripTime / 2;
				long receiveTimeAccordingToLeader = leaderResponseTime + messageTime;
				timeCorrection = receiveTimeAccordingToLeader - receiveTime;
				if(timedebug) System.out.println("time correction: " + timeCorrection + ", message time: " + messageTime + ", response sent: " + leaderResponseTime + ", response received: " + receiveTime);
			}
		}
		//finally, clear the log (for now - we might make the log last longer later)
		log.clear();
		//stability count
		if(stabilityCount++ == 20) {
			stabilityCount = 0;
			stableTimeCorrection += timeCorrection;
			timeCorrection = 0;
		}
	}

	public float getStability() {
		if(stableTimeCorrection == 0) {
			return 0;
		} else {
			return (float)(timeCorrection / stableTimeCorrection);
		}
	}

//	public void messageReceived(String msg) {
//		String[] parts = msg.split("[ ]");
//		for(int i = 0; i < parts.length; i++) {
//			parts[i] = parts[i].trim();
//		}
//		if(parts[0].equals("s")) {
//			//an original send message
//			//respond if you were not the sender
//			if(!parts[1].equals(myMAC)) {
//				broadcast("r " + parts[1] + " " + parts[2] + " " + myMAC + " " + stableTimeNow());
//			}
//		} else if(parts[0].equals("r")) {
//			//a response message
//			//respond only if you WERE the sender
//			if(parts[1].equals(myMAC)) {
//				//find out how long the return trip was
//				long timeOriginallySent = Long.parseLong(parts[2]);
//				String otherMAC = parts[3];
//				long timeReturnSent = Long.parseLong(parts[4]);
//				long currentTime = stableTimeNow();
//				log(timeOriginallySent, otherMAC, timeReturnSent, currentTime);
//				if(verbose) {
//					long returnTripTime = currentTime - timeOriginallySent;
//					long timeAheadOfOther = (currentTime - (returnTripTime / 2)) - timeReturnSent;	//+ve if this unit is ahead of other unit
//					System.out.println("Return trip from " + myMAC + " to " + parts[3] + " took " + returnTripTime + "ms");
//					System.out.println("This machine (" + myMAC + ") is " + (timeAheadOfOther > 0 ? "ahead of" : "behind") + " " + otherMAC + " by " + Math.abs(timeAheadOfOther) + "ms");
//				}
//			}
//		}
//	}

	/**
	 *
	 * @param timeOriginallySent
	 * @param otherMAC
	 * @param timeReturnSent
	 * @param currentTime
	 */
	private void log(long timeOriginallySent, String otherMAC, long timeReturnSent, long currentTime) {
		if(!log.containsKey(timeOriginallySent)) {
			log.put(timeOriginallySent, new Hashtable<String, long[]>());
		}
		log.get(timeOriginallySent).put(otherMAC, new long[] {timeReturnSent, currentTime});
	}

//	/**
//	 * Send String s over the multicast group.
//	 *
//	 * @param s the message to send.
//	 */
//	public void broadcast(String s) {
//		byte buf[] = null;
//		try {
//			buf = s.getBytes("US-ASCII");
//		} catch (UnsupportedEncodingException e) {
//			logger.error("Unable to encode string {} with current encoding!", s, e);
//		}
//		logger.trace("Sending message: {} (length in bytes = {})", s, buf.length);
//		// Create a DatagramPacket
//		DatagramPacket pack = null;
//		try {
//			pack = new DatagramPacket(
//				buf,
//				buf.length,
//				InetAddress.getByName(LoadableConfig.getInstance().getMulticastAddr()),
//					LoadableConfig.getInstance().getClockSynchPort()
//			);
//		} catch (UnknownHostException e) {
//			logger.error("Unable to send Synchronizer message to host!", e);
//		}
//		try {
//			broadcastSocket.send(pack);
//		} catch (IOException e) {
//			logger.error("Error sending Synchronizer message!", e);
//		}
//	}

    public void broadcast(String msg) {
        broadcast.broadcast(oscPath, msg);
    }

	public static void main(String[] args) {
		Synchronizer s = getInstance();
		s.displayClock();
	}

}
