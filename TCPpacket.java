import java.net.DatagramPacket;
import java.nio.ByteBuffer;

public class TCPpacket {
    // actual TCP packet headers, will be serialized
    private int sequenceNum;
    private int ack;
    private long timeStamp;
    private int length;
    private boolean synFlag;
    private boolean finFlag;
    private boolean ackFlag;
    private short zeros;
    private short checksum;
    private byte[] payload;

    // constants and other attributes
    private static final int MAX_DATA_LEN = 1446;

    // Constructor
    public TCPpacket() {
        this.zeros = 0;
    }

    public TCPpacket(byte[] payload) {
        this.payload = payload;
        this.zeros = 0;
    }

    // Setters
    public void setPayload(byte[] payload) {
        this.payload = payload;
    }
    public void setSequenceNum(int sequenceNum) {
        this.sequenceNum = sequenceNum;
    }
    public void setAck(int ack) {
        this.ack = ack;
    }
    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }
    public void setLength(int length) {
        this.length = length;
    }
    public void setSynFlag(boolean synFlag) {
        this.synFlag = synFlag;
    }
    public void setFinFlag(boolean finFlag) {
        this.finFlag = finFlag;
    }
    public void setAckFlag(boolean ackFlag) {
        this.ackFlag = ackFlag;
    }
    public void setChecksum(short checksum) {
        this.checksum = checksum;
    }

    // Getters
    public byte[] getPayload() {
        return this.payload;
    }
    public int getSequenceNum() {
        return this.sequenceNum;
    }
    public int getAck() {
        return this.ack;
    }
    public long getTimeStamp() {
        return this.timeStamp;
    }
    public int getLength() {
        return this.length;
    }
    public boolean getSynFlag() {
        return this.synFlag;
    }
    public boolean getFinFlag() {
        return this.finFlag;
    }
    public boolean getAckFlag() {
        return this.ackFlag;
    }
    public short setChecksum() {
        return this.checksum;
    }

    // internal helper methods

    private int determineLengthAndFlags() {
        int length = this.length;
        boolean synFlag = this.synFlag;
        boolean finFlag = this.finFlag;
        boolean ackFlag = this.ackFlag;
        int serializedLength = length << 3;
        int synMask = 1 << 2;
        int finMask = 1 << 1;
        int ackMask = 1;

        if (synFlag) {
            serializedLength = serializedLength | synMask;
        }
        if (finFlag) {
            serializedLength = serializedLength | finMask;
        }
        if (ackFlag) {
            serializedLength = serializedLength | ackMask;
        }

        return serializedLength;

    }

    // Useful Methods

    public byte[] serialize() {
        // find length of total TCP Packet
        int length = 24; // length of TCP header
        if (this.payload != null) {
            length += this.payload.length;
        }
        // create byte[]
        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);

        // add header info to byte[]
        bb.putInt(this.sequenceNum);
        bb.putInt(this.ack);
        bb.putLong(this.timeStamp);
        bb.putInt(determineLengthAndFlags()); // compute length along with flags
        bb.putShort(this.zeros);
        bb.putShort(this.checksum);

        // add payload to byte[] if present
        if (this.payload != null) {
            bb.put(this.payload);
        }
        return data;
    }

    public TCPpacket deserialize(byte[] data) {

        return null;
    }

    @Override
    public String toString() {
        String str = "";
        str+= "sequenceNum: " + this.sequenceNum + "\n";
        str+= "ack: " + this.ack + "\n";
        str+= "timeStamp: " + this.timeStamp + "\n";
        str+= "length: " + this.length + "\n";
        str+= "synFlag: " + this.synFlag + "\n";
        str+= "finFlag: " + this.finFlag + "\n";
        str+= "ackFlag: " + this.ackFlag + "\n";
        str+= "zeros: " + this.zeros + "\n";
        str+= "checksum: " + this.checksum + "\n";
        if (this.payload != null) {
            str+= "payLoad length: " + this.payload.length + "\n";
            if (this.payload.length > 20){
                str+= "first 20 characters: " + new String(this.payload, 0, 80) + "\n";
                str+= "last 20 characters: " + new String(this.payload, this.payload.length - 81, 80) + "\n";
            }
        }
        else {
            str+= "Payload: null\n";
        }
        return str;
    }


}