import socket
import struct
import threading
import time

class Sender:
    NUM_OF_ARGS = 4
    PACKET_SIZE_BYTES = 1024
    HEADER_SIZE_BYTES = 2

    def __init__(self, receiver_port, window_size_N, retransmission_timeout):
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.receiver_port = receiver_port
        self.window_size_N = window_size_N
        self.retransmission_timeout = retransmission_timeout
        self.base = 1
        self.nextSeqNum = 1
        self.retransmission_count = 0
        self.lock = threading.Lock()

    def send_packet(self, data):
        if self.nextSeqNum < self.base + self.window_size_N:
            # Send the packet
            address = ("127.0.0.1", self.receiver_port)
            packet = struct.pack(f"!H{len(data)}s", self.nextSeqNum, data)
            self.socket.sendto(packet, address)
            print(f"Sent packet with sequence number {self.nextSeqNum} base: {self.base}")

            # Move to the next sequence number
            self.nextSeqNum += 1
        else:
            print("Window is full. Waiting for acknowledgments...")

    def send_packets(self, total_packets, packets):
        while self.base <= total_packets:
            # Send packets up to the window size
            start_time = time.time()

            while self.nextSeqNum < self.base + self.window_size_N and self.nextSeqNum <= total_packets:
                data = packets[self.nextSeqNum - 1]
                self.send_packet(data)

            middle_time = time.time()

            # Wait for acknowledgments for the sent packets
            with self.lock:
                elapsed_time = middle_time - start_time
                time_to_wait = max(0, self.retransmission_timeout - elapsed_time)
                time.sleep(time_to_wait)

            end_time = time.time()

            if end_time - start_time >= self.retransmission_timeout:
                print("Retransmitting entire window due to timeout")
                self.retransmission_count += 1
                self.nextSeqNum = self.base

        # Send a packet with sequence number 0 to indicate the end of the file
        end_packet_data = struct.pack("!H", 0)
        self.send_packet(end_packet_data)

class Receiver(threading.Thread):
    def __init__(self, socket, total_packets):
        super().__init__()
        self.socket = socket
        self.total_packets = total_packets
        self.stop = False

    def run(self):
        print("Receiver started")
        while not self.stop:
            try:
                # Ensure the socket is using UDP and receive data along with the address
                packet, sender_addr = self.socket.recvfrom(1024)
            except OSError as e:
                print(f"Error receiving packet: {e}")
                # Handle the error, e.g., by resizing the buffer or taking appropriate action
                sys.exit(1)

            # Extract acknowledgment number
            ack_num = struct.unpack("!H", packet[:2])[0]
            print(f"Acknum {ack_num}")

            # Handle acknowledgment
            self.handle_ack(ack_num)

    def handle_ack(self, ack_num):
        if ack_num >= sender.base:
            print(f"Received acknowledgment for packet with sequence number {ack_num}")
            # Move the window
            sender.base = ack_num + 1
            print(f"Moved window to {sender.base}")

            # If the window has moved, notify the sender
            with sender.lock:
                sender.lock.notify()

            if sender.base > self.total_packets:
                self.stop = True
import os
def main():
    if len(sys.argv) != Sender.NUM_OF_ARGS + 1:
        print("Usage: python Sender.py <file_path> <receiver_port> <window_size_N> <retransmission_timeout>")
        sys.exit(1)

    file_path = sys.argv[1]
    receiver_port = int(sys.argv[2])
    window_size_N = int(sys.argv[3])
    retransmission_timeout = int(sys.argv[4])

    with open(file_path, "rb") as file:
        total_packets = (os.path.getsize(file_path) + Sender.PACKET_SIZE_BYTES - Sender.HEADER_SIZE_BYTES - 1) // (
                Sender.PACKET_SIZE_BYTES - Sender.HEADER_SIZE_BYTES)

        packets = []
        for i in range(total_packets):
            start = i * (Sender.PACKET_SIZE_BYTES - Sender.HEADER_SIZE_BYTES)
            end = min((i + 1) * (Sender.PACKET_SIZE_BYTES - Sender.HEADER_SIZE_BYTES), os.path.getsize(file_path))
            # Create a byte array for the packet
            packet_data = bytearray(end - start + Sender.HEADER_SIZE_BYTES)
            packet_data[:Sender.HEADER_SIZE_BYTES] = struct.pack("!H", i + 1)

            # Read packet data from the file
            file.seek(start)
            packet_data[Sender.HEADER_SIZE_BYTES:] = file.read(end - start)

            # Add the packet to the array of packets
            packets.append(packet_data)

    global sender
    sender = Sender(receiver_port, window_size_N, retransmission_timeout)
    receiver = Receiver(sender.socket, total_packets)
    receiver.start()  # Start the receiver thread

    sender.send_packets(total_packets, packets)

    # Wait for the receiver thread to finish
    receiver.join()

    # Close the file
    file.close()

    print(f"Retransmission count: {sender.retransmission_count}")


if __name__ == "__main__":
    import sys
    main()
