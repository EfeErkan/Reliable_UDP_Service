import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Sender
{
   static final int NUM_OF_ARGS = 4;
   static final int PACKET_SIZE_BYTES = 1024;
   static final int HEADER_SIZE_BYTES = 2;

   public static void main(String[] args) throws IOException
   {
      if (args.length != NUM_OF_ARGS)
      {
         System.out.println("Usage: java Sender <file_path> <receiver_port> <window_size_N> <retransmission_timeout>");
         System.exit(1);
      }
      String file_path = args[0];
      int receiver_port = Integer.parseInt(args[1]);
      int window_size_N = Integer.parseInt(args[2]);
      int retransmission_timeout = Integer.parseInt(args[3]);

      FileInputStream fileInputStream = new FileInputStream(new File(file_path));
      int totalPackets = (int) Math.ceil((double) fileInputStream.available() / (PACKET_SIZE_BYTES - HEADER_SIZE_BYTES));

      for (int i = 0; i < totalPackets; i++) {
         int start = i * (PACKET_SIZE_BYTES - HEADER_SIZE_BYTES);
         int end = Math.min((i + 1) * (PACKET_SIZE_BYTES - HEADER_SIZE_BYTES), fileInputStream.available());

         // Create a byte array for the packet
         byte[] packetData = new byte[end - start];

         writeSequenceNumber(packetData, i);

         // Read packet data from the file
         readPacketData(fileInputStream, start, packetData);

         // Process the packet (you can save or send it as needed)
      }
   }

   private static void writeSequenceNumber(byte[] packetData, int sequenceNumber) {
      packetData[0] = (byte) ((sequenceNumber >> 8) & 0xFF);
      packetData[1] = (byte) (sequenceNumber & 0xFF);
   }

   private static void readPacketData(FileInputStream fileInputStream, int start, byte[] packetData) throws IOException {
      // Move the file pointer to the starting position
      fileInputStream.skip(start);

      // Read packet data from the file
      fileInputStream.read(packetData, 2, packetData.length);
   }
}