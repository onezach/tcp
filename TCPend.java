import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;

enum Stage {
    NO_CONNECTION,
    HANDSHAKE,
    DATA_TRANSFER,
    FIN,
    CONNECTION_TERMINATED
}

public class TCPend {

    public static DatagramSocket socket;
    public static DatagramPacket packetIn;
    public static DatagramPacket packetOut;
    public static TCPpacket tcpIn;
    public static TCPpacket tcpOut;
    public static Stage stage;
    public static int sequenceNum;
    public static int expectedSeqNum;
    public static int curAck;
    public static File inFile;
    public static File outFile;
    public static FileReader fr;
    public static FileWriter fw;
    public static BufferedReader br;
    public static BufferedWriter bw;
    public static byte[] buffer;
    public static Queue<TCPpacket> toSend;
    public static Queue<TCPpacket> recieverBuffer;
    public static InetAddress outAddr;
    public static int rPort;
    public static ArrayList<TCPpacket> awaitingVerification;
    public static ArrayList<Integer> curExpectedAcks;
    public static ArrayList<Boolean> curReceivedAcks;
    public static boolean complete;

    public class Transmission extends Thread {
        public void run() {
            while (stage != Stage.NO_CONNECTION && stage != Stage.CONNECTION_TERMINATED) {

                // clears out acknowledged packets from verification buffer
                for (int i = 0; i < awaitingVerification.size(); i++) {
                    if (curReceivedAcks.get(i) == true) {
                        for (int j = 0; j < i + 1; j++) {
                            awaitingVerification.remove(0);
                            curExpectedAcks.remove(0);
                            curReceivedAcks.remove(0);
                        }
                        // System.out.println("After a clearing: " + curExpectedAcks + " " + curReceivedAcks);
                        break;
                    }
                }

                // sends packets that are queued up in the toSend queue
                synchronized (toSend) {
                    if (toSend.size() > 0) {
                        try {
                            TCPpacket out = toSend.remove();
                            byte[] outArr = out.serialize();
                            packetOut = new DatagramPacket(outArr, outArr.length, outAddr, rPort);

                            if (!out.getSynFlag() && !out.getFinFlag()) {
                                awaitingVerification.add(out);
                                curExpectedAcks.add(out.getSequenceNum() + out.getLength());
                                curReceivedAcks.add(false);
                            } else {
                                awaitingVerification.add(out);
                                curExpectedAcks.add(out.getSequenceNum() + 1);
                                curReceivedAcks.add(false);
                            }
                            
                            // send the packet
                            socket.send(packetOut);
                            
                        } catch (IOException e) {
                            System.out.println("Error in send");
                            e.printStackTrace();
                        }
                    }
                }
            }
            System.out.println("End of run");
            return;
        }
    }

