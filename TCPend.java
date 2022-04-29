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
    public static int currentAck;
    public static int curAck;
    public static File inFile;
    public static File outFile;
    public static FileReader fr;
    public static FileWriter fw;
    public static BufferedReader br;
    public static BufferedWriter bw;
    public static byte[] buffer;
    public static Queue<TCPpacket> recieverBuffer;
    public static ArrayList<TCPpacket> senderBuffer;
    public static InetAddress outAddr;
    public static int numDuplicates;
    public static int rPort;
    public static int lastAck;
    private static int numFullSegments;
    private static int lengthOfLastSegment;
    private static int numPacketsCreated;

    public static void sendPacketSender(TCPpacket tcpOutPacket) throws IOException {
        byte[] serialzied = tcpOutPacket.serialize();
        packetOut = new DatagramPacket(serialzied, serialzied.length, outAddr, rPort);
        socket.send(packetOut);
        tcpOutPacket.setIsSent(true);
    }

    public static void initiateHandShake() throws IOException {
        tcpOut = new TCPpacket();
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setSynFlag(true);
        sendPacketSender(tcpOut);
        System.out.println("Intiated HandShake with sequenceNum " + sequenceNum);
        sequenceNum++;
        stage = stage.HANDSHAKE;
    }

    public static void completeHandShake() throws IOException {
        if (tcpIn.getSynFlag() && tcpIn.getAckFlag() && tcpIn.getAck() == sequenceNum) {
            System.out.println("Recieved Syn/Ack, sending ACK");
            expectedSeqNum = tcpIn.getSequenceNum() + 1;
            tcpOut = new TCPpacket();
            tcpOut.setAckFlag(true);
            tcpOut.setAck(tcpIn.getSequenceNum() + 1);
            sendPacketSender(tcpOut);
            currentAck = 1;
            stage = stage.DATA_TRANSFER;
        }
        else {
            System.out.println("Error in intitiateHandshake");
            return; // drop packet
        }
    }

    public static void populateBuffer(int sws, int mtu) throws IOException {
        for (int i = 0; i < sws; i++) {
            char[] payload = new char[mtu];
            br.read(payload);
            tcpOut = new TCPpacket((new String(payload, 0, mtu)).getBytes());
            tcpOut.setSequenceNum(sequenceNum);
            tcpOut.setLength(tcpOut.getPayload().length);
            senderBuffer.add(tcpOut);
            sequenceNum += tcpOut.getLength();
        }
        numPacketsCreated += sws;
    }

    public static void sendPackets(int sws, int mtu) throws IOException {
        for (TCPpacket packet : senderBuffer) {
            if (!packet.getIsSent()) {
                sendPacketSender(packet);
                System.out.println("Pakcet " + packet.getSequenceNum() +  " sent");
            }
        }
        stage = Stage.DATA_TRANSFER;
    }

    public static void handleAck(int sws, int mtu) throws IOException {
        if (!tcpIn.getAckFlag()) {
            System.out.println("Error: Ack not recieved in data transfer mode");
            return; // drop packet
        }
        if (tcpIn.getAck() < currentAck) {
            System.out.println("old Ack recieved, dropping");
            return; // drop packet
        }
        if (tcpIn.getAck() == currentAck) {
            numDuplicates++;
            if (numDuplicates >= 3) {
                //resend
                numDuplicates = 0;
            }
        }
        int numRemoved = 0;
        for(int i = 0; i < senderBuffer.size(); i++) {
            if (i < senderBuffer.size() && senderBuffer.get(i).getSequenceNum() <= tcpIn.getAck()) {
                senderBuffer.remove(i);
                i--;
                numRemoved++;
            }
        }
        if (lastAck != -1 ){
            return;
        }
        for (int i = 0; i < numRemoved; i++) {
            char[] payload;
            if (numPacketsCreated == numFullSegments) {
                payload = new char[lengthOfLastSegment];
                mtu = lengthOfLastSegment;
            }
            else {
                payload = new char[mtu];
            }
            if (br.read(payload) != -1) {
                tcpOut = new TCPpacket((new String(payload, 0, mtu)).getBytes());
                tcpOut.setSequenceNum(sequenceNum);
                tcpOut.setLength(tcpOut.getPayload().length);
                senderBuffer.add(tcpOut);
                sequenceNum += tcpOut.getLength();
                numPacketsCreated++;
                
            }
            else {
                System.out.println("last Packet created");
                lastAck = sequenceNum;
                System.out.println("lastAck = " + (sequenceNum));
                break;
            }
        }

    }

    public static void sendFin() throws IOException {
        System.out.println("Sending Fin");
        tcpOut = new TCPpacket();
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setLength(0);
        tcpOut.setFinFlag(true);
        sendPacketSender(tcpOut);
    }

    public static void handleClose () throws IOException {
        System.out.println("In handleClose");
        if (tcpIn.getAckFlag() && tcpIn.getFinFlag()) {
            tcpOut = new TCPpacket();
            tcpOut.setLength(0);
            tcpOut.setAckFlag(true);
            tcpOut.setAck(tcpIn.getSequenceNum()+1);
            sendPacketSender(tcpOut);
            System.out.println("recieved Fin/Ack, sending final ack");
            stage = Stage.CONNECTION_TERMINATED;
            }
    }

    public static void sender(int port, String remoteIP, int remotePort, String fileName, int mtu, int sws) throws IOException, InterruptedException {
        System.out.println("Starting Sender");
        // initalize variables
        buffer = new byte[1472]; 
        stage = Stage.NO_CONNECTION;
        inFile = new File(fileName);
        fr = new FileReader(inFile);
        br = new BufferedReader(fr);
        socket = new DatagramSocket(port);
        outAddr = InetAddress.getByName(remoteIP);
        rPort = remotePort;
        sequenceNum = 0;
        senderBuffer = new ArrayList<TCPpacket>(sws);
        numDuplicates = 0;
        lastAck = -1;
        numFullSegments = (int)(inFile.length() / mtu);
        lengthOfLastSegment = (int)(inFile.length() % mtu);
        numPacketsCreated = 0;
        System.out.println("numFullSegments: " + numFullSegments);
        System.out.println("length of last Segment: " + lengthOfLastSegment);



        // start connection
        initiateHandShake();

        while (stage != Stage.CONNECTION_TERMINATED) {
            // receive packet
            buffer = new byte[1472];
            packetIn = new DatagramPacket(buffer, buffer.length);
            socket.receive(packetIn);
            
            // desrialze tcp 
            tcpIn = new TCPpacket();
            tcpIn.deserialize(packetIn.getData());
            switch(stage) {
                case NO_CONNECTION:
                    break;
                case HANDSHAKE:
                    completeHandShake();
                    populateBuffer(sws, mtu);
                    sendPackets(sws, mtu);
                    break;
                case DATA_TRANSFER:
                    handleAck(sws, mtu);
                    if (senderBuffer.size() == 0 && lastAck != -1) {
                        stage = Stage.FIN;
                        break;
                    }
                    sendPackets(sws, mtu);
                    break;
                case FIN:
                    sendFin();
                    buffer = new byte[1472];
                    packetIn = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packetIn);
                    tcpIn = new TCPpacket();
                    tcpIn.deserialize(packetIn.getData());
                    handleClose();
                    break;
                case CONNECTION_TERMINATED:
                    System.out.println("Connection terminated");
                    return;
            }
        }
        System.out.println("Connection terminated");
        System.out.println(numDuplicates);
                    return;
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
