import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
// import java.net.SocketException;
// import java.net.UnknownHostException;

public class TCPend {

    public static DatagramSocket socket;
    public static DatagramPacket packetIn;
    public static DatagramPacket packetOut;
    public static TCPpacket tcpIn;
    public static TCPpacket tcpOut;
    public static byte[] buffer = new byte[1500];
    
    public static void sender(int port, String remoteIP, int remotePort, String fileName, int mtu, int sws) throws IOException {
        System.out.println("Starting Sender");
        
        socket = new DatagramSocket(port); // NOTE: sender cannot have same port number as reciever port if on same machine
        InetAddress outAddr = InetAddress.getByName(remoteIP);

        String payLoad1 = "Hello world!";
        byte[] payout = payLoad1.getBytes();

        tcpOut = new TCPpacket();
        tcpOut.setSequenceNum(0);
        tcpOut.setAck(0);
        tcpOut.setTimeStamp(System.nanoTime());
        tcpOut.setLength(50);
        tcpOut.setSynFlag(true);
        tcpOut.setFinFlag(false);
        tcpOut.setAckFlag(false);
        tcpOut.setChecksum((short) 0);
        tcpOut.setPayload(payout);

        byte[] out = tcpOut.serialize();

        packetOut = new DatagramPacket(out, out.length, outAddr, remotePort);
        socket.send(packetOut);

        // response back
        packetIn = new DatagramPacket(buffer, buffer.length);
        socket.receive(packetIn);
        tcpIn = new TCPpacket();
        tcpIn.deserialize(packetIn.getData());

        System.out.println(new String(tcpIn.getPayload(), 0, tcpIn.getPayload().length));
        if (tcpIn.getAckFlag()) {
            System.out.println("Sender received sequence number " + tcpIn.getSequenceNum() + " and ack number " + tcpIn.getAck());
        }


        String payload2 = "Sender close...";
        payout = payload2.getBytes();

        tcpOut = new TCPpacket();
        tcpOut.setSequenceNum(0);
        tcpOut.setAck(0);
        tcpOut.setTimeStamp(System.nanoTime());
        tcpOut.setLength(50);
        tcpOut.setSynFlag(false);
        tcpOut.setFinFlag(true);
        tcpOut.setAckFlag(false);
        tcpOut.setChecksum((short) 0);
        tcpOut.setPayload(payout);

        byte[] out2 = tcpOut.serialize();

        packetOut = new DatagramPacket(out2, out2.length, outAddr, remotePort);
        socket.send(packetOut);

        socket.close();
    }

    public static void receiver(int port, int mtu, int sws, String fileName) throws IOException {
        System.out.println("Starting Reciever");

        socket = new DatagramSocket(port);

        while (true) {
            // recieve packet
            packetIn = new DatagramPacket(buffer, buffer.length);
            socket.receive(packetIn);
            System.out.println("receiver received packet");


            tcpIn = new TCPpacket();
            // buffer = packetIn.getData();
            tcpIn.deserialize(packetIn.getData());
            int senderPort = packetIn.getPort();
            InetAddress senderAddress = packetIn.getAddress();
            
            // new String(packetIn.getData(), 0, packetIn.getLength());
            System.out.println(new String(tcpIn.getPayload(), 0, tcpIn.getPayload().length));
            if (tcpIn.getFinFlag()) {
                System.out.println("Receiver got a fin");
                break;
            }

            if (tcpIn.getSynFlag()) {
                
                System.out.println("Receiver got a syn");
                String payload = "got seq num:"  + tcpIn.getSequenceNum();
                byte[] payout = payload.getBytes();

                tcpOut = new TCPpacket();
                tcpOut.setSequenceNum(100);
                tcpOut.setAck(tcpIn.getAck() + 1);
                tcpOut.setTimeStamp(System.nanoTime());
                tcpOut.setLength(50);
                tcpOut.setSynFlag(false);
                tcpOut.setFinFlag(false);
                tcpOut.setAckFlag(true);
                tcpOut.setChecksum((short) 0);
                tcpOut.setPayload(payout);

                // tcpOut.setPayload(buffer);
                // tcpOut.setSequenceNum(100);
                // tcpOut.setAckFlag(true);
                // tcpOut.setAck(tcpIn.getAck() + 1);
                // tcpOut.setSynFlag(true);
                // tcpOut.setTimeStamp(System.nanoTime());

                byte[] out = tcpOut.serialize();

                packetOut = new DatagramPacket(out, out.length, senderAddress, senderPort);
                socket.send(packetOut);

            }
            
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