    public class Acknowledgement extends Thread {
        public void run() {
            while (stage != Stage.NO_CONNECTION && stage != Stage.CONNECTION_TERMINATED) {
                // end condition: we have gotten a FIN from the receiver
                if (complete && toSend.size() == 0 && awaitingVerification.size() == 0) {
                    System.out.println("ACK done");
                    return;
                }
                try {
                    // response back from receiver
                    packetIn = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packetIn);
                    tcpIn = new TCPpacket();
                    tcpIn.deserialize(packetIn.getData());

                    System.out.println("Sender received an ack number " + tcpIn.getAck());

                    if (tcpIn.getSynFlag()) {
                        curAck = tcpIn.getSequenceNum() + 1;
                        stage = Stage.DATA_TRANSFER;
                    }

                    if (tcpIn.getFinFlag()) {
                        curAck = tcpIn.getSequenceNum() + 1;
                        System.out.println("Sender received a FIN and sequence number " + tcpIn.getSequenceNum());
                        complete = true;
                    }
                    
                    System.out.println(curExpectedAcks);
                    synchronized (curExpectedAcks) {
                        for (int i = 0; i < curExpectedAcks.size(); i++) {
                            System.out.println(curExpectedAcks.get(i));
                            if (tcpIn.getAck() == curExpectedAcks.get(i)) {
                                synchronized (curReceivedAcks) {
                                    curReceivedAcks.set(i, true);
                                    System.out.println("ACK " + curExpectedAcks.get(i) + " set to true");
                                }
                                break;
                            }
                        }
                    }

                } catch (Exception e) {
                    System.out.println("Acknowledgement error");
                }
                // Thread.yield();
            }
        }
    }

    public class Retransmission extends Thread {
        public void run() {
            while (stage != Stage.NO_CONNECTION && stage != Stage.CONNECTION_TERMINATED) {
                synchronized (awaitingVerification) {
                    if (awaitingVerification.size() > 0) {
                        try {
                            TCPpacket reout = awaitingVerification.get(0);
                            byte[] reoutArr = reout.serialize();
                            packetOut = new DatagramPacket(reoutArr, reoutArr.length, outAddr, rPort);
                            socket.send(packetOut);
                            System.out.println("Sender retransmits packet with sequence number " + reout.getSequenceNum() + " and length " + reout.getLength());
                            Thread.sleep(30);
                        } catch (Exception e) {
                            System.out.println("Retransmission error");
                        }
                    }
                }   
            }
        }
    }
    

    public static void sender(int port, String remoteIP, int remotePort, String fileName, int mtu, int sws) throws IOException, InterruptedException {
        System.out.println("Starting Sender"); 
        buffer = new byte[1472]; // -------------- TO CHANGE --------------
        stage = Stage.NO_CONNECTION;
        inFile = new File(fileName);
        fr = new FileReader(inFile);
        br = new BufferedReader(fr);
        complete = false;
        
        socket = new DatagramSocket(port); // NOTE: sender cannot have same port number as reciever port if on same machine
        outAddr = InetAddress.getByName(remoteIP);
        rPort = remotePort;

        toSend = new LinkedList<>();
        awaitingVerification = new ArrayList<>();
        curExpectedAcks = new ArrayList<>();
        curReceivedAcks = new ArrayList<>();

        sequenceNum = 0;

        // ----------  HANDSHAKE --------- //
        stage = Stage.HANDSHAKE;

        Transmission transThread = (new TCPend()).new Transmission();
        transThread.start();
        Acknowledgement ackThread = (new TCPend()).new Acknowledgement();
        ackThread.start();
        Retransmission retransThread = (new TCPend()).new Retransmission();
        retransThread.start();
        
        // need only set necessary fields
        tcpOut = new TCPpacket();
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setSynFlag(true);
        toSend.add(tcpOut);
        sequenceNum++;

        // ---------- DATA TRANSFER --------- // 

        while (true) {
            if (stage == Stage.DATA_TRANSFER) {
                break;
            }
            Thread.sleep(200);
        }

        // send an ack
        tcpOut = new TCPpacket();
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setAckFlag(true);
        tcpOut.setAck(curAck);
        System.out.println("Sender sends ack " + curAck);
        toSend.add(tcpOut);

        int maxSize = mtu - 52;
        int fullSegs = (int) (inFile.length() / maxSize);
        int partialSegLength = (int) (inFile.length() % maxSize);

        for (int i = 0; i < fullSegs; i++) {
            char[] cbuf = new char[maxSize];
            // byte[] payout = new byte[maxSize];
            br.read(cbuf);
            tcpOut = new TCPpacket((new String(cbuf, 0, maxSize)).getBytes());
            tcpOut.setAckFlag(true);
            tcpOut.setAck(curAck);
            tcpOut.setSequenceNum(sequenceNum);
            tcpOut.setLength(maxSize);

            toSend.add(tcpOut);
            sequenceNum = sequenceNum + maxSize;
            Thread.yield();
        }

        char[] lastBuf = new char[partialSegLength];
        // byte[] payout = new byte[maxSize];
        br.read(lastBuf);
        tcpOut = new TCPpacket((new String(lastBuf, 0, partialSegLength)).getBytes());
        tcpOut.setAckFlag(true);
        tcpOut.setAck(curAck);
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setLength(partialSegLength);

        toSend.add(tcpOut);
        sequenceNum = sequenceNum + tcpOut.getLength();

        

        // currently sending Hello and then world\n
        // synchronized (toSend) {
        //     byte[] payout = new String("Hello ").getBytes();
        //     tcpOut = new TCPpacket(payout);
        //     tcpOut.setAckFlag(true);
        //     tcpOut.setAck(curAck);
        //     tcpOut.setSequenceNum(sequenceNum);
        //     tcpOut.setLength(payout.length);
        //     toSend.add(tcpOut);
        //     sequenceNum = sequenceNum + tcpOut.getLength();
        // }
       
        // synchronized (toSend) {
        //     byte[] payout2 = new String("world\n").getBytes();
        //     tcpOut = new TCPpacket(payout2);
        //     tcpOut.setAckFlag(true);
        //     tcpOut.setAck(curAck);
        //     tcpOut.setSequenceNum(sequenceNum);
        //     tcpOut.setLength(payout2.length);
        //     toSend.add(tcpOut);
        //     sequenceNum = sequenceNum + tcpOut.getLength();
        // }

        // synchronized (toSend) {
        //     byte[] payout = new String("Hello ").getBytes();
        //     tcpOut = new TCPpacket(payout);
        //     tcpOut.setAckFlag(true);
        //     tcpOut.setAck(curAck);
        //     tcpOut.setSequenceNum(sequenceNum);
        //     tcpOut.setLength(payout.length);
        //     toSend.add(tcpOut);
        //     sequenceNum = sequenceNum + tcpOut.getLength();
        // }
       
        // synchronized (toSend) {
        //     byte[] payout2 = new String("world\n").getBytes();
        //     tcpOut = new TCPpacket(payout2);
        //     tcpOut.setAckFlag(true);
        //     tcpOut.setAck(curAck);
        //     tcpOut.setSequenceNum(sequenceNum);
        //     tcpOut.setLength(payout2.length);
        //     toSend.add(tcpOut);
        //     sequenceNum = sequenceNum + tcpOut.getLength();
        // }

        // synchronized (toSend) {
        //     byte[] payout = new String("Hello ").getBytes();
        //     tcpOut = new TCPpacket(payout);
        //     tcpOut.setAckFlag(true);
        //     tcpOut.setAck(curAck);
        //     tcpOut.setSequenceNum(sequenceNum);
        //     tcpOut.setLength(payout.length);
        //     toSend.add(tcpOut);
        //     sequenceNum = sequenceNum + tcpOut.getLength();
        // }
       
        // synchronized (toSend) {
        //     byte[] payout2 = new String("world\n").getBytes();
        //     tcpOut = new TCPpacket(payout2);
        //     tcpOut.setAckFlag(true);
        //     tcpOut.setAck(curAck);
        //     tcpOut.setSequenceNum(sequenceNum);
        //     tcpOut.setLength(payout2.length);
        //     toSend.add(tcpOut);
        //     sequenceNum = sequenceNum + tcpOut.getLength();
        // }

        // gateway to starting fin --> waiting until all data is acked
        while (true) {
            if (toSend.size() == 0 && awaitingVerification.size() == 0) {
                break;
            }
            Thread.sleep(200);
            System.out.println(awaitingVerification.size() + "/" + toSend.size());
        }

        System.out.println("Starting fin on sender side " + awaitingVerification.size() + "/" + toSend.size());

        stage = Stage.FIN;
        // send FIN to receiver
        tcpOut = new TCPpacket();
        tcpOut.setAckFlag(true);
        tcpOut.setAck(curAck);
        tcpOut.setFinFlag(true);
        tcpOut.setSequenceNum(sequenceNum);
        toSend.add(tcpOut);
        sequenceNum++;
        System.out.println("Sender sends fin with sequence number " + tcpOut.getSequenceNum());

        // gateway to final ack --> wait until FIN is acked
        while (true) {
            if (complete) {
                break;
            }
            Thread.sleep(200);
            System.out.println("almost...");
        }

        // send an ack
        tcpOut = new TCPpacket();
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setAckFlag(true);
        tcpOut.setAck(curAck);
        toSend.add(tcpOut);

        Thread.sleep(100);
        stage = Stage.CONNECTION_TERMINATED;
        socket.close();
        
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////// RECEIVER CODE /////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static void sendPacket(DatagramPacket packetIn) throws IOException {
        // serialize
        byte[] out = tcpOut.serialize();

        // get information on sender so that we can send packets back
        int senderPort = packetIn.getPort();
        InetAddress senderAddress = packetIn.getAddress();
        
        // create Datagram out and send
        packetOut = new DatagramPacket(out, out.length, senderAddress, senderPort);
        socket.send(packetOut);
    }

    private static void handleNoConnection(TCPpacket tcpIn, DatagramPacket packetIn) throws IOException {
        // check if initial message is handhsake starter
        if (!tcpIn.getSynFlag()) {
            System.out.println("Error: recieved packet in stage NO_CONNECTION without a syn flag");
            return; // Drop Packet
        }

        // checksum check
        if (!checkCheckSum(tcpIn)) {
            System.out.println("error: incorrect checksum in handleNoConnection, dropping packet");
            return; // Drop Packet
        }
        
        // print recieved
        System.out.println("Receiver got a syn");

        // create TCP packet out
        tcpOut = new TCPpacket();
        sequenceNum = 0;
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setSynFlag(true);
        tcpOut.setAck(tcpIn.getSequenceNum() + 1);
        tcpOut.setAckFlag(true);

        // send packet
        sendPacket(packetIn);

        // update
        stage = Stage.HANDSHAKE;
        expectedSeqNum = tcpIn.getSequenceNum() + 1;
        sequenceNum++;
    }

    private static void handleHandShake(TCPpacket tcpIn, DatagramPacket packetIn, String fileName) throws IOException {
        // check if initial message is handhsake starter
        if (!tcpIn.getAckFlag() || tcpIn.getAck() != expectedSeqNum) {
            System.out.println("Error: recieved packet in stage handshake without ack or wrong ack (" + tcpIn.getAck() + ")" + sequenceNum);
            return; // Drop Packet
        }
        // checksum check
        if (!checkCheckSum(tcpIn)) {
            System.out.println("error: incorrect checksum, dropping packet");
            return; // Drop Packet
        }
        
        // print recieved
        System.out.println("Receiver got ack to complete 3-way handshake\n");

        // update current stage
        stage = Stage.DATA_TRANSFER;
    }

    private static void writeToFile(byte[] payload) throws IOException {
        if (payload != null) {
            String payloadStr = new String(payload, 0, payload.length);
            System.out.println("writing to file");
            bw.write(payloadStr);
        }
    }

    private static void handleDataTransfer(TCPpacket tcpIn, DatagramPacket packetIn, int sws) throws IOException {
        // checksum check
        if (!checkCheckSum(tcpIn)) {
            System.out.println("error: incorrect checksum, dropping packet");
            return; // Drop Packet
        }
        // check if next packet is next contigous
        if (tcpIn.getSequenceNum() != expectedSeqNum){
            System.out.println("wrong sequence number recieved in data_transfer stage");
            System.out.println("expxted: " + expectedSeqNum);
            System.out.println("got: " + tcpIn.getSequenceNum());

            if (tcpIn.getSequenceNum() < expectedSeqNum || recieverBuffer.size() == sws ) {
                System.out.println("Buffer full or old packet recieved, dropping packet");
                return; // Drop Packet
            }

            // add to queue
            System.out.println("adding out of order packet to reciever buffer");
            recieverBuffer.add(tcpIn);

            // send ACK for missing 
            // create TCP packet to send ACK
            tcpOut = new TCPpacket();
            tcpOut.setAck(expectedSeqNum);
            tcpOut.setAckFlag(true);

            // send packet
            sendPacket(packetIn);
            System.out.println("Sending deup ACK for seq ");
            System.out.println("|");
            return;

        }

        System.out.println("Succesfully recived data packet");

        expectedSeqNum += tcpIn.getLength();

        // write packet to file
        writeToFile(tcpIn.getPayload());

        // if buffer not empty, flush and write
        if (recieverBuffer.size() > 0) {
            flushRecieverBuffer();

            // create TCP packet to send ACK
            tcpOut = new TCPpacket();
            tcpOut.setAck(expectedSeqNum);
            tcpOut.setAckFlag(true);

            // send packet
            sendPacket(packetIn);
            System.out.println("Sending ACK for seq " + expectedSeqNum);
            System.out.println("|");
            return;
        }

        // create TCP packet to send ACK
        tcpOut = new TCPpacket();
        tcpOut.setAck(expectedSeqNum);
        tcpOut.setAckFlag(true);
        // send packet
        sendPacket(packetIn);
        System.out.println("Sending ACK for seq " + expectedSeqNum);
        System.out.println("|");
    }

    private static void handleFin(TCPpacket tcpIn, DatagramPacket packetIn) throws IOException {
        // checksum check
        if (!checkCheckSum(tcpIn)) {
            System.out.println("error: incorrect checksum, dropping packet");
            return; // Drop Packet
        }
        System.out.println("\nFIN recived, begin connection termination");
        // create TCP packet to send ACK
        tcpOut = new TCPpacket();
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setAck(tcpIn.getSequenceNum() + 1);
        tcpOut.setAckFlag(true);
        tcpOut.setFinFlag(true);

        // send packet
        sendPacket(packetIn);
        System.out.println("Sending FIN/ACK for fin");

        stage = Stage.CONNECTION_TERMINATED;
        bw.close(); 
    }

    public static void flushRecieverBuffer() throws IOException {
        for (int i = 0; i < recieverBuffer.size(); i++) {
            if (recieverBuffer.peek().getSequenceNum() == expectedSeqNum) {
                TCPpacket packet = recieverBuffer.poll();
                writeToFile(packet.getPayload());
                expectedSeqNum += packet.getPayload().length;
            }
            else {
                break;
            }
        }
    }

    private static boolean checkCheckSum(TCPpacket packet) {
        short oldCheckSum = packet.getChecksum();
        packet.setChecksum((short)0);
        byte[] serialized = packet.serialize();
        ByteBuffer bb = ByteBuffer.wrap(serialized);
        bb.getLong();
        bb.getLong();
        bb.getInt();
        bb.getShort();
        short newCheckSum = bb.getShort();
        if (oldCheckSum != newCheckSum)
            return false;
        return true;

    }

    public static void receiver(int port, int mtu, int sws, String fileName) throws IOException {
        System.out.println("Starting Reciever\n");
        stage = Stage.NO_CONNECTION;

        // intitalize 
        outFile = new File(fileName);
        fw = new FileWriter(outFile);
        bw = new BufferedWriter(fw);
        recieverBuffer = new PriorityQueue<TCPpacket>(sws);
        socket = new DatagramSocket(port);

        while (true) {
             // receive packet
             buffer = new byte[1472];
             packetIn = new DatagramPacket(buffer, buffer.length);
             socket.receive(packetIn);
 
             tcpIn = new TCPpacket();
             tcpIn.deserialize(packetIn.getData());
            
             // check if need to end connection
             if (tcpIn.getFinFlag() && stage == Stage.DATA_TRANSFER)
                stage = Stage.FIN;
            
            // handle packet
            switch(stage){
                case NO_CONNECTION:
                    handleNoConnection(tcpIn, packetIn);
                    break;
                case HANDSHAKE:
                    handleHandShake(tcpIn, packetIn, fileName);
                    break;
                case DATA_TRANSFER:
                    handleDataTransfer(tcpIn, packetIn, sws);
                    break;
                case FIN:
                    handleFin(tcpIn, packetIn);
                    break;
                case CONNECTION_TERMINATED:
                    break;
            }
            if (stage == Stage.CONNECTION_TERMINATED)
                break;
        }
        System.out.println("Receiver closing...");
        socket.close();
    }   

    public static void main(String[] args) {
        if (args.length == 12) {
            try {
                sender(Integer.parseInt(args[1]), args[3], Integer.parseInt(args[5]), args[7], Integer.parseInt(args[9]), Integer.parseInt(args[11]));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } 
        else if (args.length == 8) {
            try {
                receiver(Integer.parseInt(args[1]), Integer.parseInt(args[3]), Integer.parseInt(args[5]), args[7]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
