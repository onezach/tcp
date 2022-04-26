import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedList;
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
    public static File inFile;
    public static File outFile;
    public static FileReader fr;
    public static FileWriter fw;
    public static BufferedReader br;
    public static BufferedWriter bw;
    public static byte[] buffer;
    public static Queue<byte[]> toSend;
    public static InetAddress outAddr;
    public static int rPort;


    // public class Threader extends Thread {
        
    //     private void send() {
    //         while (stage != Stage.CONNECTION_TERMINATED) {
    //             if (toSend.size() > 0) {
    //                 try {
    //                     byte[] out = toSend.remove();
    //                     packetOut = new DatagramPacket(out, out.length, outAddr, rPort);
    //                     socket.send(packetOut);
    //                     sequenceNum += out.length;
    //                     return;
    //                 } catch (IOException e) {
    //                     System.out.println("Error in send");
    //                     // return;
    //                 }
                    
    //             }
    //         }
    //     }
        
    //     public void run() {
    //         send();
    //     }
    // }

    public void run() {
        send();
    }

    private static void send() {
        while (stage != Stage.NO_CONNECTION && stage != Stage.CONNECTION_TERMINATED) {
            if (toSend.size() > 0) {
                try {
                    byte[] out = toSend.remove();
                    packetOut = new DatagramPacket(out, out.length, outAddr, rPort);
                    socket.send(packetOut);
                    sequenceNum += out.length;
                    // return;
                } catch (IOException e) {
                    System.out.println("Error in send");
                    // return;
                }
                
            }
        }
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
        // int fullPackets = (int) (inFile.length() / 1448);
        // int partialPacketLength = (int) (inFile.length() % 1448);
        // for (int i = 0; i < fullPackets; i++) {
        //     char[] cbuf = new char[]
        //     br.read
        // }
        (new TCPend()).start();
        
        // ----------  HANDSHAKE --------- //
        stage = Stage.HANDSHAKE;
        // need only set necessary fields
        tcpOut = new TCPpacket();
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setSynFlag(true);

        toSend.add(tcpOut.serialize());
        // send();
        // byte[] out = tcpOut.serialize();
        // packetOut = new DatagramPacket(out, out.length, outAddr, remotePort);
        // socket.send(packetOut);
        // sequenceNum += out.length; // 1

        // response back from receiver
        packetIn = new DatagramPacket(buffer, buffer.length);
        socket.receive(packetIn);
        tcpIn = new TCPpacket();
        tcpIn.deserialize(packetIn.getData());

        // verify that an ack flag is detected
        if (tcpIn.getAckFlag() && tcpIn.getSynFlag()) {
            // inSeq = tcpIn.getSequenceNum();
            System.out.println("Sender received sequence number " + tcpIn.getSequenceNum() + " and ack number " + tcpIn.getAck());
        } else {
            System.out.println("Sender didn't receive a SYN/ACK");
            return;
        }

        // send an ack
        tcpOut = new TCPpacket();
        tcpOut.setAckFlag(true);
        tcpOut.setAck(tcpIn.getSequenceNum() + 1);
        System.out.println("Sender sends ack " + (tcpIn.getSequenceNum() + 1));
        toSend.add(tcpOut.serialize());
        // send();
        // byte[] out2 = tcpOut.serialize();
        // packetOut = new DatagramPacket(out2, out2.length, outAddr, remotePort);
        // socket.send(packetOut);

        // ---------- DATA TRANSFER --------- // 
        byte[] payout = new String("Hello ").getBytes();
        tcpOut = new TCPpacket(payout);
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setLength(24 + payout.length);
        byte[] out5 = tcpOut.serialize();
        packetOut = new DatagramPacket(out5, out5.length, outAddr, remotePort);
        socket.send(packetOut);
        sequenceNum += out5.length;

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
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setLength(24 + payout2.length);
        byte[] out6 = tcpOut.serialize();
        packetOut = new DatagramPacket(out6, out6.length, outAddr, remotePort);
        socket.send(packetOut);
        sequenceNum += out6.length;

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
        tcpOut.setFinFlag(true);
        tcpOut.setSequenceNum(sequenceNum);
        byte[] out3 = tcpOut.serialize();
        packetOut = new DatagramPacket(out3, out3.length, outAddr, remotePort);
        socket.send(packetOut);
        sequenceNum += out3.length;

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
            System.out.println("Sender received a FIN and sequence number " + tcpIn.getSequenceNum());
        } else {
            System.out.println("Sender didn't receive a FIN");
            return;
        }

        // send an ack
        tcpOut = new TCPpacket();
        tcpOut.setAckFlag(true);
        tcpOut.setAck(tcpIn.getSequenceNum() + 1);
        byte[] out4 = tcpOut.serialize();
        packetOut = new DatagramPacket(out4, out4.length, outAddr, remotePort);
        socket.send(packetOut);

        socket.close();
        stage = Stage.CONNECTION_TERMINATED;
    }

    private static void handleNoConnection(TCPpacket tcpIn, DatagramPacket packetIn) throws IOException {
        // check if initial message is handhsake starter
        if (!tcpIn.getSynFlag()) {
            System.out.println("Error: recieved packet in stage NO_CONNECTION without a syn flag");
            // Drop Packet
            return;
        }
        
        // print recieved
        System.out.println("Receiver got a syn");
        String payload = "got seq num:"  + tcpIn.getSequenceNum();

        // create TCP packet out
        tcpOut = new TCPpacket();
        sequenceNum = 100;
        tcpOut.setSequenceNum(sequenceNum);
        tcpOut.setSynFlag(true);
        tcpOut.setAck(tcpIn.getSequenceNum() + 1);
        tcpOut.setAckFlag(true);

        // serialize
        byte[] out = tcpOut.serialize();

        // get information on sender so that we can send packets back
        int senderPort = packetIn.getPort();
        InetAddress senderAddress = packetIn.getAddress();
        
        // create Datagram out
        packetOut = new DatagramPacket(out, out.length, senderAddress, senderPort);
        
        // send packet
        socket.send(packetOut);

        // update
        stage = Stage.HANDSHAKE;
        expectedSeqNum = tcpOut.getAck();
        sequenceNum++;
    }

    private static void handleHandShake(TCPpacket tcpIn, DatagramPacket packetIn, String fileName) throws IOException {
        // check if initial message is handhsake starter
        if (!tcpIn.getAckFlag() || tcpIn.getAck() != sequenceNum) {
            System.out.println("Error: recieved packet in stage Hnadshake without ack or wrong ack");
            // Drop Packet
            return;
        }
        
        // print recieved
        System.out.println("Receiver got ack to complete 3-way handshake");
        System.out.println("ack = " + tcpIn.getAck());

        // update current stage
        stage = Stage.DATA_TRANSFER;
    }

    private static void writeToFile(byte[] payload) throws IOException {
        System.out.println("byte[] len = " + payload.length);
        String payloadStr = new String(payload, 0, payload.length);
        System.out.println("payloadStr len = " + payloadStr.length());
        System.out.println("About to write " + payloadStr + " to file");
        bw.write(payloadStr);
    }

    private static void handleDataTransfer(TCPpacket tcpIn, DatagramPacket packetIn) throws IOException {
        // check if next packet is next contigous
        if (tcpIn.getSequenceNum() != expectedSeqNum){
            System.out.println("Error: wrong sequence number recieved in data_transfer stage, dropping packet");
        }

        System.out.println("Succesfully recived data packet");
        System.out.println("packet:\n" + new String(tcpIn.getPayload(), 0, tcpIn.getPayload().length));

        expectedSeqNum++;

        // write packet to file
        writeToFile(tcpIn.getPayload());

        // create TCP packet to send ACK
        tcpOut = new TCPpacket();
        tcpOut.setAck(tcpIn.getSequenceNum() + 1);
        tcpOut.setAckFlag(true);

        // serialize
        byte[] out = tcpOut.serialize();

        // get information on sender so that we can send packets back
        int senderPort = packetIn.getPort();
        InetAddress senderAddress = packetIn.getAddress();
        
        // create Datagram out
        packetOut = new DatagramPacket(out, out.length, senderAddress, senderPort);
        
        // send ACK
        socket.send(packetOut);
        System.out.println("Sending ACK for seq " + tcpIn.getSequenceNum());
    }

    private static void handleFin(TCPpacket tcpIn, DatagramPacket packetIn) throws IOException {
        System.out.println("FIN recived, begin connection termination");
        // create TCP packet to send ACK
        tcpOut = new TCPpacket();
        tcpOut.setAck(tcpIn.getSequenceNum() + 1);
        tcpOut.setAckFlag(true);

        // serialize
        byte[] out = tcpOut.serialize();

        // get information on sender so that we can send packets back
        int senderPort = packetIn.getPort();
        InetAddress senderAddress = packetIn.getAddress();
        
        // create Datagram out
        packetOut = new DatagramPacket(out, out.length, senderAddress, senderPort);
        
        // send ACK
        socket.send(packetOut);
        System.out.println("Sending ACK for fin");

        ///////////////////////////////////////////
        // This is where reciever would clean up any loose ends before sending fin if we were actually doing that
        ///////////////////////////////////////////

        // create TCP packet to send FIN
        tcpOut = new TCPpacket();
        tcpOut.setFinFlag(true);
        tcpOut.setSequenceNum(sequenceNum);
        //tcpOut.setSequenceNum(); need to keep track of our seq


        // serialize
        out = tcpOut.serialize();

        // get information on sender so that we can send packets back
        senderPort = packetIn.getPort();
        senderAddress = packetIn.getAddress();
        
        // create Datagram out
        packetOut = new DatagramPacket(out, out.length, senderAddress, senderPort);
        
        // send ACK
        socket.send(packetOut);
        System.out.println("Sending FIN for fin");

        stage = Stage.CONNECTION_TERMINATED;
        bw.close();



 
    }

    public static void receiver(int port, int mtu, int sws, String fileName) throws IOException {
        System.out.println("Starting Reciever");
        stage = Stage.NO_CONNECTION;
        outFile = new File(fileName);
        fw = new FileWriter(outFile);
        bw = new BufferedWriter(fw);
        
        socket = new DatagramSocket(port);

        while (true) {
             // receive packet
             byte[] buffer = new byte[1472];
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
                    handleDataTransfer(tcpIn, packetIn);
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
