import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class TCPend {

    public static DatagramSocket socket;
    public static DatagramPacket packetIn;
    public static DatagramPacket packetOut;
    public static TCPpacket tcpIn;
    public static TCPpacket tcpOut;
    
    public static void sender(int port, String remoteIP, int remotePort, String fileName, int mtu, int sws) throws IOException {
        System.out.println("Starting Sender");
        byte[] buffer = new byte[1500];
        
        socket = new DatagramSocket(port); // NOTE: sender cannot have same port number as reciever port if on same machine
        InetAddress outAddr = InetAddress.getByName(remoteIP);

        String payLoad1 = "Hello world!";
        byte[] payout = payLoad1.getBytes();

        // need only set necessary fields
        tcpOut = new TCPpacket();
        tcpOut.setSequenceNum(0);
        tcpOut.setSynFlag(true);
        tcpOut.setPayload(payout);

        byte[] out = tcpOut.serialize();

        packetOut = new DatagramPacket(out, out.length, outAddr, remotePort);
        socket.send(packetOut);

        // response back from receiver
        packetIn = new DatagramPacket(buffer, buffer.length);
        socket.receive(packetIn);
        tcpIn = new TCPpacket();
        tcpIn.deserialize(packetIn.getData());
        // print payload
        System.out.println(new String(tcpIn.getPayload(), 0, tcpIn.getPayload().length));
        // verify that an ack flag is detected
        if (tcpIn.getAckFlag()) {
            System.out.println("Sender received sequence number " + tcpIn.getSequenceNum() + " and ack number " + tcpIn.getAck());
        }

        // send FIN to receiver
        tcpOut = new TCPpacket();
        tcpOut.setFinFlag(true);
        byte[] out2 = tcpOut.serialize();

        packetOut = new DatagramPacket(out2, out2.length, outAddr, remotePort);
        socket.send(packetOut);

        socket.close();
    }

    public static void receiver(int port, int mtu, int sws, String fileName) throws IOException {
        System.out.println("Starting Reciever");

        socket = new DatagramSocket(port);

        while (true) {
            // receive packet
            byte[] buffer = new byte[1500];
            packetIn = new DatagramPacket(buffer, buffer.length);
            socket.receive(packetIn);

            tcpIn = new TCPpacket();
            tcpIn.deserialize(packetIn.getData());
            // get information on sender so that we can send packets back
            int senderPort = packetIn.getPort();
            InetAddress senderAddress = packetIn.getAddress();
            
            // print payload of incoming packet
            System.out.println(new String(tcpIn.getPayload(), 0, tcpIn.getPayload().length));

            // handle SYN
            if (tcpIn.getSynFlag()) { 
                System.out.println("Receiver got a syn");
                String payload = "got seq num:"  + tcpIn.getSequenceNum();
                byte[] payout = payload.getBytes();

                tcpOut = new TCPpacket();
                tcpOut.setSequenceNum(100);
                tcpOut.setAck(tcpIn.getAck() + 1);
                tcpOut.setAckFlag(true);
                tcpOut.setPayload(payout);

                byte[] out = tcpOut.serialize();

                packetOut = new DatagramPacket(out, out.length, senderAddress, senderPort);
                socket.send(packetOut);

            }

            // handle FIN
            if (tcpIn.getFinFlag()) {
                System.out.println("Receiver got a fin");
                break;
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
