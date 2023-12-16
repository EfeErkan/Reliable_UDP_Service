import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Timer;
import java.util.TimerTask;

public class Sender
{
	private final Object lock = new Object();
	private Timer timer = new Timer();

	private static final int NUM_OF_ARGS = 4;
	private static final int PACKET_SIZE_BYTES = 1024;
	private static final int HEADER_SIZE_BYTES = 2;

	private DatagramSocket socket;
	private int receiver_port;
	private int window_size_N;
	private int retransmission_timeout;
	private int base;
	private int nextSeqNum;
	private boolean retransmission;

	public Sender(int receiver_port, int window_size_N, int retransmission_timeout)
	{
		try
		{
			this.socket = new DatagramSocket();
			this.receiver_port = receiver_port;
			this.window_size_N = window_size_N;
			this.retransmission_timeout = retransmission_timeout;
			base = 1;
			nextSeqNum = 1;
			retransmission = false;
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
					InetAddress address = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
					DatagramPacket packet = new DatagramPacket(data, data.length, address, receiver_port);
					this.socket.send(packet);
					System.out.println("Sent packet with sequence number " + nextSeqNum);

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
		while (base <= totalPackets) {
			 // Send packets up to the window size

			while (nextSeqNum < base + window_size_N && nextSeqNum <= totalPackets) {
				  byte[] data = packets[nextSeqNum - 1];
				  sendPacket(data);
			}

			//startRetransmissionTimer(totalPackets, packets);
			long startTime = System.currentTimeMillis();

			// Wait for acknowledgments for the sent packets
			synchronized (lock) {
				try {
					lock.wait(retransmission_timeout);
					// lock.notify();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			long endTime = System.currentTimeMillis();
			long elapsedTime = endTime - startTime;

			if (elapsedTime >= retransmission_timeout) {
				System.out.println("Retransmitting entire window due to timeout");
				nextSeqNum = base;
			}
		}

		// Send a packet with sequence number 0 to indicate the end of the file
		byte[] endPacketData = new byte[HEADER_SIZE_BYTES];
		writeSequenceNumber(endPacketData, 0);
		sendPacket(endPacketData);
	}
	
	private void startRetransmissionTimer(int totalPackets, byte[][] packets) {
		timer.schedule(new RetransmissionTask(totalPackets, packets), retransmission_timeout);
  	}

  private void stopRetransmissionTimer() {
		timer.cancel();
		// Create a new Timer for future use
		timer = new Timer();
  	}

	private class RetransmissionTask extends TimerTask {
		private final int totalPackets;
      private final byte[][] packets;

		public RetransmissionTask(int totalPackets, byte[][] packets) {
			this.totalPackets = totalPackets;
			this.packets = packets;
	  	}
      @Override
      public void run() {
         // Retransmit the entire window on timeout
			System.out.println("Retransmitting entire window due to timeout");
			retransmission = false;
			//stopRetransmissionTimer();
      }
    }
  

	private class Receiver implements Runnable
	{
		private DatagramSocket socket;

		private Receiver(DatagramSocket socket) throws SocketException
		{
			this.socket = socket;
		}

		@Override
		public void run()
		{
			System.out.println("Receiver started");
			while (true)
			{
				byte[] buffer = new byte[1024];
				DatagramPacket ackPacket = new DatagramPacket(buffer, buffer.length);
				try {
					this.socket.receive(ackPacket);
				} catch (IOException e) {
					e.printStackTrace();
				}
				//System.out.println("Received acknowledgment packet");
				
				// Extract acknowledgment number
				byte[] ackData = ackPacket.getData();
				int ackNum = ((ackData[0] & 0xFF) << 8) | (ackData[1] & 0xFF);

				// Handle acknowledgment
				handleAck(ackNum);
			}
		}

		private void handleAck(int ackNum) {
			if (ackNum >= base && ackNum < base + window_size_N) {
				System.out.println("Received acknowledgment for packet with sequence number " + ackNum);
				 // Move the window
				base = ackNum + 1;
				System.out.println("Moved window to " + base);

				// // If the window has moved, notify the sender
				synchronized (lock) {
					lock.notify();
				}
			}
	  }
	}

	public static void main(String[] args) throws IOException, InterruptedException
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

		File file = new File(file_path);
		FileInputStream fileInputStream = new FileInputStream(file);
		int totalPackets = (int) Math.ceil((double) fileInputStream.available() / (PACKET_SIZE_BYTES - HEADER_SIZE_BYTES));

		byte[][] packets = new byte[totalPackets][PACKET_SIZE_BYTES];

		for (int i = 0; i < totalPackets; i++) {
			int start = i * (PACKET_SIZE_BYTES - HEADER_SIZE_BYTES);
			int end = (int) Math.min((i + 1) * (PACKET_SIZE_BYTES - HEADER_SIZE_BYTES), file.length());
			
			// Create a byte array for the packet
			byte[] packetData = new byte[end - start + HEADER_SIZE_BYTES];

			writeSequenceNumber(packetData, i + 1);

			// Read packet data from the file
			readPacketData(fileInputStream, packetData, HEADER_SIZE_BYTES, end - start);

			// Add the packet to the array of packets
			packets[i] = packetData;
		}

		Sender sender = new Sender(receiver_port, window_size_N, retransmission_timeout);
      Receiver receiver = sender.new Receiver(sender.socket);
      Thread receiverThread = new Thread(receiver);
      receiverThread.start();  // Start the receiver thread

      sender.sendPackets(totalPackets, packets);

      // Wait for the receiver thread to finish
      receiverThread.join();

		// Close the file input stream
		fileInputStream.close();

	}

	private static void writeSequenceNumber(byte[] packetData, int sequenceNumber) {
		packetData[0] = (byte) ((sequenceNumber >> 8) & 0xFF);
		packetData[1] = (byte) (sequenceNumber & 0xFF);
	}

	private static void readPacketData(FileInputStream fileInputStream, byte[] packetData, int offset, int length) throws IOException {
		// Read packet data from the file
		int bytesRead = fileInputStream.read(packetData, offset, length);
  
		// Ensure that the correct amount of data is read
		if (bytesRead != length) {
			 throw new IOException("Error reading packet data from file.");
		}
	}
}