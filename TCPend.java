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

public class TCPend extends Thread {

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


    public void run() {
        while (stage != Stage.NO_CONNECTION && stage != Stage.CONNECTION_TERMINATED) {


            // ---------   RETRANSMISSION AND SUCH --------- //


            if (toSend.size() > 0) {
                System.out.println("Identified");
                try {
                    TCPpacket out = toSend.remove();
                    byte[] outArr = out.serialize();
                    packetOut = new DatagramPacket(outArr, outArr.length, outAddr, rPort);
                    socket.send(packetOut);
                    System.out.println("Sender sends packet");

                    if (out.getSynFlag()) {
                        sequenceNum++;
                    } 
                    else { // all others have an ACK flag
                        if (!out.getFinFlag()) {
                            if (out.getLength() > 0) {
                                sequenceNum = sequenceNum + out.getLength();
                            } else {
                                continue; // just an ACK with no data
                            }
                        } else {
                            sequenceNum++;
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Error in send");
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("End of run");
    }

    public static void sender(int port, String remoteIP, int remotePort, String fileName, int mtu, int sws) throws IOException {
        System.out.println("Starting Sender"); 
        buffer = new byte[1472]; // -------------- TO CHANGE --------------
        stage = Stage.NO_CONNECTION;
        // inFile = new File(fileName);
        // fr = new FileReader(inFile);
        // br = new BufferedReader(br);
        
        socket = new DatagramSocket(port); // NOTE: sender cannot have same port number as reciever port if on same machine
        outAddr = InetAddress.getByName(remoteIP);
        rPort = remotePort;

        sequenceNum = 0;

        toSend = new LinkedList<>();

        (new TCPend()).start();
        
        // ----------  HANDSHAKE --------- //
        stage = Stage.HANDSHAKE;
        // need only set necessary fields
        tcpOut = new TCPpacket();
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setSynFlag(true);
        toSend.add(tcpOut);

        // response back from receiver
        packetIn = new DatagramPacket(buffer, buffer.length);
        socket.receive(packetIn);
        tcpIn = new TCPpacket();
        tcpIn.deserialize(packetIn.getData());

        // verify that an ack flag is detected
        if (tcpIn.getAckFlag() && tcpIn.getSynFlag()) {
            // inSeq = tcpIn.getSequenceNum();
            curAck = tcpIn.getSequenceNum() + 1;
            System.out.println("Sender received sequence number " + tcpIn.getSequenceNum() + " and ack number " + tcpIn.getAck());
        } else {
            System.out.println("Sender didn't receive a SYN/ACK");
            return;
        }

        // send an ack
        tcpOut = new TCPpacket();
        tcpOut.setAckFlag(true);
        tcpOut.setAck(curAck);
        System.out.println("Sender sends ack " + curAck);
        toSend.add(tcpOut);

        // ---------- DATA TRANSFER --------- // 
        byte[] payout = new String("Hello ").getBytes();
        tcpOut = new TCPpacket(payout);
        tcpOut.setAckFlag(true);
        tcpOut.setAck(curAck);
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setLength(payout.length);
        toSend.add(tcpOut);

        // response back from receiver
        packetIn = new DatagramPacket(buffer, buffer.length);
        socket.receive(packetIn);
        tcpIn = new TCPpacket();
        tcpIn.deserialize(packetIn.getData());

        // verify that an ack flag is detected
        if (tcpIn.getAckFlag()) {
            System.out.println("Sender received ack number " + tcpIn.getAck());
        } else {
            System.out.println("Sender didn't receive an ACK after hello");
            return;
        }

        byte[] payout2 = new String("world\n").getBytes();
        tcpOut = new TCPpacket(payout2);
        tcpOut.setAckFlag(true);
        tcpOut.setAck(curAck);
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setLength(payout2.length);
        toSend.add(tcpOut);

        // response back from receiver
        packetIn = new DatagramPacket(buffer, buffer.length);
        socket.receive(packetIn);
        tcpIn = new TCPpacket();
        tcpIn.deserialize(packetIn.getData());
 
        // verify that an ack flag is detected
        if (tcpIn.getAckFlag()) {
            System.out.println("Sender received ack number " + tcpIn.getAck());
        } else {
            System.out.println("Sender didn't receive an ACK after world");
            return;
        }

        // send FIN to receiver
        tcpOut = new TCPpacket();
        tcpOut.setAckFlag(true);
        tcpOut.setAck(curAck);
        tcpOut.setFinFlag(true);
        tcpOut.setSequenceNum(sequenceNum);
        toSend.add(tcpOut);


        // response back from receiver
        packetIn = new DatagramPacket(buffer, buffer.length);
        socket.receive(packetIn);
        tcpIn = new TCPpacket();
        tcpIn.deserialize(packetIn.getData());

        // verify that an ack flag is detected
        if (tcpIn.getAckFlag()) {
            System.out.println("Sender received ack number " + tcpIn.getAck());
        } else {
            System.out.println("Sender didn't receive an before the FIN");
            return;
        }

        // response back from receiver
        packetIn = new DatagramPacket(buffer, buffer.length);
        socket.receive(packetIn);
        tcpIn = new TCPpacket();
        tcpIn.deserialize(packetIn.getData());

        // verify that a FIN flag is detected
        if (tcpIn.getFinFlag()) {
            curAck++;
            System.out.println("Sender received a FIN and sequence number " + tcpIn.getSequenceNum());
        } else {
            System.out.println("Sender didn't receive a FIN");
            return;
        }

        // send an ack
        tcpOut = new TCPpacket();
        tcpOut.setAckFlag(true);
        tcpOut.setAck(curAck);
        toSend.add(tcpOut);

        socket.close();
        stage = Stage.CONNECTION_TERMINATED;
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
        String payloadStr = new String(payload, 0, payload.length);
        System.out.println("writing to file");
        bw.write(payloadStr);
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
        expectedSeqNum += tcpIn.getPayload().length;

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
        tcpOut.setAck(tcpIn.getSequenceNum() + tcpIn.getPayload().length + 1);
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
        tcpOut.setAck(tcpIn.getSequenceNum() + 1);
        tcpOut.setAckFlag(true);

        // send packet
        sendPacket(packetIn);
        System.out.println("Sending ACK for fin");

        // This is where reciever would clean up any loose ends before sending fin if we were actually doing that

        // create TCP packet to send FIN
        tcpOut = new TCPpacket();
        tcpOut.setFinFlag(true);
        tcpOut.setSequenceNum(sequenceNum);
        
        sendPacket(packetIn);
        System.out.println("Sending FIN for fin\n");

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
