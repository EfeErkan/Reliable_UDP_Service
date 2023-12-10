import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Sender
{
   private static final int NUM_OF_ARGS = 4;
   private static final int PACKET_SIZE_BYTES = 1024;
   private static final int HEADER_SIZE_BYTES = 2;

   private DatagramSocket socket;
   private int receiver_port;
   private int window_size_N;
   private int retransmission_timeout;
   private int base;
   private int nextSeqNum;

   public Sender(int receiver_port, int window_size_N, int retransmission_timeout)
   {
      try
      {
         this.socket = new DatagramSocket();
         this.receiver_port = receiver_port;
         this.window_size_N = window_size_N;
         this.retransmission_timeout = retransmission_timeout;
         base = 0;
         nextSeqNum = 0;
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
   }

   private void sendPacket(byte[] data) {
      try {
          if (nextSeqNum < base + window_size_N) {
              // Send the packet
              InetAddress address = InetAddress.getByName("localhost");
              DatagramPacket packet = new DatagramPacket(data, data.length, address, receiver_port);
              socket.send(packet);

              // Move to the next sequence number
              nextSeqNum++;

          } else {
              System.out.println("Window is full. Waiting for acknowledgments...");
          }
      } catch (IOException e) {
          e.printStackTrace();
      }
   }

   public void sendPackets(int totalPackets, byte[][] packets) {
      for (int i = 0; i < totalPackets; ) {
          // Send packets up to the window size
          while (nextSeqNum < base + window_size_N && i < totalPackets) {
              byte[] data = packets[i];
              sendPacket(data);
              i++;
          }
  
          // Wait for acknowledgments for the sent packets
          synchronized (this) {
              try {
                  wait(retransmission_timeout);
              } catch (InterruptedException e) {
                  e.printStackTrace();
              }
          }
      }
  }
  

   private class Receiver implements Runnable
   {
      private DatagramSocket socket;

      private Receiver(DatagramSocket socket)
      {
         this.socket = socket;
      }

      @Override
      public void run()
      {
         while (true) 
         {
            byte[] buffer = new byte[1024];
            DatagramPacket ackPacket = new DatagramPacket(buffer, buffer.length);
            try {
               socket.receive(ackPacket);
            } catch (IOException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
            }

            String ackMessage = new String(ackPacket.getData(), 0, ackPacket.getLength());
            int ackNum = Integer.parseInt(ackMessage.split(" ")[1]);

            // Handle acknowledgment
            handleAck(ackNum);
         }
      }

      private void handleAck(int ackNum) {
         if (ackNum >= base && ackNum < base + window_size_N) {
             // Move the window
             base = ackNum + 1;

             // If the window has moved, notify the sender
             synchronized (Sender.this) {
                 Sender.this.notify();
             }
         }
     }
   }

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

      byte[][] packets = new byte[totalPackets][PACKET_SIZE_BYTES];

      for (int i = 0; i < totalPackets; i++) {
         int start = i * (PACKET_SIZE_BYTES - HEADER_SIZE_BYTES);
         int end = Math.min((i + 1) * (PACKET_SIZE_BYTES - HEADER_SIZE_BYTES), fileInputStream.available());

         // Create a byte array for the packet
         byte[] packetData = new byte[end - start];

         writeSequenceNumber(packetData, i);

         // Read packet data from the file
         readPacketData(fileInputStream, start, packetData);

         // Add the packet to the array of packets
         packets[i] = packetData;
      }

      Sender sender = new Sender(receiver_port, window_size_N, retransmission_timeout);
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